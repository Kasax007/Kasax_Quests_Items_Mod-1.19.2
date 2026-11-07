"""Real FTB Quests books for the importer tests (./gradlew test -PftbSamples=<dir>).

  python3 -I tools/ftb_samples.py fetch <dir>   download the quest folders of six public modpacks into <dir>
  python3 -I tools/ftb_samples.py stats <folder> print the counts of one FTB Quests folder

Only each pack's config/ftbquests/quests folder is downloaded (HTTP range requests on the pack zip, or a sparse git
checkout), the files are read as data and never executed. They belong to the pack authors and stay out of the repo.
Next to every folder the script writes <name>.stats.json: counts from this independent reader, which the Java tests
compare with the importer's own.
"""
import io, os, ssl, subprocess, sys, urllib.request, zipfile
import json, re, collections


# ------------------------------------------------------------------ FTB SNBT (same rules as FTB Library's SNBTParser)
class Snbt:
    def __init__(self, text):
        lines = []
        for line in text.split('\n'):
            t = line.strip()
            lines.append('' if t.startswith('//') or t.startswith('#') else line)
        self.s = '\n'.join(lines) + '\n'
        self.p = 0

    def nxt(self):
        c = self.s[self.p]
        self.p += 1
        return c

    def ns(self):
        while True:
            c = self.nxt()
            if c > ' ':
                return c

    def tag(self, c):
        if c == '{':
            return self.compound()
        if c == '[':
            return self.collection()
        if c in '"\'':
            return self.quoted(c)
        w = self.word(c)
        if w == 'true':
            return True
        if w == 'false':
            return False
        m = re.fullmatch(r'([-+]?\d+)([bBsSlL]?)', w)
        if m:
            return int(m.group(1))
        m = re.fullmatch(r'([-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)([fFdD]?)', w)
        if m:
            return float(m.group(1))
        return w

    def compound(self):
        d = {}
        while True:
            c = self.ns()
            if c == '}':
                return d
            if c in ',\n':
                continue
            key = self.quoted(c) if c in '"\'' else self.word(c)
            n = self.ns()
            if n not in ':=':
                raise ValueError('expected : at %d' % self.p)
            d[key] = self.tag(self.ns())

    def collection(self):
        save = self.p
        a = self.ns()
        b = self.ns()
        if b == ';' and a in 'IiLlBb':
            out = []
            while True:
                c = self.ns()
                if c == ']':
                    return out
                if c == ',':
                    continue
                out.append(self.tag(c))
        self.p = save
        out = []
        while True:
            c = self.ns()
            if c == ']':
                return out
            if c == ',':
                continue
            out.append(self.tag(c))

    def word(self, first):
        sb = [first]
        while True:
            c = self.nxt()
            if c.isalpha() or c.isdigit() or c in '._-+∞':
                sb.append(c)
            else:
                self.p -= 1
                return ''.join(sb)

    def quoted(self, stop):
        sb = []
        esc = False
        while True:
            c = self.nxt()
            if c == '\n':
                raise ValueError('newline in string')
            if esc:
                esc = False
                sb.append({'"': '"', '\\': '\\', 't': '\t', 'b': '\b', 'n': '\n', 'r': '\r', 'f': '\f'}.get(c, ''))
            elif c == '\\':
                esc = True
            elif c == stop:
                return ''.join(sb)
            else:
                sb.append(c)


def read_snbt(path):
    p = Snbt(open(path, encoding='utf-8').read())
    return p.tag(p.ns())


# ------------------------------------------------------------------ JSON5 (enough for FTB's files)
class Json5:
    """Small recursive-descent JSON5 reader (comments, unquoted keys, trailing commas, single quotes, hex, +/-Infinity)."""
    def __init__(self, text):
        self.s, self.p = text, 0

    def ws(self):
        s = self.s
        while self.p < len(s):
            c = s[self.p]
            if c in ' \t\r\n\ufeff\u00a0\u2028\u2029':
                self.p += 1
            elif s.startswith('//', self.p):
                while self.p < len(s) and s[self.p] != '\n':
                    self.p += 1
            elif s.startswith('/*', self.p):
                self.p = s.index('*/', self.p) + 2
            else:
                break

    def value(self):
        self.ws()
        c = self.s[self.p]
        if c == '{':
            self.p += 1
            d = {}
            while True:
                self.ws()
                if self.s[self.p] == '}':
                    self.p += 1
                    return d
                if self.s[self.p] in '"\'':
                    k = self.string()
                else:
                    st = self.p
                    while self.s[self.p] not in ': \t\r\n':
                        self.p += 1
                    k = self.s[st:self.p]
                self.ws()
                assert self.s[self.p] == ':', self.p
                self.p += 1
                d[k] = self.value()
                self.ws()
                if self.s[self.p] == ',':
                    self.p += 1
        if c == '[':
            self.p += 1
            a = []
            while True:
                self.ws()
                if self.s[self.p] == ']':
                    self.p += 1
                    return a
                a.append(self.value())
                self.ws()
                if self.s[self.p] == ',':
                    self.p += 1
        if c in '"\'':
            return self.string()
        st = self.p
        while self.p < len(self.s) and self.s[self.p] not in ',]} \t\r\n/':
            self.p += 1
        w = self.s[st:self.p]
        if w == 'true': return True
        if w == 'false': return False
        if w == 'null': return None
        if w.lower().lstrip('+-').startswith('0x'):
            return int(w.replace('0x', '').replace('0X', ''), 16) * (-1 if w.startswith('-') else 1)
        if 'Infinity' in w or 'NaN' in w:
            return float(w.replace('Infinity', 'inf'))
        try:
            return int(w)
        except ValueError:
            return float(w)

    def string(self):
        q = self.s[self.p]
        self.p += 1
        out = []
        while True:
            c = self.s[self.p]
            self.p += 1
            if c == q:
                return ''.join(out)
            if c == '\\':
                e = self.s[self.p]
                self.p += 1
                if e == 'u':
                    out.append(chr(int(self.s[self.p:self.p + 4], 16))); self.p += 4
                elif e == 'x':
                    out.append(chr(int(self.s[self.p:self.p + 2], 16))); self.p += 2
                elif e == '\n':
                    pass
                else:
                    out.append({'n': '\n', 't': '\t', 'r': '\r', 'b': '\b', 'f': '\f', 'v': '\v', '0': '\0'}.get(e, e))
            else:
                out.append(c)


def read_json5(path):
    return Json5(open(path, encoding='utf-8').read()).value()


def read(path):
    return read_json5(path) if path.endswith('.json5') else read_snbt(path)


# ------------------------------------------------------------------ folder
def load(folder):
    ext = '.json5' if os.path.exists(os.path.join(folder, 'data.json5')) else '.snbt'
    data = read(os.path.join(folder, 'data' + ext)) if os.path.exists(os.path.join(folder, 'data' + ext)) else {}
    gp = os.path.join(folder, 'chapter_groups' + ext)
    groups = read(gp).get('chapter_groups', []) if os.path.exists(gp) else []
    chapters = []
    cd = os.path.join(folder, 'chapters')
    for f in sorted(os.listdir(cd)) if os.path.isdir(cd) else []:
        if f.endswith(ext):
            chapters.append((f, read(os.path.join(cd, f))))
    tables = []
    td = os.path.join(folder, 'reward_tables')
    for f in sorted(os.listdir(td)) if os.path.isdir(td) else []:
        if f.endswith(ext):
            tables.append((f, read(os.path.join(td, f))))
    lang = {}
    ld = os.path.join(folder, 'lang')
    if os.path.isdir(ld):
        for root, dirs, files in os.walk(ld):
            for f in files:
                if not (f.endswith('.snbt') or f.endswith('.json5')):
                    continue
                rel = os.path.relpath(os.path.join(root, f), ld)
                loc = rel.split(os.sep)[0].split('.')[0]
                lang.setdefault(loc, {}).update(read(os.path.join(root, f)))
    return ext, data, groups, chapters, tables, lang


def typ(t):
    t = t or 'item'
    return t[len('ftbquests:'):] if t.startswith('ftbquests:') else t


def item_form(it):
    if isinstance(it, str):
        return 'string'
    if isinstance(it, dict):
        iid = it.get('id', '')
        if iid.startswith('itemfilters:') or iid.startswith('ftbfiltersystem:'):
            return 'filter:' + iid
        if 'tag' in it:
            return 'compound+nbt'
        if 'components' in it:
            return 'compound+components'
        return 'compound'
    return 'other'


def stats(folder):
    ext, data, groups, chapters, tables, lang = load(folder)
    st = collections.OrderedDict()
    st['format'] = 'json5' if ext == '.json5' else 'snbt'
    st['locales'] = sorted(lang)
    st['groups'] = len(groups)
    st['chapters'] = len(chapters)
    q = t = r = links = images = deps = 0
    ttypes, rtypes, forms = collections.Counter(), collections.Counter(), collections.Counter()
    quest_ids = []
    for f, c in chapters:
        qs = c.get('quests', [])
        q += len(qs)
        links += len(c.get('quest_links', []))
        images += len(c.get('images', []))
        for qq in qs:
            quest_ids.append(qq.get('id'))
            deps += len(qq.get('dependencies', []) or [])
            for tk in qq.get('tasks', []):
                t += 1
                ttypes[typ(tk.get('type'))] += 1
                if typ(tk.get('type')) == 'item':
                    forms[item_form(tk.get('item'))] += 1
            for rw in qq.get('rewards', []):
                r += 1
                rtypes[typ(rw.get('type'))] += 1
    st['quests'] = q
    st['tasks'] = t
    st['rewards'] = r
    st['quest_links'] = links
    st['images'] = images
    st['dependencies'] = deps
    st['reward_tables'] = len(tables)
    st['table_entries'] = sum(len(tb.get('rewards', [])) for _, tb in tables)
    st['task_types'] = dict(ttypes.most_common())
    st['reward_types'] = dict(rtypes.most_common())
    st['item_task_forms'] = dict(forms.most_common())
    st['first_quest_ids'] = quest_ids[:3]
    return st



# ------------------------------------------------------------------ downloads
CTX = ssl.create_default_context(cafile='/root/.ccr/ca-bundle.crt')


def final_url(url):
    req = urllib.request.Request(url, method='HEAD', headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, context=CTX, timeout=60) as r:
        return r.geturl(), int(r.headers.get('Content-Length', '0'))


class HttpFile(io.RawIOBase):
    BLOCK = 1 << 18

    def __init__(self, url, size):
        self.url, self.size, self.pos, self.cache, self.fetched = url, size, 0, {}, 0

    def seekable(self):
        return True

    def readable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, off, whence=0):
        self.pos = off if whence == 0 else self.pos + off if whence == 1 else self.size + off
        return self.pos

    def _block(self, i):
        if i not in self.cache:
            a = i * self.BLOCK
            b = min(self.size, a + self.BLOCK) - 1
            req = urllib.request.Request(self.url, headers={'Range': f'bytes={a}-{b}', 'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, context=CTX, timeout=120) as r:
                data = r.read()
            self.fetched += len(data)
            if len(self.cache) > 64:
                self.cache.clear()
            self.cache[i] = data
        return self.cache[i]

    def read(self, n=-1):
        if n is None or n < 0:
            n = self.size - self.pos
        out = bytearray()
        while n > 0 and self.pos < self.size:
            i = self.pos // self.BLOCK
            blk = self._block(i)
            off = self.pos - i * self.BLOCK
            part = blk[off:off + n]
            out += part
            self.pos += len(part)
            n -= len(part)
        return bytes(out)

    def readinto(self, b):
        data = self.read(len(b))
        b[:len(data)] = data
        return len(data)



SAMPLES = [
    # name, FTB Quests version in the pack, source
    ('atm9', '2001 (1.20.1), texts inline', ('git', 'https://github.com/AllTheMods/ATM-9', '255ca23b91cebbb57130074e0bf7b86c266ac2a6')),
    ('prom2', '2001 (1.20.1 Fabric), texts inline', ('cf', 466901, 9455926)),
    ('atm10', '2101 (1.21.1), lang/<locale>/ split', ('git', 'https://github.com/AllTheMods/ATM-10', '3e14816cdc476f4e6887026b6e40c578cf38f76c')),
    ('ftbsb4', '2101 (1.21.1), lang/<locale>.snbt', ('cf', 1373378, 9282328)),
    ('atm11', '26.1.2.9 (26.1.2), JSON5', ('cf', 1148445, 9426887)),
    ('ftbevo2', '26.1.2.10 (26.1.2), JSON5', ('cf', 1731757, 9465312)),
]
PREFIX = 'overrides/config/ftbquests/quests/'


def fetch_cf(project, file, dest):
    url = f'https://www.curseforge.com/api/v1/mods/{project}/files/{file}/download'
    real, size = final_url(url)
    z = zipfile.ZipFile(io.BufferedReader(HttpFile(real, size), buffer_size=1 << 16))
    dest = os.path.realpath(dest)
    for n in z.namelist():
        if not n.startswith(PREFIX) or n.endswith('/'):
            continue
        target = os.path.realpath(os.path.join(dest, n[len(PREFIX):]))
        if not target.startswith(dest + os.sep):
            continue
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with z.open(n) as s, open(target, 'wb') as d:
            d.write(s.read())


def fetch_git(repo, commit, dest):
    tmp = dest + '.git-tmp'
    subprocess.run(['git', 'clone', '-q', '--filter=blob:none', '--sparse', '--no-checkout', repo, tmp], check=True)
    subprocess.run(['git', '-C', tmp, 'sparse-checkout', 'set', 'config/ftbquests/quests'], check=True)
    subprocess.run(['git', '-C', tmp, 'checkout', '-q', commit], check=True)
    os.rename(os.path.join(tmp, 'config', 'ftbquests', 'quests'), dest)
    subprocess.run(['rm', '-rf', tmp], check=True)


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == 'stats':
        print(json.dumps(stats(sys.argv[2]), indent=1))
        return
    if len(sys.argv) < 3 or sys.argv[1] != 'fetch':
        print(__doc__)
        return
    root = sys.argv[2]
    os.makedirs(root, exist_ok=True)
    for name, version, src in SAMPLES:
        dest = os.path.join(root, name)
        if not os.path.isdir(dest):
            print('fetching', name, '-', version, flush=True)
            if src[0] == 'cf':
                fetch_cf(src[1], src[2], dest)
            else:
                fetch_git(src[1], src[2], dest)
        json.dump(stats(dest), open(dest + '.stats.json', 'w'), indent=1)
        print(name, open(dest + '.stats.json').read().count('\n'), 'lines of counts written', flush=True)


if __name__ == '__main__':
    main()

"""Images of the CurseForge page of Quest Icons 1.1 (docs/curseforge/img/*_v2.png): the header, the "at a glance"
numbers, all icons, the quest book with its parts named, tasks and rewards, loot crates, teams, the Dream Star and the
page for pack makers, plus the section strips. Colours and shapes are the quest book's own (QuestBookScreen.java,
textures/gui/shapes). The images of the page before (header.png, book.png, quest.png, icons.png) stay as they are;
book.png and quest.png are the screenshots the new pictures are cut from.

  python3 docs/curseforge/make_images_v2.py FONT_DIR [what ...]

FONT_DIR: the fonts folder of the video archive (Press Start 2P, Inter Display Bold). Vanilla textures come from the
Minecraft client jar ($MC_JAR, default: the Loom cache).
what: header glance icons book tasks crates team star pack strips counts (default: everything).
Every number on the images is counted from the repository (see counts())."""
import io
import json
import math
import os
import re
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HERE, 'img')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'kasax', 'questsicons')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'questsicons')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'questsicons')
FONTS = sys.argv[1]
MC_JAR = os.environ.get('MC_JAR') or os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')
W = 1200
MAX_BYTES = 900 * 1024

# the colours of the quest book (client/gui/QuestBookScreen.java)
BORDER, ACCENT, TEXT, MUTED = (126, 12, 201), (192, 132, 252), (243, 232, 255), (156, 141, 176)
GOLD, GREEN, LOCKED, AVAILABLE = (255, 201, 77), (91, 227, 138), (90, 84, 104), (231, 212, 255)
PANEL, PANEL_HI, EDGE = (38, 24, 57), (58, 35, 88), (91, 58, 134)
NIGHT, INK, WHITE = (22, 12, 38), (16, 8, 28), (255, 255, 255)


def pixel(size):
    return ImageFont.truetype(os.path.join(FONTS, 'PressStart2P-Regular.ttf'), size)


def small(size):
    return ImageFont.truetype(os.path.join(FONTS, 'InterDisplay-Bold.otf'), size)


MONO = os.environ.get('QI_MONO') or next((f for f in ('/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Bold.ttf',
                                                       '/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf')
                                          if os.path.exists(f)), None)


def mono(size):
    """Code, file names and commands: JetBrains Mono or DejaVu Sans Mono ($QI_MONO)."""
    return ImageFont.truetype(MONO, size)


def read(*p):
    return open(os.path.join(*p), encoding='utf-8').read()


# ----------------------------------------------------------------------------------------------- the numbers
def counts():
    """Every number of the images, counted from the code and data of this repository."""
    items = read(JAVA, 'item', 'ModItems.java')
    icons = re.findall(r'= icon\("(\w+)"\)', items)
    crates = re.findall(r'register\("(\w+)", p -> new LootCrateItem', items)
    uses = int(re.search(r'QUEST_REWARD = register\(.*?durability\((\d+)\)', items).group(1))
    reward_item = read(JAVA, 'item', 'custom', 'QuestRewardItem.java')
    per_use = int(re.search(r'1 \+ level\.getRandom\(\)\.nextInt\(Math\.min\((\d+),', reward_item).group(1))
    tasks = re.findall(r'TYPES\.put\("(\w+)"', read(JAVA, 'quest', 'task', 'Tasks.java'))
    rewards = re.findall(r'TYPES\.put\("(\w+)"', read(JAVA, 'quest', 'reward', 'Rewards.java'))
    shapes = re.search(r'enum QuestShape \{\s*([A-Z_, ]+);', read(JAVA, 'quest', 'QuestShape.java')).group(1)
    shapes = [s.strip().lower() for s in shapes.split(',')]
    pins = int(re.search(r'shown >= (\d+)', read(JAVA, 'client', 'gui', 'QuestHud.java')).group(1))
    log = read(HERE, 'changelog-1.1.0+26.3.md')
    fixed = log.split('### Fixed', 1)[1]
    fixes = len(re.findall(r'^- ', fixed, re.M))
    lang = json.loads(read(ASSETS, 'lang', 'en_us.json'))
    return dict(icons=icons, crates=crates, uses=uses, per_use=per_use, tasks=tasks, rewards=rewards, shapes=shapes,
                pins=pins, fixes=fixes, lang=lang)


# ----------------------------------------------------------------------------------------------- sources
_jar = None


def van(rel, size=None):
    """Vanilla texture 'block/x', 'item/x' or 'entity/...' from the client jar (first frame of animated ones)."""
    global _jar
    _jar = _jar or zipfile.ZipFile(MC_JAR)
    im = Image.open(io.BytesIO(_jar.read('assets/minecraft/textures/%s.png' % rel))).convert('RGBA')
    if rel.startswith(('block/', 'item/')):
        im = im.crop((0, 0, im.width, im.width))
    return im.resize((size, size * im.height // im.width), Image.NEAREST) if size else im


def tex(rel, size=None):
    """Texture of this mod: 'item/x', 'block/x', 'gui/shapes/x' (first frame of animated textures)."""
    im = Image.open(os.path.join(ASSETS, 'textures', rel + '.png')).convert('RGBA')
    if rel.startswith('item/'):
        im = im.crop((0, 0, im.width, im.width))
    return im.resize((size, size * im.height // im.width), Image.NEAREST) if size else im


def shot(name):
    """The quest book screenshots of the page before (docs/curseforge/img)."""
    return Image.open(os.path.join(OUT, name)).convert('RGB')


def cube(top, left, right, a=48):
    """Inventory-style block from three square textures, 2a x 2a."""
    S = top.width
    out = Image.new('RGBA', (2 * a, 2 * a), (0, 0, 0, 0))
    faces = [
        (top, (S / (2 * a), -S / a, S / 2, S / (2 * a), S / a, -S / 2), [(a, 0), (2 * a, a / 2), (a, a), (0, a / 2)], 1.0),
        (left, (S / a, 0, 0, -S / (2 * a), S / a, -S / 2), [(0, a / 2), (a, a), (a, 2 * a), (0, 1.5 * a)], 0.8),
        (right, (S / a, 0, -S, S / (2 * a), S / a, -1.5 * S), [(a, a), (2 * a, a / 2), (2 * a, 1.5 * a), (a, 2 * a)], 0.62),
    ]
    for t, aff, poly, shade in faces:
        f = t.transform((2 * a, 2 * a), Image.AFFINE, aff, resample=Image.NEAREST)
        if shade < 1:
            r, g, b, al = f.split()
            r, g, b = (c.point(lambda v, s=shade: int(v * s)) for c in (r, g, b))
            f = Image.merge('RGBA', (r, g, b, al))
        m = Image.new('L', (2 * a, 2 * a), 0)
        ImageDraw.Draw(m).polygon(poly, fill=255)
        out.paste(f, (0, 0), Image.composite(f.split()[3], Image.new('L', m.size, 0), m))
    return out


def vcube(top, side=None, front=None, a=48):
    t, s = van(top), van(side or top)
    return cube(t, van(front) if front else s, s, a)


def glint(im, color=(170, 90, 255), alpha=110):
    """The purple shimmer of enchanted items, on the opaque pixels."""
    g = Image.new('RGBA', im.size, color + (0,))
    a = np.asarray(im.split()[3]).astype(np.float32)
    h = np.linspace(0, 1, im.width)[None, :] + np.linspace(0, 1, im.height)[:, None]
    band = (np.sin(h * 9) * 0.5 + 0.5) * alpha
    g.putalpha(Image.fromarray((np.minimum(a, band)).astype(np.uint8)))
    out = im.copy()
    out.alpha_composite(g)
    return out


def box_img(top, side, ab, side_h):
    """An inventory-style box: top texture as a diamond 2ab wide, both sides side_h tall (left lit, right darker)."""
    S = top.width
    out = Image.new('RGBA', (2 * ab, ab + side_h), (0, 0, 0, 0))
    faces = [
        (top, (S / (2 * ab), -S / ab, S / 2, S / (2 * ab), S / ab, -S / 2),
         [(ab, 0), (2 * ab, ab / 2), (ab, ab), (0, ab / 2)], 1.0),
        (side, (S / ab, 0, 0, -S / (2 * side_h), S / side_h, -S * ab / (2 * side_h)),
         [(0, ab / 2), (ab, ab), (ab, ab + side_h), (0, ab / 2 + side_h)], 0.8),
        (side, (S / ab, 0, -S, S / (2 * side_h), S / side_h, -1.5 * ab * S / side_h),
         [(ab, ab), (2 * ab, ab / 2), (2 * ab, ab / 2 + side_h), (ab, ab + side_h)], 0.62),
    ]
    for t, aff, poly, shade in faces:
        f = t.transform(out.size, Image.AFFINE, aff, resample=Image.NEAREST)
        if shade < 1:
            r, g, b, al = f.split()
            r, g, b = (c.point(lambda v, k=shade: int(v * k)) for c in (r, g, b))
            f = Image.merge('RGBA', (r, g, b, al))
        m = Image.new('L', out.size, 0)
        ImageDraw.Draw(m).polygon(poly, fill=255)
        out.paste(f, (0, 0), Image.composite(f.split()[3], Image.new('L', m.size, 0), m))
    return out


def pedestal(a=64):
    """The Dream Star Pedestal from its block model (models/block/dream_star_pedestal.json): base slab, column and
    top, drawn from the bottom up in the inventory view; a = half the width of a whole block in pixels."""
    model = json.loads(read(ASSETS, 'models', 'block', 'dream_star_pedestal.json'))
    tx = {k: tex('block/' + v.split('/')[-1]) for k, v in model['textures'].items() if k != 'particle'}
    out = Image.new('RGBA', (2 * a, 2 * a + a), (0, 0, 0, 0))
    by = out.height - a // 2                               # screen y of the centre of the block's bottom
    for el in model['elements']:
        (x0, y0, z0), (x1, y1, z1) = el['from'], el['to']
        f = el['faces']
        up = f.get('up') or f['north']
        top = tx[up['texture'][1:]].crop(tuple(up['uv'])).resize((16, 16), Image.NEAREST)
        side = tx[f['north']['texture'][1:]].crop(tuple(f['north']['uv'])).resize((16, 16), Image.NEAREST)
        ab = int(round(a * (x1 - x0) / 16))
        side_h = max(1, int(round(a * (y1 - y0) / 16)))
        piece = box_img(top, side, ab, side_h)
        top_y = by - a * y1 / 16
        out.alpha_composite(piece, (int(out.width / 2 - ab), int(top_y - ab / 2)))
    return out.crop(out.getbbox())


# ----------------------------------------------------------------------------------------------- drawing helpers
def gradient(w, h, c0, c1, horizontal=True):
    im = Image.new('RGBA', (w, h))
    d = ImageDraw.Draw(im)
    n = w if horizontal else h
    for i in range(n):
        t = i / max(1, n - 1)
        c = tuple(int(c0[k] + (c1[k] - c0[k]) * t) for k in range(3)) + (255,)
        d.line([(i, 0), (i, h)] if horizontal else [(0, i), (w, i)], fill=c)
    return im


def stripes(im, step=26, width=11, color=(255, 255, 255, 10)):
    """Diagonal stripes like the banners of the page before."""
    lay = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for x in range(-im.height, im.width + im.height, step):
        d.line([(x, im.height), (x + im.height, 0)], fill=color, width=width)
    im.alpha_composite(lay)
    return im


def sparkles(im, n=40, seed=7, color=AVAILABLE):
    """Little four-pointed stars scattered over a background."""
    rng = np.random.default_rng(seed)
    d = ImageDraw.Draw(im)
    for _ in range(n):
        x, y = rng.integers(0, im.width), rng.integers(0, im.height)
        r = int(rng.integers(2, 5))
        a = int(rng.integers(40, 110))
        d.line([(x - r, y), (x + r, y)], fill=color + (a,), width=2)
        d.line([(x, y - r), (x, y + r)], fill=color + (a,), width=2)
    return im


def night(w, h, seed=7):
    im = gradient(w, h, (30, 15, 52), (54, 26, 90), horizontal=False)
    stripes(im)
    return sparkles(im, n=w * h // 22000, seed=seed)


def rounded(im, r):
    m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, im.width - 1, im.height - 1], r, fill=255)
    out = im.convert('RGBA')
    out.putalpha(Image.fromarray(np.minimum(np.asarray(out.split()[3]), np.asarray(m))))
    return out


def outlined(d, xy, text, f, fill=GOLD, stroke=5, stroke_fill=INK, anchor='la', drop=True):
    if drop:
        d.text((xy[0], xy[1] + stroke // 2 + 3), text, font=f, fill=stroke_fill, stroke_width=stroke,
               stroke_fill=stroke_fill, anchor=anchor)
    d.text(xy, text, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill, anchor=anchor)


def wrap(d, s, f, width):
    lines = ['']
    for word in s.split():
        t = (lines[-1] + ' ' + word).strip()
        if d.textlength(t, font=f) <= width or not lines[-1]:
            lines[-1] = t
        else:
            lines.append(word)
    return lines


def para(d, xy, s, f, width, fill=TEXT, gap=5, anchor='la'):
    """Wrapped text; anchor 'ma' centres every line on x. Returns the y below the last line."""
    x, y = xy
    asc, desc = f.getmetrics()
    for ln in wrap(d, s, f, width):
        d.text((x, y), ln, font=f, fill=fill, anchor=anchor)
        y += asc + desc + gap
    return y


def shadowed(im, layer, xy, blur=10, offset=(0, 6), alpha=140, color=(8, 0, 18)):
    sh = Image.new('RGBA', (layer.width + 4 * blur, layer.height + 4 * blur), (0, 0, 0, 0))
    a = layer.split()[3].point(lambda v: v * alpha // 255)
    sh.paste(color + (255,), (2 * blur, 2 * blur), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    im.alpha_composite(sh, (int(xy[0] - 2 * blur + offset[0]), int(xy[1] - 2 * blur + offset[1])))
    im.alpha_composite(layer, (int(xy[0]), int(xy[1])))


def card(w, h, r=18, fill=PANEL, outline=EDGE, width=4, glow=True):
    """A panel of the quest book: dark purple, a purple frame and a thin lilac line inside."""
    c = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    g = gradient(w, h, tuple(min(255, v + 10) for v in fill), fill, horizontal=False)
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], r, fill=255)
    c.paste(g, (0, 0), m)
    d = ImageDraw.Draw(c)
    d.rounded_rectangle([0, 0, w - 1, h - 1], r, outline=outline + (255,), width=width)
    if glow:
        d.rounded_rectangle([width + 2, width + 2, w - width - 3, h - width - 3], max(2, r - width - 2),
                            outline=ACCENT + (60,), width=1)
    return c


def node(shape, size, fill=PANEL_HI, ring=ACCENT):
    """A quest node of the book: the shape texture (textures/gui/shapes) as a ring with a dark inside."""
    s = tex('gui/shapes/' + shape).resize((size, size), Image.LANCZOS)
    a = s.split()[3]
    out = Image.new('RGBA', (size, size), ring + (0,))
    out.putalpha(a)
    inner = a.resize((int(size * 0.84), int(size * 0.84)), Image.LANCZOS)
    pad = (size - inner.width) // 2
    body = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    fillim = Image.new('RGBA', inner.size, fill + (255,))
    fillim.putalpha(inner)
    body.alpha_composite(fillim, (pad, pad))
    out.alpha_composite(body)
    return out


def arrow(d, pts, color=GOLD, width=12, head=22, outline=INK):
    (xa, ya), (xb, yb) = pts[-2], pts[-1]
    ang = math.atan2(yb - ya, xb - xa)
    bx, by = xb - head * math.cos(ang), yb - head * math.sin(ang)
    nx, ny = -math.sin(ang), math.cos(ang)
    line = list(pts[:-1]) + [(bx + 2 * math.cos(ang), by + 2 * math.sin(ang))]
    tri = [(xb, yb), (bx + nx * head * 0.9, by + ny * head * 0.9), (bx - nx * head * 0.9, by - ny * head * 0.9)]
    mx, my = sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3
    big = [(mx + (px - mx) * 1.32, my + (py - my) * 1.32) for px, py in tri]
    for col, extra, t in ((outline, 7, big), (color, 0, tri)):
        r = (width + extra) / 2
        d.ellipse([line[0][0] - r, line[0][1] - r, line[0][0] + r, line[0][1] + r], fill=col)
        d.line(line, fill=col, width=width + extra, joint='curve')
        d.polygon(t, fill=col)


def badge(im, center, n, r=19, fill=BORDER, ring=GOLD):
    d = ImageDraw.Draw(im)
    x, y = center
    d.ellipse([x - r - 4, y - r - 4, x + r + 4, y + r + 4], fill=ring)
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill)
    d.text((x + 1, y + 2), str(n), font=pixel(int(r * 1.05)), fill=WHITE, anchor='mm')


def keycap(im, x, y, label, w=None, h=44, f=None):
    d = ImageDraw.Draw(im)
    f = f or pixel(18)
    w = w or max(h, int(d.textlength(label, font=f)) + 26)
    d.rounded_rectangle([x, y + 5, x + w, y + h + 5], 8, fill=EDGE)
    d.rounded_rectangle([x, y, x + w, y + h], 8, fill=AVAILABLE, outline=EDGE, width=2)
    d.text((x + w / 2, y + h / 2 + 1), label, font=f, fill=INK, anchor='mm')
    return w


def chip(d, xy, text, f=None, fill=NIGHT, ink=GOLD, outline=EDGE, anchor='mm', pad=12, h=34):
    """A file name or a command in a dark pill."""
    f = f or mono(18)
    tw = d.textlength(text, font=f) + 2 * pad
    x, y = xy
    if anchor == 'mm':
        x -= tw / 2
    d.rounded_rectangle([x, y - h / 2, x + tw, y + h / 2], 8, fill=fill, outline=outline, width=2)
    d.text((x + tw / 2, y + 1), text, font=f, fill=ink, anchor='mm')
    return tw


def sticker(text, size, fill=GOLD, ink=INK, angle=-12):
    s = size * 3
    g = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse([6, 6, s - 7, s - 7], fill=INK)
    d.ellipse([20, 20, s - 21, s - 21], fill=fill)
    d.text((s / 2, s / 2 + 4), text, font=small(int(s * 0.4)), fill=ink, anchor='mm')
    return g.rotate(angle, Image.BICUBIC).resize((size, size), Image.LANCZOS)


def save(im, name):
    """PNG (keeps the rounded corners); one that would be too big gets 255 colours plus a transparent one."""
    path = os.path.join(OUT, name)
    im.save(path, optimize=True)
    if os.path.getsize(path) > MAX_BYTES:
        p = im.convert('RGB').quantize(255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
        pal = p.getpalette()[:255 * 3] + [0, 0, 0]
        a = np.array(p)
        if im.mode == 'RGBA':
            a[np.asarray(im.split()[3]) < 128] = 255
        q = Image.fromarray(a, 'P')
        q.putpalette(pal)
        q.save(path, optimize=True, transparency=255)
    size = os.path.getsize(path)
    print(f'{name:24s} {im.size[0]}x{im.size[1]}  {size // 1024} KB')
    assert size < 1024 * 1024, name


def icon_name(c, item):
    n = c['lang']['item.questsicons.' + item]
    return n[:-5] if n.endswith(' Icon') else n


def icon(item, size):
    return tex('item/' + item, size)


def star_glow(size):
    """The Dream Star with a soft light around it."""
    s = icon('dream_star', size)
    g = Image.new('RGBA', (size * 2, size * 2), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse([size * 0.35, size * 0.35, size * 1.65, size * 1.65], fill=(255, 150, 240, 120))
    g = g.filter(ImageFilter.GaussianBlur(size * 0.18))
    g.alpha_composite(s, (size // 2, size // 2))
    return g


# ----------------------------------------------------------------------------------------------- header
def header(name='header_v2.png', size=(1600, 500)):
    """The quest book of Dreamcraft (book.png) with icons flying around it, the title on the left."""
    w, h = size
    im = night(w, h, seed=3)
    glow = Image.new('RGBA', size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([760, 10, 1620, 520], fill=(150, 60, 230, 90))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(80)))
    book = shot('book.png')
    bw = 740
    bh = round(book.height * bw / book.width)
    pic = rounded(book.resize((bw, bh), Image.LANCZOS).convert('RGBA'), 14)
    fr = Image.new('RGBA', (bw + 16, bh + 16), (0, 0, 0, 0))
    ImageDraw.Draw(fr).rounded_rectangle([0, 0, bw + 15, bh + 15], 20, fill=BORDER + (255,), outline=ACCENT + (255,), width=3)
    fr.alpha_composite(pic, (8, 8))
    bx, by = 812, (h - fr.height) // 2
    shadowed(im, fr, (bx, by), blur=16, offset=(0, 10), alpha=170)
    for item, sz, x, y in [('quest_crown', 104, 742, 20), ('quest_portal_two', 92, 1500, 18), ('quest_key', 84, 760, 360),
                           ('dream_star', 96, 1488, 380), ('chapter_lock', 72, 1130, 418), ('legendary_crate', 76, 1300, 410)]:
        shadowed(im, icon(item, sz), (x, y), blur=8, offset=(0, 6), alpha=150)
    d = ImageDraw.Draw(im)
    outlined(d, (62, 64), 'QUEST', pixel(92), fill=ACCENT, stroke=8)
    outlined(d, (62, 180), 'ICONS', pixel(92), fill=GOLD, stroke=8)
    st = sticker('1.1', 112)
    im.alpha_composite(st, (552, 150))
    d = ImageDraw.Draw(im)
    d.text((66, 318), 'Icons and a whole quest book', font=small(40), fill=AVAILABLE)
    tag = 'Minecraft 26.3 · Fabric'
    f = small(30)
    pw = d.textlength(tag, font=f) + 48
    d.rounded_rectangle([66, 382, 66 + pw, 434], 26, fill=BORDER, outline=GOLD, width=3)
    d.text((66 + pw / 2, 408), tag, font=f, fill=WHITE, anchor='mm')
    save(rounded(im, 30), name)


# ----------------------------------------------------------------------------------------------- strips
STRIPS = [('icons', 'THE ICONS', 'quest_crown', 'circle'),
          ('book', 'THE QUEST BOOK', 'quest_book', 'hexagon'),
          ('tasks', 'TASKS AND REWARDS', 'icon_exclamation', 'diamond'),
          ('crates', 'LOOT CRATES', 'legendary_crate', 'octagon'),
          ('team', 'PLAY TOGETHER', 'icon_heart', 'heart'),
          ('star', 'NEW IN 1.1: THE DREAM STAR', 'dream_star', 'octagon'),
          ('pack', 'FOR PACK MAKERS', 'icon_gear', 'gear')]


def strip(name, text, item, shape):
    h = 104
    im = gradient(W, h, (50, 22, 86), (104, 30, 168))
    stripes(im, color=(255, 255, 255, 12))
    sparkles(im, n=18, seed=len(name))
    nd = node(shape, 92, fill=NIGHT, ring=GOLD)
    im.alpha_composite(nd, (22, 6))
    ic = icon(item, 56)
    im.alpha_composite(ic, (68 - ic.width // 2, 52 - ic.height // 2))
    d = ImageDraw.Draw(im)
    size = 36
    while d.textlength(text, font=pixel(size)) > W - 372 and size > 20:
        size -= 2
    outlined(d, (136, 56), text, pixel(size), stroke=5, anchor='lm')
    # a little quest line on the right: done, done, waiting
    xs = [W - 196, W - 128, W - 60]
    d.line([(xs[0], 52), (xs[2], 52)], fill=GREEN, width=5)
    for k, (x, sh) in enumerate(zip(xs, ['circle', 'diamond', 'star'])):
        n = node(sh, 44, fill=PANEL, ring=GREEN if k < 2 else GOLD)
        im.alpha_composite(n, (x - 22, 30))
    save(rounded(im, 22), 'strip_%s_v2.png' % name)


def build_strips():
    for s in STRIPS:
        strip(*s)


# ----------------------------------------------------------------------------------------------- at a glance
NEW_11 = [  # changelog-1.1.0+26.3.md
    (lambda: icon('dream_star', 64), 'The Dream\nStar'),
    (lambda: pedestal(30), 'Dream Star\nPedestal'),
    (lambda: icon('quest_book', 60), 'The book names\nits key'),
    (lambda: van('item/name_tag', 60), 'Join a team\nwith one click'),
    (lambda: icon('icon_gear', 60), '{fixes}\nfixes'),
]


def glance(name='glance_v2.png'):
    c = counts()
    tiles = [
        ('quest_portal_two', 'hexagon', len(c['icons']), 'ICONS', 'for quests, chapters and rewards'),
        ('icon_exclamation', 'diamond', len(c['tasks']), 'TASKS', 'from items to places'),
        ('icon_chest', 'square', len(c['rewards']), 'REWARDS', 'items, XP, loot, commands ...'),
        ('icon_star', 'star', len(c['shapes']), 'SHAPES', 'for the quests on the map'),
        ('legendary_crate', 'octagon', len(c['crates']), 'CRATES', 'common, rare, epic, legendary'),
        ('icon_map', 'circle', c['pins'], 'PINS', 'quests you follow on screen'),
    ]
    cols, tw, th, gx, gy = 3, 368, 216, 16, 16
    top, band_h = 108, 186
    by = top + 2 * th + gy + 46
    h = by + band_h + 34
    im = night(W, h, seed=5)
    d = ImageDraw.Draw(im)
    outlined(d, (W // 2, 58), 'QUEST ICONS AT A GLANCE', pixel(36), stroke=6, anchor='mm')
    x0 = (W - cols * tw - (cols - 1) * gx) // 2
    for k, (item, shape, num, label, sub) in enumerate(tiles):
        x, y = x0 + (k % cols) * (tw + gx), top + (k // cols) * (th + gy)
        shadowed(im, card(tw, th), (x, y), blur=8, offset=(0, 5), alpha=150)
        nd = node(shape, 132, fill=NIGHT, ring=ACCENT)
        im.alpha_composite(nd, (x + 18, y + 22))
        ic = icon(item, 72)
        im.alpha_composite(ic, (x + 84 - ic.width // 2, y + 88 - ic.height // 2))
        d = ImageDraw.Draw(im)
        outlined(d, (x + 166, y + 100), str(num), pixel(64), stroke=6, anchor='ls')
        f = pixel(22)
        while d.textlength(label, font=f) > tw - 176:
            f = pixel(f.size - 1)
        d.text((x + 168, y + 140), label, font=f, fill=TEXT, anchor='ls')
        f = small(21)
        while d.textlength(sub, font=f) > tw - 28:
            f = small(f.size - 1)
        d.text((x + tw // 2, y + th - 24), sub, font=f, fill=AVAILABLE, anchor='ms')
    band = card(W - 32, band_h, r=22, fill=(66, 28, 112), outline=GOLD, width=4)
    im.alpha_composite(band, (16, by))
    d = ImageDraw.Draw(im)
    pill = 'NEW IN 1.1'
    f = pixel(22)
    pw = d.textlength(pill, font=f) + 44
    d.rounded_rectangle([W // 2 - pw / 2, by - 24, W // 2 + pw / 2, by + 24], 12, fill=GOLD, outline=INK, width=4)
    d.text((W // 2, by + 2), pill, font=f, fill=INK, anchor='mm')
    n = len(NEW_11)
    cw = (W - 64) / n
    for k, (ic, label) in enumerate(NEW_11):
        cx = 32 + cw * k + cw / 2
        pic = ic()
        im.alpha_composite(pic, (int(cx - pic.width / 2), by + 40 + (70 - pic.height) // 2))
        d = ImageDraw.Draw(im)
        for i, ln in enumerate(label.format(fixes=c['fixes']).split('\n')):
            d.text((cx, by + 122 + 28 * i), ln, font=small(23), fill=WHITE, anchor='ma')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the icons
def slot(size, fill=NIGHT, edge=EDGE):
    g = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.rounded_rectangle([0, 0, size - 1, size - 1], 12, fill=fill + (255,), outline=edge + (255,), width=3)
    return g


def icons_image(name='icons_v2.png'):
    """All icons of ModItems.ICONS with their names (lang), as they come in the creative tab."""
    c = counts()
    cols, cw, ch = 8, 144, 158
    rows = (len(c['icons']) + cols - 1) // cols
    x0 = (W - cols * cw) // 2
    H = 24 + rows * ch + 64
    im = night(W, H, seed=11)
    for k, item in enumerate(c['icons']):
        x, y = x0 + (k % cols) * cw, 24 + (k // cols) * ch
        sl = slot(104)
        im.alpha_composite(sl, (x + (cw - 104) // 2, y))
        ic = icon(item, 80)
        im.alpha_composite(ic, (x + (cw - ic.width) // 2, y + 12))
        d = ImageDraw.Draw(im)
        para(d, (x + cw / 2, y + 114), icon_name(c, item), small(19), cw - 8, anchor='ma', gap=0)
    d = ImageDraw.Draw(im)
    tab = c['lang']['itemgroup.questitems']
    d.text((W // 2, H - 34), 'All of them, the Quest Book, the crates and the Dream Star wait in the creative tab "%s".' % tab,
           font=small(21), fill=AVAILABLE, anchor='mm', stroke_width=4, stroke_fill=(40, 20, 68))
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the quest book
def legend_row(im, x, y, n, text, w, f=None, fill=TEXT):
    badge(im, (x + 20, y + 20), n, r=17)
    d = ImageDraw.Draw(im)
    return para(d, (x + 48, y + 6), text, f or small(20), w - 52, fill=fill, gap=2)


BOOK_MARKS = [  # (x, y) in book.png (1600x900): the chapter list, the map, a quest with a reward waiting, an open
    (336, 118, 'Chapters in groups, with how far you are in each.'),         # quest, Claim all and Edit
    (1060, 760, 'The quest map: drag it to move, scroll to zoom.'),
    (448, 408, 'Done, and a reward is waiting for you.'),
    (735, 548, 'You can start this one. Grey with a cross: still locked. A ? marks an optional quest.'),
    (1300, 98, 'Claim all rewards at once.'),
    (1474, 98, 'Edit: build the book in the game (operators).'),
]


def book_image(name='book_v2.png'):
    """book.png with its parts numbered (QuestBookScreen: node colours, badges, buttons)."""
    src = shot('book.png')
    bw = W - 32
    sc = bw / src.width
    bh = round(src.height * sc)
    pic = rounded(src.resize((bw, bh), Image.LANCZOS).convert('RGBA'), 14)
    leg_h = 200
    H = 16 + bh + 22 + leg_h + 18
    im = night(W, H, seed=13)
    fr = Image.new('RGBA', (bw + 8, bh + 8), (0, 0, 0, 0))
    ImageDraw.Draw(fr).rounded_rectangle([0, 0, bw + 7, bh + 7], 18, fill=BORDER + (255,))
    fr.alpha_composite(pic, (4, 4))
    shadowed(im, fr, (12, 12), blur=10, offset=(0, 6), alpha=150)
    for k, (x, y, _) in enumerate(BOOK_MARKS):
        badge(im, (16 + x * sc, 16 + y * sc), k + 1, r=21)
    ly = 16 + bh + 22
    panel = card(W - 32, leg_h)
    im.alpha_composite(panel, (16, ly))
    colw = (W - 64) / 3
    for k, (_, _, text) in enumerate(BOOK_MARKS):
        legend_row(im, int(32 + colw * (k % 3)), ly + 16 + (k // 3) * 88, k + 1, text, int(colw - 14))
    save(rounded(im, 26), name)


QUEST_BOX = (510, 100, 1500, 872)          # the open quest in quest.png (1600x900)
QUEST_MARKS = [(560, 225, 'The story of the quest, with colours and pictures.'),
               (560, 362, 'Which quests come first.'),
               (560, 470, 'Its tasks, ticked off one by one.'),
               (560, 655, 'Its rewards: claim them when you are done.'),
               (628, 818, 'Pin it to keep its tasks on your screen.')]


def quest_image(name='quest_v2.png'):
    """quest.png cut to the open quest, its parts numbered in the margin; next to it the four ways to open the book."""
    src = shot('quest.png').crop(QUEST_BOX)
    qw = 600
    sc = qw / src.width
    qh = round(src.height * sc)
    H = qh + 40
    im = night(W, H, seed=17)
    pic = rounded(src.resize((qw, qh), Image.LANCZOS).convert('RGBA'), 12)
    fr = Image.new('RGBA', (qw + 8, qh + 8), (0, 0, 0, 0))
    ImageDraw.Draw(fr).rounded_rectangle([0, 0, qw + 7, qh + 7], 16, fill=BORDER + (255,))
    fr.alpha_composite(pic, (4, 4))
    qx = 66
    shadowed(im, fr, (qx, 16), blur=10, offset=(0, 6), alpha=150)
    d = ImageDraw.Draw(im)
    for k, (x, y, _) in enumerate(QUEST_MARKS):
        yy = 20 + (y - QUEST_BOX[1]) * sc
        d.line([(36, yy), (qx + 4 + (x - QUEST_BOX[0]) * sc - 16, yy)], fill=GOLD, width=3)
        badge(im, (34, yy), k + 1, r=18)
        d = ImageDraw.Draw(im)
    rx = qx + qw + 8 + 22
    rw = W - rx - 16
    rows = []
    tmp = ImageDraw.Draw(Image.new('RGB', (10, 10)))
    for _, _, text in QUEST_MARKS:
        rows.append(len(wrap(tmp, text, small(20), rw - 26 - 52)))
    leg_h = 26 + sum(r * 26 + 14 for r in rows)
    im.alpha_composite(card(rw, leg_h), (rx, 16))
    y = 30
    for k, (_, _, text) in enumerate(QUEST_MARKS):
        y = legend_row(im, rx + 14, y, k + 1, text, rw - 26) + 9
    oy = 16 + leg_h + 18
    oh = H - oy - 16
    im.alpha_composite(card(rw, oh), (rx, oy))
    d = ImageDraw.Draw(im)
    d.text((rx + rw / 2, oy + 30), 'OPEN THE BOOK', font=pixel(20), fill=GOLD, anchor='mm')
    cells = []
    k = Image.new('RGBA', (60, 56), (0, 0, 0, 0))
    keycap(k, 0, 0, 'J', w=52, h=46)
    cells.append((k, 'Press J'))
    cells.append((icon('quest_book', 52), 'The Quest Book'))
    env = Image.new('RGBA', (60, 52), (0, 0, 0, 0))
    ed = ImageDraw.Draw(env)
    ed.rectangle([2, 2, 57, 49], fill=(198, 198, 198), outline=(40, 40, 40), width=3)
    ed.rectangle([12, 13, 47, 38], fill=WHITE, outline=(80, 80, 80), width=2)
    ed.line([(12, 13), (30, 28), (47, 13)], fill=(80, 80, 80), width=2)
    cells.append((env, 'Button in the inventory'))
    cells.append((None, 'or type /quests open'))
    cw = rw / 3
    for i, (pic_, label) in enumerate(cells[:3]):
        cx = rx + cw * i + cw / 2
        im.alpha_composite(pic_, (int(cx - pic_.width / 2), oy + 56))
        d = ImageDraw.Draw(im)
        para(d, (cx, oy + 118), label, small(19), cw - 10, anchor='ma', gap=0, fill=AVAILABLE)
    chip(d, (rx + rw / 2, oy + oh - 34), '/quests open', h=38)
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- tasks and rewards
def checkbox(size):
    g = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.rounded_rectangle([3, 3, size - 4, size - 4], 6, fill=PANEL_HI, outline=AVAILABLE, width=3)
    d.line([(size * 0.25, size * 0.52), (size * 0.43, size * 0.7), (size * 0.76, size * 0.3)], fill=GREEN, width=max(4, size // 9),
           joint='curve')
    return g


def chest_icon(a=26):
    t = van('entity/chest/normal')
    sq = lambda im_: im_.resize((16, 16), Image.NEAREST)
    front = Image.new('RGBA', (14, 15))
    front.paste(t.crop((42, 14, 56, 19)), (0, 0))
    front.paste(t.crop((42, 33, 56, 43)), (0, 5))
    front.alpha_composite(t.crop((1, 1, 3, 5)), (6, 3))
    side = Image.new('RGBA', (14, 15))
    side.paste(t.crop((0, 14, 14, 19)), (0, 0))
    side.paste(t.crop((0, 33, 14, 43)), (0, 5))
    return cube(sq(t.crop((28, 0, 42, 14))), sq(front), sq(side), a)


def three(items, size=32):
    """Three items side by side, a little fanned out (the choice reward)."""
    g = Image.new('RGBA', (size + 2 * (size // 3), size + 8), (0, 0, 0, 0))
    for k, it in enumerate(items):
        ic = it.resize((size, size), Image.NEAREST)
        g.alpha_composite(ic, (k * (size // 3), 8 - 4 * k))
    return g


TASKS = {  # quest/task/Tasks.java TYPES, in that order
    'item': ('Item', 'Have items, or hand them in', lambda: icon('icon_chest', 52)),
    'checkmark': ('Checkmark', 'Just tick the box', lambda: checkbox(50)),
    'kill': ('Kill', 'Defeat mobs of a type or tag', lambda: icon('icon_sword', 52)),
    'advancement': ('Advancement', 'Earn it, or one criterion', lambda: icon('icon_trophy', 52)),
    'dimension': ('Dimension', 'Visit a dimension', lambda: icon('quest_portal_one', 52)),
    'biome': ('Biome', 'Find a biome or biome tag', lambda: icon('icon_tree', 52)),
    'structure': ('Structure', 'Stand inside a structure', lambda: icon('quest_dungeon', 52)),
    'xp': ('XP', 'Reach levels or pay them', lambda: van('item/experience_bottle', 52)),
    'stat': ('Statistic', 'Reach a value, like jumps', lambda: van('item/feather', 52)),
    'observation': ('Observation', 'Look at a block or mob', lambda: van('item/spyglass', 52)),
    'location': ('Location', 'Reach a place in a radius', lambda: icon('icon_compass', 52)),
}
REWARDS = {  # quest/reward/Rewards.java TYPES, in that order
    'item': ('Item', 'Any item, any amount', lambda: van('item/diamond', 52)),
    'xp': ('XP', 'Experience points', lambda: van('item/experience_bottle', 52)),
    'xp_levels': ('XP Levels', 'Whole levels', lambda: van('item/enchanted_book', 52)),
    'command': ('Command', 'Runs a command for you', lambda: vcube('block/command_block_back', 'block/command_block_side',
                                                                     'block/command_block_front', a=26)),
    'random': ('Random', 'A weighted pick from a table', lambda: icon('icon_question', 52)),
    'choice': ('Choice', 'You pick one of several', lambda: three([van('item/iron_sword'), van('item/golden_sword'),
                                                                    van('item/diamond_sword')])),
    'loot': ('Loot', 'Rolls a loot table', lambda: chest_icon(26)),
    'advancement': ('Advancement', 'Grants an advancement', lambda: icon('icon_trophy', 52)),
}


def entry_grid(im, x, y, w, entries, cols, cell_h=92):
    cw = w / cols
    for k, (head, sub, ic) in enumerate(entries):
        cx, cy = x + cw * (k % cols), y + (k // cols) * cell_h
        pic = ic()
        im.alpha_composite(pic, (int(cx + 12 + (56 - pic.width) / 2), int(cy + 10 + (56 - pic.height) / 2)))
        d = ImageDraw.Draw(im)
        d.text((cx + 80, cy + 26), head, font=small(23), fill=GOLD, anchor='ls')
        para(d, (cx + 80, cy + 34), sub, small(18), cw - 90, fill=TEXT, gap=1)


def tasks_image(name='tasks_v2.png'):
    c = counts()
    assert list(TASKS) == c['tasks'] and list(REWARDS) == c['rewards'], 'task or reward types changed'
    tasks = [TASKS[t] for t in c['tasks']]
    rewards = [REWARDS[r] for r in c['rewards']]
    th = 70 + 3 * 92 + 10
    rh = 70 + 2 * 92 + 60
    H = 16 + th + 18 + rh + 16
    im = night(W, H, seed=19)
    for (y, h, title_, entries) in ((16, th, '%d TASKS' % len(tasks), tasks), (16 + th + 18, rh, '%d REWARDS' % len(rewards), rewards)):
        im.alpha_composite(card(W - 32, h), (16, y))
        d = ImageDraw.Draw(im)
        outlined(d, (40, y + 40), title_, pixel(32), stroke=5, anchor='lm')
        entry_grid(im, 28, y + 64, W - 56, entries, 4)
    d = ImageDraw.Draw(im)
    y = 16 + th + 18 + rh - 40
    x = 44
    for t in ['for each player or once for the team', 'claimed by hand or on its own', 'Claim all collects everything']:
        x += chip(d, (x, y), t, f=small(19), ink=AVAILABLE, anchor='la', h=38) + 14
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- loot crates
RARITY = {'common_crate': (255, 255, 255), 'rare_crate': (255, 255, 85), 'epic_crate': (85, 255, 255),
          'legendary_crate': (255, 85, 255)}         # Minecraft's rarity colours: common, uncommon, rare, epic


def crates_image(name='crates_v2.png'):
    """LootCrateItem: each crate rolls the reward table with its own name; QuestRewardItem: the Mystery Reward."""
    c = counts()
    H = 16 + 330 + 18 + 230 + 16
    im = night(W, H, seed=23)
    # row 1: crates -> table -> reward
    cw1, cw2, cw3 = 470, 330, 300
    gap = (W - 32 - cw1 - cw2 - cw3) / 2
    xs = [16, 16 + cw1 + gap, 16 + cw1 + cw2 + 2 * gap]
    k1 = card(cw1, 330)
    d1 = ImageDraw.Draw(k1)
    d1.text((cw1 / 2, 34), 'OPEN A CRATE', font=pixel(20), fill=GOLD, anchor='mm')
    n = len(c['crates'])
    for i, cr in enumerate(c['crates']):
        cx = 20 + (cw1 - 40) / n * (i + 0.5)
        ic = icon(cr, 84)
        k1.alpha_composite(ic, (int(cx - 42), 80))
        nm = c['lang']['item.questsicons.' + cr].replace(' Loot Crate', '')
        d1 = ImageDraw.Draw(k1)
        d1.text((cx, 190), nm, font=small(21), fill=RARITY[cr], anchor='mm')
    para(d1, (cw1 / 2, 236), 'Right-click to open. They are items, so they make great quest rewards.', small(20),
         cw1 - 40, anchor='ma', gap=2)
    k2 = card(cw2, 330)
    d2 = ImageDraw.Draw(k2)
    d2.text((cw2 / 2, 34), 'ITS TABLE', font=pixel(20), fill=GOLD, anchor='mm')
    chip(d2, (cw2 / 2, 82), 'reward_tables/rare_crate.snbt', f=mono(16), h=34, pad=8)
    rows = [(van('item/diamond', 36), '1x', '5'), (van('item/golden_apple', 36), '2x', '10'),
            (van('item/experience_bottle', 36), '8x', '25')]
    d2.text((96, 122), 'count', font=small(17), fill=MUTED, anchor='mm')
    d2.text((214, 122), 'weight', font=small(17), fill=MUTED, anchor='mm')
    for i, (ic, cnt, wt) in enumerate(rows):
        y = 140 + i * 50
        k2.alpha_composite(ic, (30, y))
        d2 = ImageDraw.Draw(k2)
        d2.text((96, y + 18), cnt, font=small(22), fill=TEXT, anchor='mm')
        d2.text((214, y + 18), wt, font=small(22), fill=TEXT, anchor='mm')
    para(d2, (cw2 / 2, 296), 'Higher weight, more likely.', small(19), cw2 - 30, anchor='ma', fill=AVAILABLE)
    k3 = card(cw3, 330)
    d3 = ImageDraw.Draw(k3)
    d3.text((cw3 / 2, 34), 'YOUR LOOT', font=pixel(20), fill=GOLD, anchor='mm')
    g = Image.new('RGBA', (200, 200), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse([40, 40, 160, 160], fill=(255, 210, 120, 120))
    g = g.filter(ImageFilter.GaussianBlur(18))
    k3.alpha_composite(g, (cw3 // 2 - 100, 40))
    k3.alpha_composite(van('item/golden_apple', 96), (cw3 // 2 - 48, 92))
    d3 = ImageDraw.Draw(k3)
    para(d3, (cw3 / 2, 226), 'A random pick from the table the pack fills.', small(20), cw3 - 36, anchor='ma', gap=2)
    for k, x in zip((k1, k2, k3), xs):
        shadowed(im, k, (x, 16), blur=8, offset=(0, 5), alpha=150)
    d = ImageDraw.Draw(im)
    for i in range(2):
        xa = xs[i] + (cw1, cw2)[i] + 6
        arrow(d, [(xa, 181), (xs[i + 1] - 6, 181)], width=10, head=18)
    # row 2: the Mystery Reward
    y2 = 16 + 330 + 18
    k4 = card(W - 32, 230, fill=(56, 26, 84), outline=GOLD)
    im.alpha_composite(k4, (16, y2))
    mr = icon('quest_reward', 140)
    im.alpha_composite(mr, (60, y2 + 44))
    d = ImageDraw.Draw(im)
    outlined(d, (240, y2 + 56), c['lang']['item.questsicons.quest_reward'].upper(), pixel(28), stroke=5, anchor='lm')
    para(d, (240, y2 + 96), 'Right-click it for a random item from the whole game, from every mod you have.', small(24),
         560, fill=TEXT, gap=4)
    for i, (num, lab) in enumerate([(str(c['uses']), 'uses'), ('1-%d' % c['per_use'], 'items each')]):
        x = 880 + i * 150
        outlined(d, (x, y2 + 104), num, pixel(46), stroke=5, anchor='mm')
        d.text((x, y2 + 160), lab, font=small(22), fill=AVAILABLE, anchor='mm')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- teams
def head(skin, size):
    t = van('entity/player/wide/' + skin)
    h = t.crop((8, 8, 16, 16))
    h.alpha_composite(t.crop((40, 8, 48, 16)))
    return h.resize((size, size), Image.NEAREST)


def team_image(name='team_v2.png'):
    """/quests team ... (server/QuestCommands.java), shared progress and team rewards (progress/TeamData.java),
    pins (client/gui/QuestHud.java) and toasts (client/gui/QuestToast.java)."""
    c = counts()
    steps = [('CREATE', '/quests team create', 'Give your team a name.', [head('steve', 64)]),
             ('INVITE', '/quests team invite', 'Or just click the invite in chat.', [head('steve', 64), head('alex', 64)]),
             ('JOIN', '/quests team join', 'Your friend joins your team.', [head('alex', 64)]),
             ('SHARE', '', 'Tasks and finished quests count for everyone.', [head('steve', 56), head('alex', 56), head('kai', 56)])]
    cw, ch = 266, 300
    gap = (W - 32 - 4 * cw) / 3
    H = 16 + ch + 18 + 190 + 16
    im = night(W, H, seed=29)
    xs = []
    for i, (t, cmd, text, heads) in enumerate(steps):
        k = card(cw, ch)
        d = ImageDraw.Draw(k)
        badge(k, (34, 34), i + 1, r=18)
        d = ImageDraw.Draw(k)
        d.text((62, 36), t, font=pixel(20), fill=GOLD, anchor='lm')
        tw = sum(h_.width for h_ in heads) + 10 * (len(heads) - 1)
        x = (cw - tw) // 2
        for h_ in heads:
            k.alpha_composite(h_, (x, 72))
            x += h_.width + 10
        d = ImageDraw.Draw(k)
        if cmd:
            chip(d, (cw / 2, 170), cmd, f=mono(17), h=34)
        para(d, (cw / 2, 200 if cmd else 170), text, small(20), cw - 30, anchor='ma', gap=2)
        x0 = 16 + i * (cw + gap)
        shadowed(im, k, (x0, 16), blur=8, offset=(0, 5), alpha=150)
        xs.append(x0)
    d = ImageDraw.Draw(im)
    for i in range(3):
        arrow(d, [(xs[i] + cw + 5, 16 + ch / 2), (xs[i + 1] - 5, 16 + ch / 2)], width=9, head=16)
    y2 = 16 + ch + 18
    half = (W - 32 - 18) // 2
    for j, (title_, text, pic) in enumerate([
            ('PIN QUESTS', 'Up to %d pinned quests stay in the corner of your screen, with their tasks.' % c['pins'], icon('icon_map', 84)),
            ('TOASTS', 'A pop-up cheers when you finish a quest or a whole chapter.', icon('icon_trophy', 84))]):
        k = card(half, 190)
        k.alpha_composite(pic, (28, 52))
        d = ImageDraw.Draw(k)
        d.text((136, 46), title_, font=pixel(20), fill=GOLD, anchor='lm')
        para(d, (136, 70), text, small(21), half - 160, gap=3)
        shadowed(im, k, (16 + j * (half + 18), y2), blur=8, offset=(0, 5), alpha=150)
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the dream star
def grid3(keys, pattern, size=56, gap=6):
    """A crafting grid like the game's (pattern rows, keys: letter -> icon)."""
    g = Image.new('RGBA', (3 * size + 2 * gap, 3 * size + 2 * gap), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    for r, row in enumerate(pattern):
        for col, ch in enumerate(row):
            x, y = col * (size + gap), r * (size + gap)
            d.rectangle([x, y, x + size - 1, y + size - 1], fill=(139, 139, 139))
            d.line([(x, y), (x + size - 1, y)], fill=(55, 55, 55), width=3)
            d.line([(x, y), (x, y + size - 1)], fill=(55, 55, 55), width=3)
            d.line([(x, y + size - 2), (x + size - 1, y + size - 2)], fill=WHITE, width=3)
            d.line([(x + size - 2, y), (x + size - 2, y + size - 1)], fill=WHITE, width=3)
            if ch != ' ':
                ic = keys[ch]
                ic = ic.resize((size - 14, (size - 14) * ic.height // ic.width), Image.NEAREST) if ic.width > size - 14 else ic
                g.alpha_composite(ic, (x + (size - ic.width) // 2, y + (size - ic.height) // 2))
    return g


def vanilla_icon(item_id, size=42):
    """Icon of a vanilla item of a recipe: blocks as cubes, items flat (enchanted ones shimmer)."""
    name = item_id.split(':')[1]
    blocks = {'dragon_egg': ('block/dragon_egg',), 'netherite_block': ('block/netherite_block',),
              'quartz_pillar': ('block/quartz_pillar_top', 'block/quartz_pillar_side'),
              'polished_blackstone': ('block/polished_blackstone',), 'heavy_core': ('block/heavy_core',)}
    if name in blocks:
        return vcube(*blocks[name], a=size // 2)
    if name == 'enchanted_golden_apple':
        return glint(van('item/golden_apple', size))
    return van('item/' + name, size)


def recipe(name):
    r = json.loads(read(DATA, 'recipe', name + '.json'))
    keys = {}
    for k, v in r['key'].items():
        keys[k] = vanilla_icon(v[0] if isinstance(v, list) else v)
    return keys, r['pattern']


def star_image(name='star_v2.png'):
    """The Dream Star (DreamStarItem: fire and explosion proof, never despawns), the pedestal
    (DreamStarPedestalBlock / Renderer) and the recipes of data/questsicons/recipe."""
    c = counts()
    H = 16 + 400 + 18 + 300 + 16
    im = night(W, H, seed=31)
    k = card(W - 32, 400, fill=(40, 18, 64), outline=GOLD)
    im.alpha_composite(k, (16, 16))
    sg = star_glow(150)
    im.alpha_composite(sg, (70, 66))
    ped = pedestal(78)
    px, py = 420, 16 + 400 - 36 - ped.height
    glow = Image.new('RGBA', (300, 300), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([60, 40, 240, 220], fill=(200, 120, 255, 110))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(30)), (px + ped.width // 2 - 150, py - 210))
    im.alpha_composite(ped, (px, py))
    im.alpha_composite(icon('dream_star', 84), (px + ped.width // 2 - 42, py - 112))
    d = ImageDraw.Draw(im)
    for (sx, sy, r) in [(px + 6, py - 70, 7), (px + ped.width - 4, py - 120, 6), (px + ped.width // 2 + 64, py - 30, 5)]:
        d.line([(sx - r, sy), (sx + r, sy)], fill=WHITE, width=3)
        d.line([(sx, sy - r), (sx, sy + r)], fill=WHITE, width=3)
    d.text((145, 330), 'Dream Star', font=small(24), fill=TEXT, anchor='mm')
    d.text((px + ped.width // 2, 16 + 400 - 18), 'on its pedestal', font=small(21), fill=AVAILABLE, anchor='mm')
    facts = [(van('item/blaze_powder', 40), 'Cannot burn in fire or lava'),
             (vcube('block/tnt_top', 'block/tnt_side', a=20), 'Survives explosions'),
             (van('item/clock_00', 40), 'Never despawns when dropped'),
             (icon('dream_star', 40), 'Floats, turns, sparkles and lights up the room'),
             (icon('icon_trophy', 40), 'The challenge "%s"' % c['lang']['advancements.questsicons.dream_star.title'])]
    fx = 640
    outlined(d, (fx, 62), '"%s"' % c['lang']['item.questsicons.dream_star.tooltip'], small(27), fill=GOLD, stroke=3)
    for i, (ic, t) in enumerate(facts):
        y = 112 + i * 56
        im.alpha_composite(ic, (fx, y))
        d = ImageDraw.Draw(im)
        d.text((fx + 56, y + 21), t, font=small(22), fill=TEXT, anchor='lm')
    # recipes
    y2 = 16 + 400 + 18
    kv, pv = recipe('dream_star_vanilla')
    kp, pp = recipe('dream_star_pedestal')
    cards_ = [('DREAM STAR', grid3(kv, pv), 'Anywhere. In Dreamcraft it takes key items of the whole pack.'),
              ('PEDESTAL', grid3(kp, pp), 'Right-click it with the star. An empty hand takes it back.')]
    half = (W - 32 - 18) // 2
    for j, (t, g, text) in enumerate(cards_):
        kk = card(half, 300)
        kk.alpha_composite(g, (26, 76))
        dd = ImageDraw.Draw(kk)
        dd.text((26, 42), t, font=pixel(20), fill=GOLD, anchor='lm')
        arrow(dd, [(g.width + 40, 76 + g.height / 2), (g.width + 82, 76 + g.height / 2)], width=9, head=16)
        res = icon('dream_star', 64) if j == 0 else pedestal(30)
        kk.alpha_composite(res, (g.width + 92 + (64 - res.width) // 2, int(76 + g.height / 2 - res.height / 2)))
        para(dd, (g.width + 176, 92), text, small(20), half - g.width - 196, gap=3)
        shadowed(im, kk, (16 + j * (half + 18), y2), blur=8, offset=(0, 5), alpha=150)
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- for pack makers
SNBT = ['{', '  id: "punch_a_tree"', '  title: "Punch a Tree"', '  dependencies: ["welcome"]',
        '  tasks: [{ type: "item"', '    item: "#minecraft:logs"', '    count: 8 }]', '  rewards: [{ type: "item"',
        '    item: "minecraft:apple"', '    count: 4 }]', '}']


def pack_image(name='pack_v2.png'):
    """The quest files (server/QuestServer.java, the FTB Quests layout), the example quest of quest/StarterBook.java,
    the editor (server/QuestEditor.java), the commands (server/QuestCommands.java) and the shapes (QuestShape)."""
    c = counts()
    ch = 330
    H = 16 + ch + 18 + 210 + 16
    im = night(W, H, seed=37)
    w1, w2, w3 = 330, 430, 340
    gap = (W - 32 - w1 - w2 - w3) / 2
    xs = [16, 16 + w1 + gap, 16 + w1 + w2 + 2 * gap]
    # files
    k1 = card(w1, ch)
    d1 = ImageDraw.Draw(k1)
    d1.text((22, 34), 'THE FILES', font=pixel(20), fill=GOLD, anchor='lm')
    tree = [(0, 'config/questsicons/quests/', GOLD), (1, 'data.snbt', TEXT), (1, 'chapter_groups.snbt', TEXT),
            (1, 'chapters/', GOLD), (2, 'my_chapter.snbt', TEXT), (1, 'reward_tables/', GOLD), (2, 'my_table.snbt', TEXT)]
    for i, (lvl, t, col) in enumerate(tree):
        y = 72 + i * 30
        x = 24 + lvl * 26
        if lvl:
            d1.line([(x - 16, y - 18), (x - 16, y), (x - 4, y)], fill=MUTED, width=2)
        d1.text((x, y), t, font=mono(18), fill=col, anchor='lm')
    para(d1, (22, 268), 'The FTB Quests layout. The first start writes an example chapter.', small(18), w1 - 40, gap=1,
         fill=AVAILABLE)
    # snbt
    k2 = card(w2, ch)
    d2 = ImageDraw.Draw(k2)
    d2.text((22, 34), 'A QUEST', font=pixel(20), fill=GOLD, anchor='lm')
    d2.rounded_rectangle([18, 58, w2 - 18, ch - 18], 10, fill=NIGHT, outline=EDGE, width=2)
    for i, ln in enumerate(SNBT):
        d2.text((32, 76 + i * 22), ln, font=mono(17), fill=GREEN if ':' in ln and '"' in ln else TEXT, anchor='lm')
    # editor
    k3 = card(w3, ch)
    d3 = ImageDraw.Draw(k3)
    d3.text((22, 34), 'OR IN THE GAME', font=pixel(20), fill=GOLD, anchor='lm')
    bx = Image.new('RGBA', (120, 50), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bx)
    bd.rectangle([0, 0, 119, 49], fill=(38, 24, 57), outline=(91, 58, 134), width=3)
    bd.text((60, 26), 'Edit', font=small(24), fill=TEXT, anchor='mm')
    k3.alpha_composite(bx, (22, 62))
    d3 = ImageDraw.Draw(k3)
    d3.text((158, 87), 'operators', font=small(19), fill=AVAILABLE, anchor='lm')
    para(d3, (22, 132), 'Drag quests, link them, right-click to edit one as SNBT, take icons, tasks and rewards from '
         'the item in your hand.', small(19), w3 - 40, gap=2)
    para(d3, (22, 262), 'Every change is saved to the files and sent to all players.', small(19), w3 - 40, gap=2,
         fill=GREEN)
    for k, x in zip((k1, k2, k3), xs):
        shadowed(im, k, (x, 16), blur=8, offset=(0, 5), alpha=150)
    # commands and shapes
    y2 = 16 + ch + 18
    kb = card(W - 32, 210)
    im.alpha_composite(kb, (16, y2))
    d = ImageDraw.Draw(im)
    d.text((40, y2 + 34), 'COMMANDS', font=pixel(18), fill=GOLD, anchor='lm')
    x = 220
    x += chip(d, (x, y2 + 34), '/quests', f=mono(19), anchor='la', h=36, ink=GREEN) + 12
    cmds = re.findall(r'literal\("(\w+)"\)', read(JAVA, 'server', 'QuestCommands.java'))
    subs = [k for k in ['open', 'reload', 'givebook', 'complete', 'reset', 'team'] if k in cmds]
    for t in subs:
        x += chip(d, (x, y2 + 34), t, f=mono(19), anchor='la', h=36) + 10
    d.text((40, y2 + 104), '%d SHAPES' % len(c['shapes']), font=pixel(18), fill=GOLD, anchor='lm')
    sw = (W - 32 - 230) / len(c['shapes'])
    for i, sh in enumerate(c['shapes']):
        cx = 220 + sw * i + sw / 2
        n = node(sh, 62, fill=PANEL_HI, ring=AVAILABLE if i % 3 else GREEN)
        im.alpha_composite(n, (int(cx - 31), y2 + 74))
        d = ImageDraw.Draw(im)
        d.text((cx, y2 + 158), sh, font=small(17), fill=MUTED, anchor='mm')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- main
ALL = ['counts', 'header', 'strips', 'glance', 'icons', 'book', 'tasks', 'crates', 'team', 'star', 'pack']

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    what = sys.argv[2:] or ALL
    if 'counts' in what:
        print({k: v for k, v in counts().items() if k != 'lang'})
    if 'header' in what:
        header()
    if 'strips' in what:
        build_strips()
    if 'glance' in what:
        glance()
    if 'icons' in what:
        icons_image()
    if 'book' in what:
        book_image()
        quest_image()
    if 'tasks' in what:
        tasks_image()
    if 'crates' in what:
        crates_image()
    if 'team' in what:
        team_image()
    if 'star' in what:
        star_image()
    if 'pack' in what:
        pack_image()

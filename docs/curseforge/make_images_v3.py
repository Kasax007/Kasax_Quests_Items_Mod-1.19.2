"""Images of the CurseForge page of Quest Icons 1.2 (docs/curseforge/img/*_v3.png): the header with "1.2" and both
Minecraft versions, the "at a glance" numbers with what is new in 1.2, the strips of the new import section and of the
Dream Star, and the picture that explains the FTB Quests import. Everything else of the page stays *_v2.png.
The drawing helpers, colours and fonts are the ones of make_images_v2.py.

  python3 docs/curseforge/make_images_v3.py FONT_DIR [what ...]

what: header glance strips import (default: everything)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_images_v2 as v2  # noqa: E402  (reads FONT_DIR from sys.argv[1])
from make_images_v2 import (W, BORDER, ACCENT, TEXT, MUTED, GOLD, GREEN, AVAILABLE, PANEL, EDGE, NIGHT, INK,  # noqa: E402
                            WHITE, pixel, small, mono, van, icon, shot, night, gradient, stripes, sparkles, rounded,
                            outlined, para, shadowed, card, node, arrow, badge, chip, sticker, save)

# Counted by the converter tests on six real modpack books (src/test/.../FtbImporterTest.java, 1.2.0 report):
# the biggest one had 4,978 quests.
TESTED_BOOKS, BIGGEST_BOOK = 6, '4,978'


def counts():
    c = v2.counts()
    c['shapes'] = [s for s in c['shapes'] if s != 'none']  # "none" only hides the frame, it is not a shape to pick
    return c


# ----------------------------------------------------------------------------------------------- header
def header(name='header_v3.png', size=(1600, 500)):
    """The header of 1.1 with the sticker "1.2" and both Minecraft versions."""
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
    im.alpha_composite(sticker('1.2', 112), (552, 150))
    d = ImageDraw.Draw(im)
    d.text((66, 318), 'Icons and a whole quest book', font=small(40), fill=AVAILABLE)
    x = 66
    for tag in ('Minecraft 26.3 & 26.2 · Fabric', 'FTB Quests import'):
        f = small(28)
        pw = d.textlength(tag, font=f) + 44
        new = tag.startswith('FTB')
        d.rounded_rectangle([x, 382, x + pw, 432], 25, fill=GOLD if new else BORDER, outline=INK if new else GOLD, width=3)
        d.text((x + pw / 2, 407), tag, font=f, fill=INK if new else WHITE, anchor='mm')
        x += pw + 14
    assert x < 800, 'the tags run into the book'
    save(rounded(im, 30), name)


# ----------------------------------------------------------------------------------------------- strips
def strip(name, text, item, shape):
    """A section strip like make_images_v2.strip, saved as strip_<name>_v3.png."""
    h = 104
    im = gradient(W, h, (50, 22, 86), (104, 30, 168))
    stripes(im, color=(255, 255, 255, 12))
    sparkles(im, n=18, seed=len(name))
    im.alpha_composite(node(shape, 92, fill=NIGHT, ring=GOLD), (22, 6))
    ic = icon(item, 56)
    im.alpha_composite(ic, (68 - ic.width // 2, 52 - ic.height // 2))
    d = ImageDraw.Draw(im)
    size = 36
    while d.textlength(text, font=pixel(size)) > W - 372 and size > 20:
        size -= 2
    outlined(d, (136, 56), text, pixel(size), stroke=5, anchor='lm')
    xs = [W - 196, W - 128, W - 60]
    d.line([(xs[0], 52), (xs[2], 52)], fill=GREEN, width=5)
    for k, (x, sh) in enumerate(zip(xs, ['circle', 'diamond', 'star'])):
        im.alpha_composite(node(sh, 44, fill=PANEL, ring=GREEN if k < 2 else GOLD), (x - 22, 30))
    save(rounded(im, 22), 'strip_%s_v3.png' % name)


def build_strips():
    strip('import', 'NEW IN 1.2: IMPORT FROM FTB QUESTS', 'quest_book', 'hexagon')
    strip('star', 'THE DREAM STAR', 'dream_star', 'octagon')


# ----------------------------------------------------------------------------------------------- at a glance
def version_chip(text='26.2', size=(112, 58)):
    w, h = size
    im = Image.new('RGBA', size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w - 1, h - 1], 14, fill=BORDER, outline=GOLD, width=4)
    d.text((w / 2, h / 2 + 2), text, font=pixel(22), fill=WHITE, anchor='mm')
    return im


def import_icon(size=64):
    """A paper with an arrow into the quest book."""
    im = Image.new('RGBA', (size + 40, size), (0, 0, 0, 0))
    im.alpha_composite(van('item/paper', size - 8), (0, 4))
    d = ImageDraw.Draw(im)
    arrow(d, [(size - 18, size // 2), (size + 2, size // 2)], width=7, head=12)
    im.alpha_composite(icon('quest_book', size - 8), (size + 4 - 8 + 4, 4))
    return im


NEW_12 = [  # changelog-1.2.0+26.3.md
    (import_icon, 'Import FTB\nQuests books'),
    (version_chip, 'Now also on\nMinecraft 26.2'),
    (lambda: icon('icon_question', 60), 'Optional\ntasks'),
    (lambda: van('item/iron_ingot', 60), 'Items by tag,\nlist or mod'),
    (lambda: icon('icon_map', 60), 'Books with thousands\nof quests'),
]


def glance(name='glance_v3.png'):
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
        im.alpha_composite(node(shape, 132, fill=NIGHT, ring=ACCENT), (x + 18, y + 22))
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
    im.alpha_composite(card(W - 32, band_h, r=22, fill=(66, 28, 112), outline=GOLD, width=4), (16, by))
    d = ImageDraw.Draw(im)
    pill = 'NEW IN 1.2'
    f = pixel(22)
    pw = d.textlength(pill, font=f) + 44
    d.rounded_rectangle([W // 2 - pw / 2, by - 24, W // 2 + pw / 2, by + 24], 12, fill=GOLD, outline=INK, width=4)
    d.text((W // 2, by + 2), pill, font=f, fill=INK, anchor='mm')
    cw = (W - 64) / len(NEW_12)
    for k, (ic, label) in enumerate(NEW_12):
        cx = 32 + cw * k + cw / 2
        pic = ic()
        im.alpha_composite(pic, (int(cx - pic.width / 2), by + 40 + (70 - pic.height) // 2))
        d = ImageDraw.Draw(im)
        for i, ln in enumerate(label.split('\n')):
            d.text((cx, by + 122 + 28 * i), ln, font=small(23), fill=WHITE, anchor='ma')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the import
def check(d, x, y, s=22, color=GREEN):
    d.line([(x, y + s * 0.55), (x + s * 0.38, y + s * 0.92), (x + s, y + s * 0.1)], fill=INK, width=9, joint='curve')
    d.line([(x, y + s * 0.55), (x + s * 0.38, y + s * 0.92), (x + s, y + s * 0.1)], fill=color, width=5, joint='curve')


def label_pill(d, cx, y, text, fill=GOLD, ink=INK, f=None):
    f = f or pixel(18)
    pw = d.textlength(text, font=f) + 36
    d.rounded_rectangle([cx - pw / 2, y - 20, cx + pw / 2, y + 20], 10, fill=fill, outline=INK, width=4)
    d.text((cx, y + 2), text, font=f, fill=ink, anchor='mm')


def import_image(name='import_v3.png'):
    """Left the FTB Quests folder, in the middle the three steps, right the book it becomes; below what comes along."""
    top, row_h, band_y, band_h = 158, 404, 608, 300
    h = band_y + band_h + 28
    im = night(W, h, seed=11)
    d = ImageDraw.Draw(im)
    outlined(d, (W // 2, 56), 'BRING YOUR FTB QUESTS BOOK', pixel(34), stroke=6, anchor='mm')
    d.text((W // 2, 104), 'No FTB Quests for 26.2 or 26.3? One command turns your book into a Quest Icons book.',
           font=small(24), fill=AVAILABLE, anchor='mm')

    # left: the FTB Quests folder
    lx, cw = 24, 330
    shadowed(im, card(cw, row_h), (lx, top), blur=8, offset=(0, 5), alpha=150)
    d = ImageDraw.Draw(im)
    label_pill(d, lx + cw / 2, top, 'FTB QUESTS', fill=AVAILABLE)
    tree = [(0, 'config/ftbquests/', GOLD), (1, 'quests/', GOLD), (2, 'data.snbt', TEXT), (2, 'chapter_groups.snbt', TEXT),
            (2, 'chapters/', GOLD), (3, 'getting_started.snbt', MUTED), (2, 'reward_tables/', GOLD),
            (2, 'lang/en_us.snbt', TEXT)]
    f = mono(18)
    y = top + 44
    for depth, text, color in tree:
        x = lx + 24 + depth * 22
        if depth:
            d.line([(x - 14, y - 10), (x - 14, y + 10), (x - 4, y + 10)], fill=EDGE, width=2)
        d.text((x, y + 10), text, font=f, fill=color, anchor='lm')
        y += 29
    d.text((lx + cw / 2, top + row_h - 92), 'Every FTB Quests layout:', font=small(20), fill=AVAILABLE, anchor='mm')
    xs = lx + 22
    for t in ('1.20', '1.21', '26.1 JSON5'):
        xs += chip(d, (xs, top + row_h - 50), t, f=mono(17), anchor='lm', ink=WHITE) + 10

    # right: the Quest Icons book
    rx = W - 24 - cw
    shadowed(im, card(cw, row_h), (rx, top), blur=8, offset=(0, 5), alpha=150)
    book = shot('book.png').crop((0, 70, 960, 900))
    pw = cw - 32
    ph = round(book.height * pw / book.width)
    pic = rounded(book.resize((pw, ph), Image.LANCZOS).convert('RGBA'), 10)
    fr = Image.new('RGBA', (pw + 8, ph + 8), (0, 0, 0, 0))
    ImageDraw.Draw(fr).rounded_rectangle([0, 0, pw + 7, ph + 7], 12, fill=BORDER + (255,), outline=ACCENT + (255,), width=2)
    fr.alpha_composite(pic, (4, 4))
    im.alpha_composite(fr, (rx + 12, top + 40))
    d = ImageDraw.Draw(im)
    label_pill(d, rx + cw / 2, top, 'QUEST ICONS')
    para(d, (rx + cw / 2, top + 40 + ph + 24), 'The same chapters, live for every player.',
         small(20), cw - 40, fill=TEXT, anchor='ma')

    # middle: the three steps
    mx0, mx1 = lx + cw + 22, rx - 22
    steps = [('config/ftbquests/quests', 'Copy the folder into your 26.2 or 26.3 game or server.'),
             ('/quests import ftbquests', 'Preview: a summary in chat and a report of every difference.'),
             ('/quests import ftbquests run', 'Your old book is backed up, the new one is live.')]
    sy = top + 30
    for n, (cmd, text) in enumerate(steps, 1):
        badge(im, (mx0 + 26, sy + 22), n, r=20)
        d = ImageDraw.Draw(im)
        f = mono(17)
        while d.textlength(cmd, font=f) + 24 > mx1 - mx0 - 62:
            f = mono(f.size - 1)
        chip(d, (mx0 + 60, sy + 22), cmd, f=f, anchor='lm', ink=GREEN if n > 1 else GOLD, h=38)
        para(d, (mx0 + 62, sy + 50), text, small(20), mx1 - mx0 - 66, fill=TEXT, gap=3)
        sy += 122
    arrow(d, [(lx + cw - 6, top + row_h // 2), (mx0 + 2, top + row_h // 2)], width=10, head=18)
    arrow(d, [(mx1 - 8, top + row_h // 2), (rx + 6, top + row_h // 2)], width=10, head=18)

    # below: what comes along
    im.alpha_composite(card(W - 48, band_h, r=22, fill=(66, 28, 112), outline=GOLD, width=4), (24, band_y))
    d = ImageDraw.Draw(im)
    label_pill(d, W // 2, band_y, 'WHAT COMES ALONG')
    items = ['Chapter groups, chapters, their order', 'Quest map: places, shapes, lines',
             'Titles and texts with their colours', 'Item tasks with tags and FTB filters',
             'Kill, advancement, biome, XP ... tasks', 'Rewards, loot tables and "choose one"',
             'Pictures on the quest map', 'Quest ids: a second import updates']
    colw = (W - 48 - 64) / 2
    for k, t in enumerate(items):
        x = 24 + 36 + (k % 2) * colw
        y = band_y + 42 + (k // 2) * 42
        check(d, x, y + 2)
        d.text((x + 36, y + 14), t, font=small(22), fill=WHITE, anchor='lm')
    y = band_y + 42 + 4 * 42 + 14
    d.line([(60, y - 6), (W - 60, y - 6)], fill=EDGE, width=2)
    d.text((W // 2, y + 16), 'Fluid, energy or KubeJS tasks become checkmarks with a clear title, the report lists each one.',
           font=small(20), fill=AVAILABLE, anchor='mm')
    d.text((W // 2, y + 52), 'Tested with %d big modpack books, up to %s quests in one book.' % (TESTED_BOOKS, BIGGEST_BOOK),
           font=small(22), fill=GOLD, anchor='mm')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- main
ALL = ['header', 'glance', 'strips', 'import']

if __name__ == '__main__':
    what = sys.argv[2:] or ALL
    if 'header' in what:
        header()
    if 'glance' in what:
        glance()
    if 'strips' in what:
        build_strips()
    if 'import' in what:
        import_image()

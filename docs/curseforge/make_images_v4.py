"""Images of the CurseForge page of Quest Icons 1.3 (docs/curseforge/img/*_v4.png): the header with "1.3", the "at a
glance" numbers with what is new in 1.3, the icon library by category, the quest-map pictures, the in-game editor
(cut from the self-test's screenshots) and their section strips. The drawing helpers, colours and fonts are the ones of
make_images_v2.py; the icons come from the generated library (tools/icons).

  python3 docs/curseforge/make_images_v4.py FONT_DIR SCREENSHOT_DIR [what ...]

SCREENSHOT_DIR: build/run/selftest/screenshots of a self-test run (qi_editor_*.png).
what: header glance strips icons maps editor (default: everything)."""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_images_v2 as v2  # noqa: E402  (reads FONT_DIR from sys.argv[1])
from make_images_v2 import (W, ASSETS, ACCENT, TEXT, MUTED, GOLD, GREEN, AVAILABLE, PANEL, EDGE, NIGHT, INK, BORDER,  # noqa: E402
                            WHITE, pixel, small, mono, night, gradient, stripes, sparkles, rounded, outlined, para,
                            shadowed, card, node, arrow, chip, sticker, save, shot)

SHOTS = sys.argv[2] if len(sys.argv) > 2 else ''
LIB = json.load(open(os.path.join(ASSETS, 'icon_library.json'), encoding='utf-8'))
LANG = json.load(open(os.path.join(ASSETS, 'lang', 'en_us.json'), encoding='utf-8'))


def icon_tex(item):
    """The texture of an icon of the library, or of another item of the mod."""
    for c in LIB['categories']:
        for i in c['icons']:
            if i['id'] == item and c['id'] != 'classic':
                return os.path.join(ASSETS, 'textures', 'item', 'icons', c['id'], item + '.png')
    return os.path.join(ASSETS, 'textures', 'item', item + '.png')


def icon(item, size):
    im = Image.open(icon_tex(item)).convert('RGBA')
    im = im.crop((0, 0, im.width, im.width))
    return im.resize((size, size), Image.NEAREST)


def counts():
    c = v2.counts()
    c['icons'] = [i['id'] for cat in LIB['categories'] for i in cat['icons']]
    c['categories'] = len(LIB['categories'])
    c['images'] = len(LIB['map_images'])
    c['shapes'] = [s for s in c['shapes'] if s != 'none']
    return c


# ----------------------------------------------------------------------------------------------- header
def header(name='header_v4.png', size=(1600, 500)):
    w, h = size
    im = night(w, h, seed=4)
    glow = Image.new('RGBA', size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([760, 10, 1620, 520], fill=(150, 60, 230, 90))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(80)))
    # A wall of icons on the right, fading in.
    ids = counts()['icons']
    pick = [i for i in ids if not i.startswith(('quest_portal', 'logo'))]
    step, sz = 64, 52
    x0, y0 = 820, 18
    k = 0
    for row in range(7):
        for col in range(12):
            x, y = x0 + col * step + (row % 2) * 24, y0 + row * step + 6
            if x > w - 40:
                continue
            ic = icon(pick[(k * 37) % len(pick)], sz)
            k += 1
            a = min(1.0, max(0.25, (x - 760) / 360))
            if a < 1:
                ic.putalpha(ic.getchannel('A').point(lambda v, a=a: int(v * a)))
            im.alpha_composite(ic, (x, y))
    d = ImageDraw.Draw(im)
    outlined(d, (62, 64), 'QUEST', pixel(92), fill=ACCENT, stroke=8)
    outlined(d, (62, 180), 'ICONS', pixel(92), fill=GOLD, stroke=8)
    im.alpha_composite(sticker('1.3', 112), (552, 150))
    d = ImageDraw.Draw(im)
    d.text((66, 318), '%d icons and a quest book you edit in game' % len(ids), font=small(38), fill=AVAILABLE)
    x = 66
    for tag in ('Minecraft 26.3 & 26.2 · Fabric', 'No SNBT needed'):
        f = small(28)
        pw = d.textlength(tag, font=f) + 44
        new = tag.startswith('No')
        d.rounded_rectangle([x, 382, x + pw, 432], 25, fill=GOLD if new else BORDER, outline=INK if new else GOLD, width=3)
        d.text((x + pw / 2, 407), tag, font=f, fill=INK if new else WHITE, anchor='mm')
        x += pw + 14
    save(rounded(im, 30), name)


# ----------------------------------------------------------------------------------------------- strips
def strip(name, text, item, shape):
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
    save(rounded(im, 22), 'strip_%s_v4.png' % name)


def build_strips():
    strip('icons', 'THE ICON LIBRARY', 'letter_q', 'rsquare')
    strip('maps', 'PICTURES FOR THE QUEST MAP', 'arrow_yellow_right', 'hexagon')
    strip('editor', 'NEW IN 1.3: EDIT IN GAME', 'ui_pencil', 'gear')
    strip('import', 'IMPORT FROM FTB QUESTS', 'quest_book', 'hexagon')


# ----------------------------------------------------------------------------------------------- at a glance
NEW_13 = [
    ('letter_a', 'Letters, numbers\nand symbols'),
    ('tech_battery_full', 'Icons for every\nchapter theme'),
    ('ui_pencil', 'Forms instead\nof SNBT'),
    ('ui_search', 'Icon and item\npickers'),
    ('arrow_cycle', 'Undo, copy,\npaste, multi-select'),
]


def glance(name='glance_v4.png'):
    c = counts()
    tiles = [
        ('letter_q', 'rsquare', len(c['icons']), 'ICONS', 'in %d categories' % c['categories']),
        ('arrow_yellow_right', 'hexagon', c['images'], 'MAP PICTURES', 'arrows, frames, banners ...'),
        ('icon_exclamation', 'diamond', len(c['tasks']), 'TASKS', 'from items to places'),
        ('icon_chest', 'square', len(c['rewards']), 'REWARDS', 'items, XP, loot, commands ...'),
        ('icon_star', 'star', len(c['shapes']), 'SHAPES', 'for the quests on the map'),
        ('legendary_crate', 'octagon', len(c['crates']), 'CRATES', 'common, rare, epic, legendary'),
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
        outlined(d, (x + 166, y + 100), str(num), pixel(56 if num >= 100 else 64), stroke=6, anchor='ls')
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
    pill = 'NEW IN 1.3'
    f = pixel(22)
    pw = d.textlength(pill, font=f) + 44
    d.rounded_rectangle([W // 2 - pw / 2, by - 24, W // 2 + pw / 2, by + 24], 12, fill=GOLD, outline=INK, width=4)
    d.text((W // 2, by + 2), pill, font=f, fill=INK, anchor='mm')
    cw = (W - 64) / len(NEW_13)
    for k, (ic, label) in enumerate(NEW_13):
        cx = 32 + cw * k + cw / 2
        pic = icon(ic, 64)
        im.alpha_composite(pic, (int(cx - pic.width / 2), by + 40 + (70 - pic.height) // 2))
        d = ImageDraw.Draw(im)
        for i, ln in enumerate(label.split('\n')):
            d.text((cx, by + 122 + 28 * i), ln, font=small(23), fill=WHITE, anchor='ma')
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the library
def icons_image(name='icons_v4.png'):
    """Every category of the library: its name and all its icons (36 px), on cards."""
    cats = [c for c in LIB['categories'] if c['id'] != 'classic'] + [c for c in LIB['categories'] if c['id'] == 'classic']
    sz, gap = 36, 6
    pad = 18
    inner = W - 2 * 24 - 2 * pad
    per_row = inner // (sz + gap)
    blocks = []
    for c in cats:
        rows = (len(c['icons']) + per_row - 1) // per_row
        blocks.append((c, rows))
    H = 30 + sum(40 + rows * (sz + gap) + 10 + 12 for _c, rows in blocks) + 18
    im = night(W, H, seed=12)
    y = 30
    for c, rows in blocks:
        bh = 40 + rows * (sz + gap) + 10
        im.alpha_composite(card(W - 48, bh, r=16, glow=False), (24, y))
        d = ImageDraw.Draw(im)
        title = LANG['questsicons.icons.category.' + c['id']]
        d.text((24 + pad, y + 12), title.upper(), font=pixel(16), fill=GOLD)
        tag = '#questsicons:icons/' + c['id']
        d.text((W - 24 - pad, y + 13), '%d · %s' % (len(c['icons']), tag), font=mono(16), fill=MUTED, anchor='ra')
        for k, i in enumerate(c['icons']):
            x = 24 + pad + (k % per_row) * (sz + gap)
            yy = y + 38 + (k // per_row) * (sz + gap)
            im.alpha_composite(icon(i['id'], sz), (x, yy))
        y += bh + 12
    save(rounded(im, 26), name)


def maps_image(name='maps_v4.png'):
    """The quest-map pictures on a map-coloured board, grouped as in the picture picker."""
    imgs = LIB['map_images']
    H = 30
    pad = 20
    placed = []
    x, y, rowh = pad + 24, 30 + 44, 0
    order = LIB['map_image_categories']
    cur = None
    titles = []
    for cat in order:
        x = pad + 24
        if placed:
            y += rowh + 30
            rowh = 0
        titles.append((cat, y - 34))
        for m in [m for m in imgs if m['category'] == cat]:
            s = 3 if m['width'] <= 48 else 2
            w, h = m['width'] * s, m['height'] * s
            if x + w > W - pad - 24:
                x = pad + 24
                y += rowh + 14
                rowh = 0
            placed.append((m, x, y, s))
            x += w + 16
            rowh = max(rowh, h)
        y += 40
    H = y + rowh + 30
    im = night(W, H, seed=13)
    im.alpha_composite(card(W - 48, H - 40, r=20, fill=(20, 12, 36), glow=False), (24, 20))
    d = ImageDraw.Draw(im)
    for cat, ty in titles:
        d.text((44, ty), LANG['questsicons.map_image.category.' + cat].upper(), font=pixel(16), fill=GOLD)
    for m, px, py, s in placed:
        pic = Image.open(os.path.join(ASSETS, 'textures', 'gui', 'map', m['id'] + '.png')).convert('RGBA')
        im.alpha_composite(pic.resize((pic.width * s, pic.height * s), Image.NEAREST), (px, py))
    save(rounded(im, 26), name)


# ----------------------------------------------------------------------------------------------- the editor
def screenshot(name, box=None):
    im = Image.open(os.path.join(SHOTS, name)).convert('RGB')
    return im.crop(box) if box else im


def framed(pic, w):
    h = round(pic.height * w / pic.width)
    p = rounded(pic.resize((w, h), Image.LANCZOS).convert('RGBA'), 10)
    fr = Image.new('RGBA', (w + 8, h + 8), (0, 0, 0, 0))
    ImageDraw.Draw(fr).rounded_rectangle([0, 0, w + 7, h + 7], 12, fill=BORDER + (255,), outline=ACCENT + (255,), width=2)
    fr.alpha_composite(p, (4, 4))
    return fr


def editor_image(name='editor_v4.png'):
    """Four screenshots of the self-test (map, quest form, task form, icon picker) with what each one shows."""
    panels = [('qi_editor_chapter_built.png', 'Build the map', 'Right-click: new quest. Drag to move, Shift-drag to link, Ctrl to select several.'),
              ('qi_editor_quest_form.png', 'A form for every quest', 'Title, text, icon, tasks, rewards, links, shape, flags.'),
              ('qi_editor_task_form.png', 'Every task and reward type', 'Items, mobs, biomes, advancements ... with pickers and checks.'),
              ('qi_editor_icon_picker.png', 'Pick an icon', 'The library by category with search, all other items in a tab.')]
    cw = (W - 3 * 24) // 2
    tops = []
    pics = []
    for f, t, s in panels:
        pics.append(framed(screenshot(f), cw - 24))
    ph = max(p.height for p in pics)
    card_h = ph + 120
    H = 110 + 2 * card_h + 24 + 90
    im = night(W, H, seed=21)
    d = ImageDraw.Draw(im)
    outlined(d, (W // 2, 54), 'EDIT YOUR QUESTS IN GAME', pixel(32), stroke=6, anchor='mm')
    for k, ((f, t, s), pic) in enumerate(zip(panels, pics)):
        x = 24 + (k % 2) * (cw + 24)
        y = 100 + (k // 2) * (card_h + 24)
        shadowed(im, card(cw, card_h), (x, y), blur=8, offset=(0, 5), alpha=150)
        im.alpha_composite(pic, (x + 8, y + 10))
        d = ImageDraw.Draw(im)
        d.text((x + 16, y + ph + 30), t, font=small(26), fill=GOLD)
        para(d, (x + 16, y + ph + 64), s, small(20), cw - 32, fill=TEXT)
    y = 100 + 2 * (card_h + 24) + 6
    d.text((W // 2, y + 20), 'Undo (Ctrl+Z) · Copy & paste · Delete asks twice · Every field checked before saving',
           font=small(24), fill=AVAILABLE, anchor='mm')
    d.text((W // 2, y + 56), 'Saves the same SNBT files, every player sees the change at once.', font=small(22), fill=GREEN, anchor='mm')
    save(rounded(im, 26), name)


ALL = ['header', 'glance', 'strips', 'icons', 'maps', 'editor']

if __name__ == '__main__':
    what = sys.argv[3:] or ALL
    if 'header' in what:
        header()
    if 'glance' in what:
        glance()
    if 'strips' in what:
        build_strips()
    if 'icons' in what:
        icons_image()
    if 'maps' in what:
        maps_image()
    if 'editor' in what:
        editor_image()

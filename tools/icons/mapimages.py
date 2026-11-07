"""Pictures for the quest map (chapter images): arrows, dividers, frames, panels, banners and labels. Pack makers place
them with the editor's "Add image here" (textures/gui/map/<id>.png). 'grid' is the default size on the map in quest
units (one unit = one quest spacing)."""
from pixelart import Canvas
import font

CATEGORIES = [
    ('arrows', 'Arrows'),
    ('lines', 'Dividers & Lines'),
    ('frames', 'Frames & Panels'),
    ('banners', 'Banners & Labels'),
]


def _rot(c):
    n = Canvas(c.h, c.w)
    n.outline = c.outline
    for y in range(c.h):
        for x in range(c.w):
            n.cells[x][c.h - 1 - y] = c.cells[y][x]
    return n


def _flip(c):
    n = Canvas(c.w, c.h)
    n.outline = c.outline
    for y in range(c.h):
        for x in range(c.w):
            n.cells[y][c.w - 1 - x] = c.cells[y][x]
    return n


def long_arrow(length=48, mat='cream'):
    c = Canvas(length, 16)
    for y in range(2, 14):
        k = y - 2 if y <= 7 else 13 - y
        for x in range(length - 9, length - 9 + k + 1):
            c.set(x, y, mat)
    for y in range(6, 10):
        for x in range(1, length - 9):
            c.set(x, y, mat)
    return c


def curved_arrow(mat='cream'):
    c = Canvas(32, 32)
    import math
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - 2, y + 0.5 - 30)
            if 20.5 <= r <= 24.5 and x >= 2 and y <= 29 and x <= 22:
                c.set(x, y, mat)
    # head pointing right at the top end
    for y in range(1, 15):
        k = y - 1 if y <= 7 else 14 - y
        for x in range(21, 21 + k + 1):
            c.set(x, y, mat)
    return c


def divider(w=64, mat='gold', gem='ruby'):
    c = Canvas(w, 8)
    for x in range(2, w - 2):
        c.set(x, 3, mat)
        c.set(x, 4, mat)
    cx = w // 2
    for y in range(8):
        k = 3 - abs(y - 3.5) + 0.5
        for x in range(int(cx - k), int(cx + k)):
            c.set(x, y, gem, 's')
    for ex in (1, w - 3):
        for y in (2, 3, 4, 5):
            c.set(ex, y, mat)
            c.set(ex + 1, y, mat)
    return c


def dotted(w=64, mat='white'):
    c = Canvas(w, 8)
    for x in range(2, w - 2, 5):
        for dx in (0, 1, 2):
            for y in (3, 4):
                c.set(x + dx, y, mat)
    return c


def frame(size=32, mat='gold', inner=None):
    c = Canvas(size, size)
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            edge = x < 4 or y < 4 or x >= size - 4 or y >= size - 4
            if edge:
                c.set(x, y, mat)
            elif inner:
                c.set(x, y, inner, 'f')
    for (x, y) in ((1, 1), (size - 2, 1), (1, size - 2), (size - 2, size - 2)):
        for dx in range(3):
            for dy in range(3):
                c.set(x + (dx if x == 1 else -dx), y + (dy if y == 1 else -dy), mat + '2', 's')
    return c


def panel(w=48, h=48, mat='dark', border='purple'):
    c = Canvas(w, h)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            cx = min(x - 1, w - 2 - x)
            cy = min(y - 1, h - 2 - y)
            if cx + cy < 2:
                continue
            ring = cx < 2 or cy < 2
            c.set(x, y, border if ring else mat, 'b' if ring else 'f')
    return c


def banner(text, mat, ink='cream', w=None):
    glyphs = [font.BOLD[ch] if ch != ' ' else ['....'] * 10 for ch in text]
    tw = sum(len(g[0]) for g in glyphs) + len(glyphs) - 1
    w = w or max(48, tw + 18)
    c = Canvas(w, 18)
    for y in range(2, 16):
        for x in range(5, w - 5):
            c.set(x, y, mat)
    # ribbon tails
    for y in range(4, 18 - 1):
        for x in range(1, 6):
            if not (y in (9, 10) and x < 3) and not (y in (8, 11) and x < 2):
                c.set(x, y, mat + '2', 'f' if x < 5 else 'b')
            c.set(w - 1 - x, y, mat + '2', 'f') if not (y in (9, 10) and x < 3) and not (y in (8, 11) and x < 2) else None
    ox = (w - tw) // 2
    for g in glyphs:
        for gy, r in enumerate(g):
            for gx, ch in enumerate(r):
                if ch == '#':
                    c.set(ox + gx + 1, 4 + gy + 1, mat, '0')
        for gy, r in enumerate(g):
            for gx, ch in enumerate(r):
                if ch == '#':
                    c.set(ox + gx, 4 + gy, ink, '3')
        ox += len(g[0]) + 1
    return c


def plaque(w=96, h=24, mat='wood', edge='gold'):
    c = Canvas(w, h)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            cx = min(x - 1, w - 2 - x)
            cy = min(y - 1, h - 2 - y)
            if cx + cy < 1:
                continue
            c.set(x, y, edge if (cx < 2 or cy < 2) else mat, 'b' if (cx < 2 or cy < 2) else 'v')
    return c


def scroll(w=96, h=28):
    c = Canvas(w, h)
    for y in range(4, h - 4):
        for x in range(8, w - 8):
            c.set(x, y, 'paper', 'v')
    for side in (0, 1):
        for y in range(2, h - 2):
            for x in range(2, 9):
                xx = x if side == 0 else w - 1 - x
                c.set(xx, y, 'paper2', 'h' if side == 0 else 'b')
    return c


def build():
    out = []

    def add(id, name, cat, canvas, grid):
        out.append(dict(id=id, name=name, category=cat, image=canvas.render(), grid=grid))

    for mat, nm, pre in [('cream', '', ''), ('gold', 'Gold ', 'gold_'), ('lime', 'Green ', 'green_'), ('red', 'Red ', 'red_')]:
        a = long_arrow(48, mat)
        add('%sarrow_right' % pre, '%sArrow Right' % nm, 'arrows', a, (2.0, 0.67))
        add('%sarrow_left' % pre, '%sArrow Left' % nm, 'arrows', _flip(a), (2.0, 0.67))
        add('%sarrow_down' % pre, '%sArrow Down' % nm, 'arrows', _rot(a), (0.67, 2.0))
        add('%sarrow_up' % pre, '%sArrow Up' % nm, 'arrows', _flip(_rot(_flip(a))) if False else _rot(_rot(_rot(a))), (0.67, 2.0))
    cv = curved_arrow('cream')
    add('arrow_curve_right', 'Curved Arrow', 'arrows', cv, (1.5, 1.5))
    add('arrow_curve_left', 'Curved Arrow (mirrored)', 'arrows', _flip(cv), (1.5, 1.5))

    add('divider_gold', 'Gold Divider', 'lines', divider(64, 'gold', 'ruby'), (4.0, 0.5))
    add('divider_silver', 'Silver Divider', 'lines', divider(64, 'silver', 'sapphire'), (4.0, 0.5))
    add('divider_wood', 'Wooden Divider', 'lines', divider(64, 'wood', 'emerald'), (4.0, 0.5))
    add('divider_gold_vertical', 'Gold Divider (vertical)', 'lines', _rot(divider(64, 'gold', 'ruby')), (0.5, 4.0))
    add('line_dotted', 'Dotted Line', 'lines', dotted(64, 'white'), (4.0, 0.5))
    add('line_dotted_vertical', 'Dotted Line (vertical)', 'lines', _rot(dotted(64, 'white')), (0.5, 4.0))
    add('line_dotted_gold', 'Gold Dotted Line', 'lines', dotted(64, 'gold'), (4.0, 0.5))

    add('frame_gold', 'Gold Frame', 'frames', frame(32, 'gold'), (3.0, 3.0))
    add('frame_silver', 'Silver Frame', 'frames', frame(32, 'silver'), (3.0, 3.0))
    add('frame_wood', 'Wooden Frame', 'frames', frame(32, 'wood'), (3.0, 3.0))
    add('frame_stone', 'Stone Frame', 'frames', frame(32, 'stone'), (3.0, 3.0))
    add('frame_amethyst', 'Amethyst Frame', 'frames', frame(32, 'amethyst'), (3.0, 3.0))
    add('panel_dark', 'Dark Panel', 'frames', panel(48, 48, 'dark', 'purple'), (4.0, 4.0))
    add('panel_parchment', 'Parchment Panel', 'frames', panel(48, 48, 'paper', 'wood'), (4.0, 4.0))
    add('panel_stone', 'Stone Panel', 'frames', panel(48, 48, 'darkstone', 'stone'), (4.0, 4.0))
    add('panel_night', 'Night Panel', 'frames', panel(48, 48, 'navy', 'sapphire'), (4.0, 4.0))
    add('panel_forest', 'Forest Panel', 'frames', panel(48, 48, 'darkgreen', 'green'), (4.0, 4.0))

    for txt, mat, ink in [('START', 'green', 'cream'), ('GOAL', 'gold', 'dark'), ('BOSS', 'red', 'cream'),
                          ('BONUS', 'purple', 'cream'), ('SECRET', 'dark', 'gold'), ('NEW', 'orange', 'cream'),
                          ('OPTIONAL', 'blue', 'cream'), ('FINISH', 'navy', 'gold'), ('TIER I', 'wood', 'cream'),
                          ('TIER II', 'stone', 'cream'), ('TIER III', 'grey', 'cream'), ('TIER IV', 'gold', 'dark'),
                          ('TIER V', 'teal', 'cream')]:
        cv = banner(txt, mat, ink)
        idn = 'banner_' + txt.lower().replace(' ', '_')
        add(idn, '"%s" Banner' % txt.title(), 'banners', cv, (round(cv.w / 16.0, 2), 1.13))
    for mat, nm in [('red', 'Red'), ('blue', 'Blue'), ('green', 'Green'), ('purple', 'Purple'), ('gold', 'Gold')]:
        cv = banner('', mat, w=64)
        add('banner_blank_' + mat, 'Blank %s Banner' % nm, 'banners', cv, (4.0, 1.13))
    add('plaque_wood', 'Wooden Title Plaque', 'banners', plaque(96, 24, 'wood', 'gold'), (6.0, 1.5))
    add('plaque_stone', 'Stone Title Plaque', 'banners', plaque(96, 24, 'darkstone', 'silver'), (6.0, 1.5))
    add('scroll_header', 'Parchment Scroll Header', 'banners', scroll(96, 28), (6.0, 1.75))
    return out

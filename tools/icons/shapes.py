"""Shape helpers for the icon library: masks, badges with glyphs, recolouring and mirroring."""
from pixelart import Canvas, MATERIALS
import font


def mask_circle(d=14, ox=1, oy=1):
    r = d / 2
    out = set()
    for y in range(d):
        for x in range(d):
            if (x + 0.5 - r) ** 2 + (y + 0.5 - r) ** 2 <= (r - 0.15) ** 2:
                out.add((x + ox, y + oy))
    return out


def mask_rsquare(w=14, h=14, ox=1, oy=1, cut=1):
    out = set()
    for y in range(h):
        for x in range(w):
            cx = min(x, w - 1 - x)
            cy = min(y, h - 1 - y)
            if cx + cy < cut:
                continue
            out.add((x + ox, y + oy))
    return out


def mask_shield(ox=2, oy=1):
    rows = [
        "############",
        "############",
        "############",
        "############",
        "############",
        "############",
        "############",
        "############",
        ".##########.",
        ".##########.",
        "..########..",
        "...######...",
        "....####....",
        ".....##.....",
    ]
    return {(x + ox, y + oy) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch == '#'}


def mask_octagon(ox=1, oy=1, d=14, cut=4):
    out = set()
    for y in range(d):
        for x in range(d):
            cx = min(x, d - 1 - x)
            cy = min(y, d - 1 - y)
            if cx + cy < cut - 1:
                continue
            out.add((x + ox, y + oy))
    return out


def mask_diamond(ox=0, oy=0):
    out = set()
    for y in range(16):
        for x in range(16):
            if abs(x - 7.5) + abs(y - 7.5) <= 7.0:
                out.add((x + ox, y + oy))
    return out


def mask_triangle():
    """An upward warning triangle, 14 wide at the bottom."""
    out = set()
    for y in range(1, 15):
        half = (y - 1) * 0.5 + 0.5
        for x in range(1, 15):
            if abs(x - 7.5) <= half:
                out.add((x, y))
    return out


def fill(c, mask, mat, mode='b'):
    for (x, y) in mask:
        c.set(x, y, mat, mode)
    return c


def stamp(c, rows, ox, oy, mat, mode='3', shadow=None):
    """Draws glyph rows ('#' = ink) at (ox, oy); shadow = (material, tone) one pixel down-right."""
    if shadow:
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch == '#':
                    sx, sy = ox + x + 1, oy + y + 1
                    inside_glyph = (0 <= y + 1 < len(rows) and 0 <= x + 1 < len(r) and rows[y + 1][x + 1] == '#')
                    if not inside_glyph and c.get(sx, sy) is not None:
                        c.set(sx, sy, shadow[0], shadow[1])
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == '#':
                c.set(ox + x, oy + y, mat, mode)
    return c


def center_stamp(c, rows, mat, mode='3', shadow=None, box=(2, 2, 12, 12), dy=0, dx=0):
    gw, gh = len(rows[0]), len(rows)
    bx, by, bw, bh = box
    ox = bx + (bw - gw) // 2 + dx
    oy = by + (bh - gh) // 2 + dy
    return stamp(c, rows, ox, oy, mat, mode, shadow)


def glyph_badge(rows, mat, ink='cream', shape='rsquare', ink_mode='3', dy=0, shadow=True):
    c = Canvas()
    m = {'rsquare': mask_rsquare(), 'circle': mask_circle(), 'shield': mask_shield(),
         'octagon': mask_octagon(), 'diamond': mask_diamond()}[shape]
    fill(c, m, mat, 'b')
    box = (2, 2, 12, 12)
    if shape == 'shield':
        box = (2, 1, 12, 11)
    if shape == 'diamond':
        box = (3, 3, 10, 10)
    center_stamp(c, rows, ink, ink_mode, shadow=(mat, '0') if shadow else None, box=box, dy=dy)
    return c


def parse(rows_text):
    return [r for r in rows_text.strip('\n').split('\n')]


def flip_h(c):
    n = Canvas(c.w, c.h)
    n.outline = c.outline
    for y in range(c.h):
        for x in range(c.w):
            n.cells[y][c.w - 1 - x] = c.cells[y][x]
    return n


def flip_v(c):
    n = Canvas(c.w, c.h)
    n.outline = c.outline
    for y in range(c.h):
        n.cells[c.h - 1 - y] = c.cells[y][:]
    return n


def transpose(c):
    n = Canvas(c.h, c.w)
    n.outline = c.outline
    for y in range(c.h):
        for x in range(c.w):
            n.cells[x][y] = c.cells[y][x]
    return n


def rot_cw(c):
    return flip_h(transpose(c))


def rot_ccw(c):
    return flip_v(transpose(c))

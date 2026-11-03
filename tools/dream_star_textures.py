"""Pixel art for the Dream Star (animated, 16x16 frames) and its pedestal.

Usage: python3 tools/dream_star_textures.py src/main/resources/assets/questsicons <preview dir>
Writes textures/item/dream_star.png (12 frames; the .png.mcmeta next to it is kept by hand) and the three
textures/block/dream_star_pedestal_*.png, plus an enlarged preview sheet into the preview dir.
"""
import colorsys, math, sys
from PIL import Image

OUT, PREV = sys.argv[1], sys.argv[2]
FRAMES = 12

def hsv(h, s, v, a=255):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, max(0, min(1, s)), max(0, min(1, v)))
    return (round(r * 255), round(g * 255), round(b * 255), a)

def mix(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))

# ---------------------------------------------------------------- star shape
CX, CY, R, RI = 7.5, 8.35, 7.15, 3.35
tips = [math.radians(-90 + k * 72) for k in range(5)]
poly = []
for k in range(5):
    a = tips[k]
    poly.append((CX + R * math.cos(a), CY + R * math.sin(a)))
    b = a + math.radians(36)
    poly.append((CX + RI * math.cos(b), CY + RI * math.sin(b)))

def inside(px, py):
    n, c = len(poly), False
    for i in range(n):
        x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
        if (y1 > py) != (y2 > py) and px < (x2 - x1) * (py - y1) / (y2 - y1) + x1:
            c = not c
    return c

mask = [[inside(x + 0.5, y + 0.5) for x in range(16)] for y in range(16)]
def m(x, y):
    return 0 <= x < 16 and 0 <= y < 16 and mask[y][x]
edge = [[m(x, y) and not (m(x - 1, y) and m(x + 1, y) and m(x, y - 1) and m(x, y + 1)) for x in range(16)] for y in range(16)]

def facet(x, y):
    """+1 on the lit half of an arm (light from the upper left), -1 on the shaded half."""
    a = math.atan2(y + 0.5 - CY, x + 0.5 - CX)
    best = min(tips, key=lambda t: abs(math.remainder(a - t, 2 * math.pi)))
    side = math.remainder(a - best, 2 * math.pi)
    lit_dir = math.atan2(-1, -1)
    # the half whose outward normal points toward the light is lit
    normal = best + (math.pi / 2 if side > 0 else -math.pi / 2)
    return 1 if math.cos(normal - lit_dir) > 0 else -1

ring = [[(not m(x, y)) and any(m(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) for x in range(16)] for y in range(16)]

star_frames = []
for f in range(FRAMES):
    t = f / FRAMES
    hue = 288 + 30 * math.sin(t * 2 * math.pi)            # violet -> magenta -> blue shimmer
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi * 2)      # core breathing, twice per loop
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if ring[y][x]:
                px[x, y] = hsv(hue + 15, 0.9, 0.30)
                continue
            if not mask[y][x]:
                continue
            d = math.hypot(x + 0.5 - CX, y + 0.5 - CY) / R
            lit = facet(x, y) > 0
            if lit:
                c = mix(hsv(hue - 30, 0.25, 1.0), hsv(hue - 5, 0.62, 1.0), min(1, d * 1.5))
            else:
                c = mix(hsv(hue + 5, 0.70, 0.85), hsv(hue + 20, 0.95, 0.58), min(1, d * 1.5))
            if d < 0.16:                                  # glowing heart
                c = mix(c, (255, 250, 225, 255), 0.6 + 0.4 * pulse)
            elif d < 0.3:
                c = mix(c, (255, 214, 120, 255), 0.35 + 0.35 * pulse)   # golden halo around it
            px[x, y] = c
    # a twinkle that runs from tip to tip (every other frame)
    if f % 2 == 0:
        k = (f // 2) % 5
        tx = math.floor(CX + (R - 1.4) * math.cos(tips[k]))
        ty = math.floor(CY + (R - 1.4) * math.sin(tips[k]))
        for dx, dy, a in ((0, 0, 1.0), (1, 0, 0.55), (-1, 0, 0.55), (0, 1, 0.55), (0, -1, 0.55)):
            if m(tx + dx, ty + dy):
                px[tx + dx, ty + dy] = mix(px[tx + dx, ty + dy], (255, 255, 255, 255), a)
    star_frames.append(img)

sheet = Image.new("RGBA", (16, 16 * FRAMES))
for i, fr in enumerate(star_frames):
    sheet.paste(fr, (0, 16 * i))
sheet.save(f"{OUT}/textures/item/dream_star.png")

# ---------------------------------------------------------------- pedestal
STONE = [(43, 27, 66, 255), (52, 33, 79, 255), (61, 40, 92, 255), (36, 22, 56, 255)]
GOLD = [(255, 222, 117, 255), (232, 178, 61, 255), (176, 118, 33, 255), (120, 74, 22, 255)]
AME = [(232, 190, 255, 255), (184, 128, 240, 255), (130, 78, 196, 255)]
import random
rnd = random.Random(26_3)

def stone(x, y):
    n = rnd.random()
    return STONE[1] if n < 0.55 else STONE[2] if n < 0.75 else STONE[0] if n < 0.95 else STONE[3]

side = Image.new("RGBA", (16, 16)); p = side.load()
for y in range(16):
    for x in range(16):
        c = stone(x, y)
        if y in (0, 1):                       # top plate rim
            c = GOLD[1] if y == 0 else GOLD[2]
        elif y in (2, 11):                    # capital and foot rings of the column
            c = GOLD[1] if y == 2 else GOLD[2]
        elif 3 <= y <= 10:
            if x in (4, 11):
                c = STONE[3]                  # column edges
            elif x in (7, 8):
                c = STONE[0] if y % 2 else STONE[3]   # fluting groove
            elif x in (5, 10):
                c = STONE[2]
        elif y == 12:
            c = STONE[3]
        elif y == 13:
            c = GOLD[1]                       # base: gold top band
        elif y == 15:
            c = STONE[3]
        p[x, y] = c
side.save(f"{OUT}/textures/block/dream_star_pedestal_side.png")

top = Image.new("RGBA", (16, 16)); p = top.load()
for y in range(16):
    for x in range(16):
        c = stone(x, y)
        if x in (3, 12) and 3 <= y <= 12 or y in (3, 12) and 3 <= x <= 12:
            c = GOLD[1] if (x + y) % 2 else GOLD[0]
        elif x in (4, 11) and 4 <= y <= 11 or y in (4, 11) and 4 <= x <= 11:
            c = GOLD[3]
        p[x, y] = c
# amethyst sparkle inlay in the middle
for (x, y, c) in [(7, 7, 0), (8, 7, 0), (7, 8, 0), (8, 8, 0), (7, 5, 2), (8, 5, 2), (7, 6, 1), (8, 6, 1), (7, 9, 1), (8, 9, 1),
                  (7, 10, 2), (8, 10, 2), (5, 7, 2), (5, 8, 2), (6, 7, 1), (6, 8, 1), (9, 7, 1), (9, 8, 1), (10, 7, 2), (10, 8, 2)]:
    p[x, y] = AME[c]
top.save(f"{OUT}/textures/block/dream_star_pedestal_top.png")

base = Image.new("RGBA", (16, 16)); p = base.load()
for y in range(16):
    for x in range(16):
        c = stone(x, y)
        if x in (2, 13) and 2 <= y <= 13 or y in (2, 13) and 2 <= x <= 13:
            c = GOLD[2]
        p[x, y] = c
base.save(f"{OUT}/textures/block/dream_star_pedestal_base.png")

# ---------------------------------------------------------------- previews
def big(im, s=16):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)
row = Image.new("RGBA", (16 * 16 * 6 + 50, 16 * 16 * 2 + 10), (40, 40, 40, 255))
for i, fr in enumerate(star_frames):
    row.alpha_composite(big(fr), ((i % 6) * 266, (i // 6) * 266))
row.save(f"{PREV}/star_frames.png")
pv = Image.new("RGBA", (16 * 16 * 3 + 20, 16 * 16), (40, 40, 40, 255))
for i, im in enumerate((side, top, base)):
    pv.alpha_composite(big(im), (i * 266, 0))
pv.save(f"{PREV}/pedestal_tex.png")

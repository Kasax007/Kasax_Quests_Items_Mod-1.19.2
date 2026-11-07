"""Pixel-art engine of the Quest Icons generator: hue-shifted colour ramps, ASCII grids with automatic shading and the
1-pixel darker outline. Every texture of the icon library is drawn with it (see manifest.py and generate.py).

A material is a ramp of five tones: 0 outline, 1 shadow, 2 base, 3 light, 4 highlight. Shadows drift towards blue,
highlights towards yellow (hue shifting, like vanilla items). A grid cell holds a material and either an explicit tone
or a shading mode that picks the tone from the shape: light from the top left.
"""
import colorsys

from PIL import Image


def _clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360.0, _clamp(s), _clamp(v))
    return round(r * 255), round(g * 255), round(b * 255)


def _toward(h, target, amount):
    d = ((target - h + 180) % 360) - 180
    return h + max(-amount, min(amount, d))


def ramp(h, s, v, shift=14):
    """Five hue-shifted tones (outline, shadow, base, light, highlight) around the base colour (h, s, v)."""
    return [
        hsv(_toward(h, 255, shift * 2.0), s * 1.0 + 0.18, max(0.14, v * 0.38)),
        hsv(_toward(h, 255, shift), s * 1.0 + 0.07, v * 0.70),
        hsv(h, s, v),
        hsv(_toward(h, 60, shift), s * 0.80, v * 1.12 + 0.06),
        hsv(_toward(h, 60, shift * 1.8), s * 0.48, v * 1.20 + 0.17),
    ]


def hexramp(*colors):
    """A hand-made ramp from hex strings, darkest first (outline, shadow, base, light, highlight)."""
    out = []
    for c in colors:
        c = c.lstrip('#')
        out.append((int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)))
    assert len(out) == 5
    return out


# Every material of the library. Neutral greys carry a little blue so their shadows are not dead grey.
MATERIALS = {
    'gold': hexramp('5a3410', 'b86b14', 'eaa922', 'fcd84a', 'fff7b0'),
    'iron': hexramp('34343f', '7b7d8c', 'b4b8c4', 'dadde6', 'ffffff'),
    'silver': hexramp('30384a', '7a88a3', 'b9c4d8', 'dfe6f2', 'ffffff'),
    'copper': hexramp('4a1f16', '93432b', 'c96b45', 'e89668', 'ffc9a0'),
    'bronze': hexramp('3f2410', '87541f', 'b77d36', 'd9a65a', 'f5d898'),
    'diamond': hexramp('0c3c4a', '1f8a99', '4fd6db', 'a5f5ef', 'f0fffd'),
    'emerald': hexramp('06331d', '0c7a3c', '17b85a', '6fe88d', 'd6ffd8'),
    'ruby': hexramp('3d0718', '8f1232', 'd42a4a', 'f4697a', 'ffc6c8'),
    'sapphire': hexramp('0d1650', '1f3aa8', '3c6ae0', '7ba8f6', 'd2e6ff'),
    'amethyst': hexramp('2c1248', '6a2fa0', 'a160d8', 'cf9cf5', 'f3dcff'),
    'topaz': hexramp('5a2c06', 'b8620c', 'f29a1d', 'ffc75a', 'fff0b8'),
    'wood': hexramp('3a2210', '6e4320', '9c6936', 'c08d52', 'e0b67a'),
    'darkwood': hexramp('241510', '45291a', '654030', '875c40', 'a87d5a'),
    'stone': hexramp('2c2c36', '5b5c68', '8a8b96', 'acadb7', 'cfd0d8'),
    'darkstone': hexramp('17161f', '2e2c3a', '45435a', '615f78', '858399'),
    'paper': hexramp('5e4a2c', 'b49a6c', 'e6d3a4', 'f6ead0', 'fffaf0'),
    'leather': hexramp('3a1c10', '6e3820', '9a5532', 'be7a4c', 'dca277'),
    'red': ramp(358, 0.74, 0.86),
    'darkred': ramp(352, 0.78, 0.58),
    'orange': ramp(26, 0.84, 0.97),
    'yellow': ramp(50, 0.80, 0.98),
    'lime': ramp(92, 0.70, 0.86),
    'green': ramp(130, 0.66, 0.70),
    'darkgreen': ramp(140, 0.62, 0.48),
    'cyan': ramp(186, 0.72, 0.86),
    'blue': ramp(218, 0.74, 0.88),
    'navy': ramp(228, 0.70, 0.55),
    'purple': ramp(275, 0.62, 0.80),
    'magenta': ramp(312, 0.64, 0.90),
    'pink': ramp(338, 0.40, 0.98),
    'brown': ramp(26, 0.58, 0.58),
    'white': hexramp('4f5266', '9fa5bd', 'd9dde9', 'f1f3f8', 'ffffff'),
    'cream': hexramp('5f4d33', 'b8a37e', 'e9dcbc', 'f8f0dc', 'fffcf2'),
    'grey': hexramp('25252f', '4d4e5c', '737584', '9a9cab', 'c1c3cf'),
    'dark': hexramp('100e18', '1f1c2a', '312d41', '474259', '625c78'),
    'skin': hexramp('5a2e1e', 'b06c48', 'e0a07a', 'f2c29e', 'ffe2c8'),
    'bone': hexramp('4e4636', 'a69b7f', 'dcd3b8', 'efe9d6', 'fffcf0'),
    'lava': hexramp('5a0f05', 'c2300a', 'f2701a', 'ffb030', 'fff08a'),
    'fire': hexramp('6a1406', 'd33c0c', 'fb8a1a', 'ffcf3c', 'fff6b0'),
    'water': hexramp('0d2358', '1f4fae', '3a82e4', '6fb4f6', 'c4e6ff'),
    'ice': hexramp('2c5070', '6aa4cc', 'a6d8f2', 'd2f0ff', 'ffffff'),
    'glass': hexramp('3a5868', '8fb8c8', 'c8e6ee', 'e6f6fa', 'ffffff'),
    'redstone': hexramp('3a0404', '8a0808', 'd01212', 'ff4a3a', 'ffa090'),
    'lapis': hexramp('0a1a52', '1a3596', '2f58c8', '5e86e8', 'a8c4ff'),
    'quartz': hexramp('5a4e4a', 'b0a49c', 'e4dcd6', 'f4efeb', 'ffffff'),
    'coal': hexramp('0f0f14', '1f1f27', '34343f', '4b4b58', '6a6a78'),
    'netherite': hexramp('1a1418', '3a2f34', '4d4146', '665a5e', '8a7d80'),
    'slime': ramp(100, 0.62, 0.78),
    'ender': hexramp('0a2a26', '105a52', '1f8a7a', '45c4a6', 'b6f4dc'),
    'sculk': hexramp('051a22', '0a3440', '0f5260', '1f8a96', '6fe0e6'),
    'nether': hexramp('2e0a0e', '5e1418', '8a2a2a', 'b04a42', 'd8786a'),
    'endstone': hexramp('5a5638', 'b2ac74', 'dcd69a', 'efeab8', 'fffbdc'),
    'grass': hexramp('16380e', '2e6a1c', '4e9a2e', '76c444', 'b4ec7a'),
    'dirt': hexramp('3a2414', '6a4426', '8e623a', 'ae8254', 'cfa678'),
    'sand': hexramp('6a5a30', 'c2ac6a', 'e6d498', 'f4e8bc', 'fffae0'),
    'cloud': hexramp('56607e', 'a8b4cc', 'dde4f0', 'f2f5fa', 'ffffff'),
    'honey': hexramp('5a2e04', 'c06e0a', 'f2a51a', 'ffcf4a', 'fff0a0'),
    'mint': ramp(160, 0.50, 0.85),
    'teal': ramp(176, 0.70, 0.62),
    'violet': ramp(258, 0.55, 0.88),
    'rose': ramp(350, 0.55, 0.95),
    'olive': ramp(70, 0.55, 0.62),
    'plum': ramp(300, 0.55, 0.55),
    'sky': ramp(204, 0.50, 0.97),
}
# Aliases: the same colours shaded as a separate part (the shading treats each material as one shape).
for _a, _m in {'gold2': 'gold', 'iron2': 'iron', 'wood2': 'wood', 'stone2': 'stone', 'white2': 'white', 'red2': 'red',
               'dark2': 'dark', 'paper2': 'paper', 'leather2': 'leather', 'green2': 'green', 'blue2': 'blue',
               'grey2': 'grey', 'darkwood2': 'darkwood', 'silver2': 'silver', 'cream2': 'cream', 'yellow2': 'yellow',
               'purple2': 'purple', 'brown2': 'brown', 'copper2': 'copper', 'skin2': 'skin', 'bone2': 'bone',
               'glass2': 'glass', 'orange2': 'orange', 'cyan2': 'cyan', 'lime2': 'lime', 'navy2': 'navy',
               'water2': 'water', 'fire2': 'fire', 'ice2': 'ice', 'cloud2': 'cloud', 'grass2': 'grass',
               'dirt2': 'dirt', 'pink2': 'pink', 'magenta2': 'magenta', 'darkred2': 'darkred', 'sand2': 'sand',
               'amethyst2': 'amethyst', 'diamond2': 'diamond', 'emerald2': 'emerald', 'ruby2': 'ruby',
               'sapphire2': 'sapphire', 'darkstone2': 'darkstone', 'lava2': 'lava', 'teal2': 'teal'}.items():
    MATERIALS[_a] = MATERIALS[_m]

for _k in list(MATERIALS):
    if not _k.endswith('2'):
        MATERIALS.setdefault(_k + '2', MATERIALS[_k])
        MATERIALS.setdefault(_k + '3', MATERIALS[_k])

# Shading modes: b = bevel (light top/left edges, dark bottom/right), s = sphere, v = vertical gradient,
# h = horizontal gradient, f = flat base tone. A digit 0-4 is an explicit tone.
MODES = set('bsvhf')


class Canvas:
    """A W x H grid of (material, tone-or-mode) cells; None = transparent."""

    def __init__(self, w=16, h=16):
        self.w = w
        self.h = h
        self.cells = [[None] * w for _ in range(h)]
        self.outline = True
        self.extra = []   # (x, y, rgba) pixels painted after shading and outline

    def set(self, x, y, mat, mode='b'):
        if 0 <= x < self.w and 0 <= y < self.h:
            assert mat in MATERIALS, mat
            self.cells[y][x] = (mat, str(mode))

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.cells[y][x]
        return None

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.cells[y][x] = None

    def grid(self, text, palette, dx=0, dy=0):
        """Draws an ASCII grid: '.' and ' ' are transparent, other characters map through the palette to
        'material:mode' (mode b/s/v/h/f or an explicit tone 0-4; 'material' alone means bevel). '_' erases."""
        rows = [r for r in text.strip('\n').split('\n')]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in '. ':
                    continue
                if ch == '_':
                    self.clear(x + dx, y + dy)
                    continue
                spec = palette[ch]
                mat, _, mode = spec.partition(':')
                self.set(x + dx, y + dy, mat, mode or 'b')
        return self

    def copy(self):
        c = Canvas(self.w, self.h)
        c.cells = [row[:] for row in self.cells]
        c.outline = self.outline
        c.extra = list(self.extra)
        return c

    def recolor(self, mapping):
        """Swaps materials (e.g. a gold medal into a silver one)."""
        c = self.copy()
        for y in range(c.h):
            for x in range(c.w):
                cell = c.cells[y][x]
                if cell and cell[0] in mapping:
                    c.cells[y][x] = (mapping[cell[0]], cell[1])
        return c

    def shift(self, dx, dy):
        c = Canvas(self.w, self.h)
        c.outline = self.outline
        for y in range(self.h):
            for x in range(self.w):
                if self.cells[y][x] and 0 <= x + dx < self.w and 0 <= y + dy < self.h:
                    c.cells[y + dy][x + dx] = self.cells[y][x]
        return c

    def overlay(self, other, dx=0, dy=0):
        for y in range(other.h):
            for x in range(other.w):
                if other.cells[y][x]:
                    tx, ty = x + dx, y + dy
                    if 0 <= tx < self.w and 0 <= ty < self.h:
                        self.cells[ty][tx] = other.cells[y][x]
        return self

    # ------------------------------------------------------------------------------------------------ shading
    def _components(self, mat):
        seen = set()
        comps = []
        for y in range(self.h):
            for x in range(self.w):
                c = self.cells[y][x]
                if c and c[0] == mat and (x, y) not in seen:
                    stack = [(x, y)]
                    seen.add((x, y))
                    comp = []
                    while stack:
                        px, py = stack.pop()
                        comp.append((px, py))
                        for nx, ny in ((px + 1, py), (px - 1, py), (px, py + 1), (px, py - 1)):
                            n = self.get(nx, ny)
                            if n and n[0] == mat and (nx, ny) not in seen:
                                seen.add((nx, ny))
                                stack.append((nx, ny))
                    comps.append(comp)
        return comps

    def tones(self):
        """The tone (0-4) of every opaque cell."""
        out = [[None] * self.w for _ in range(self.h)]
        mats = {c[0] for row in self.cells for c in row if c}
        for mat in mats:
            for comp in self._components(mat):
                xs = [p[0] for p in comp]
                ys = [p[1] for p in comp]
                x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
                cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
                rx, ry = max(1, (x1 - x0 + 1) / 2), max(1, (y1 - y0 + 1) / 2)
                for (x, y) in comp:
                    m = self.cells[y][x][1]
                    if m.isdigit():
                        out[y][x] = int(m)
                        continue

                    def inside(nx, ny):
                        n = self.get(nx, ny)
                        return n is not None and n[0] == mat

                    up, left = not inside(x, y - 1), not inside(x - 1, y)
                    down, right = not inside(x, y + 1), not inside(x + 1, y)
                    if m == 'f':
                        t = 2
                    elif m == 'b':
                        score = (up + left) - (down + right)
                        t = 2 + max(-1, min(2, score))
                        if t == 4 and not (up and left):
                            t = 3
                    elif m == 's':
                        nx_, ny_ = (x - cx) / rx, (y - cy) / ry
                        lum = -(nx_ * 0.62 + ny_ * 0.78)       # light from the top left
                        d = (nx_ * nx_ + ny_ * ny_) ** 0.5
                        if lum > 0.62 and d < 0.9:
                            t = 4
                        elif lum > 0.18:
                            t = 3
                        elif lum > -0.38:
                            t = 2
                        else:
                            t = 1
                        if (down or right) and t > 1 and lum < 0.2:
                            t = 1
                    elif m == 'v':
                        f = (y - y0) / max(1, (y1 - y0))
                        t = 3 if f < 0.3 else 2 if f < 0.7 else 1
                        if up and t < 4 and f < 0.3:
                            t = 4 if left else 3
                    elif m == 'h':
                        f = (x - x0) / max(1, (x1 - x0))
                        t = 3 if f < 0.3 else 2 if f < 0.7 else 1
                    else:
                        raise ValueError(m)
                    out[y][x] = t
        return out

    def render(self):
        """RGBA image: shaded cells, then the outline (the darkest tone of the touching material), then extras."""
        im = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        tones = self.tones()
        px = im.load()
        for y in range(self.h):
            for x in range(self.w):
                c = self.cells[y][x]
                if c:
                    r, g, b = MATERIALS[c[0]][tones[y][x]]
                    px[x, y] = (r, g, b, 255)
        if self.outline:
            for y in range(self.h):
                for x in range(self.w):
                    if self.cells[y][x]:
                        continue
                    best = None
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        n = self.get(nx, ny)
                        if n:
                            col = MATERIALS[n[0]][0]
                            if best is None or sum(col) < sum(best):
                                best = col
                    if best:
                        px[x, y] = best + (255,)
        for (x, y, rgba) in self.extra:
            px[x, y] = rgba
        return im


def grid(text, palette, w=16, h=16, outline=True):
    c = Canvas(w, h)
    c.outline = outline
    c.grid(text, palette)
    return c


def check_texture(im, name):
    """The art rules every library texture must keep: no pure black, a transparent 1-pixel border for items."""
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and r == g == b == 0:
                raise ValueError(f'{name}: pure black at {x},{y}')

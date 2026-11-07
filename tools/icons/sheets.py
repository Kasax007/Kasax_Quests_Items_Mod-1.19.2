"""Contact sheets of the icon library: every icon at 4x on a light and on a dark background, with its id."""
import os

from PIL import Image, ImageDraw, ImageFont

SCALE = 4
CELL_W = 112
CELL_H = 92


def _font():
    for p in ('/home/user/Archive/fonts/Inter-SemiBold.otf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if os.path.exists(p):
            return ImageFont.truetype(p, 11)
    return ImageFont.load_default()


def _icon_image(icon, assets):
    if icon['existing']:
        im = Image.open(os.path.join(assets, 'textures', 'item', icon['id'] + '.png')).convert('RGBA')
        return im.crop((0, 0, im.width, im.width)).resize((16, 16), Image.NEAREST) if im.width != 16 else im.crop((0, 0, 16, 16))
    return icon['canvas'].render()


def sheet(icons, assets, title, path, cols=10):
    f = _font()
    rows = (len(icons) + cols - 1) // cols
    W = cols * CELL_W
    H = rows * CELL_H + 30
    im = Image.new('RGBA', (W, H), (255, 255, 255, 255))
    d = ImageDraw.Draw(im)
    d.text((8, 8), title, fill=(20, 20, 30), font=f)
    for n, icon in enumerate(icons):
        x = (n % cols) * CELL_W
        y = 30 + (n // cols) * CELL_H
        light = (206, 206, 214, 255)
        dark = (34, 22, 52, 255)
        d.rectangle([x + 2, y, x + CELL_W // 2 - 1, y + 70], fill=light)
        d.rectangle([x + CELL_W // 2, y, x + CELL_W - 3, y + 70], fill=dark)
        t = _icon_image(icon, assets).resize((16 * 3, 16 * 3), Image.NEAREST)
        im.alpha_composite(t, (x + 5, y + 11))
        im.alpha_composite(t, (x + CELL_W // 2 + 3, y + 11))
        name = icon['id']
        d.text((x + 4, y + 73), name[:19], fill=(20, 20, 30), font=f)
    im.convert('RGB').save(path)


def render_all(out, icons, images, assets):
    os.makedirs(out, exist_ok=True)
    import manifest
    for cid, cname, _ in manifest.CATEGORIES:
        sel = [i for i in icons if i['category'] == cid]
        if sel:
            sheet(sel, assets, '%s (%d)' % (cname, len(sel)), os.path.join(out, 'icons_%s.png' % cid))
    big = []
    for cid, cname, _ in manifest.CATEGORIES:
        big += [i for i in icons if i['category'] == cid]
    # one overview: 4x icons only, light background
    cols = 24
    rows = (len(big) + cols - 1) // cols
    ov = Image.new('RGBA', (cols * 72, rows * 72), (208, 208, 216, 255))
    for n, icon in enumerate(big):
        t = _icon_image(icon, assets).resize((64, 64), Image.NEAREST)
        ov.alpha_composite(t, ((n % cols) * 72 + 4, (n // cols) * 72 + 4))
    ov.convert('RGB').save(os.path.join(out, 'overview_light.png'))
    ov2 = Image.new('RGBA', ov.size, (30, 20, 46, 255))
    ov2.alpha_composite(ov.copy().convert('RGBA').point(lambda v: v) if False else Image.new('RGBA', ov.size, (0, 0, 0, 0)))
    for n, icon in enumerate(big):
        t = _icon_image(icon, assets).resize((64, 64), Image.NEAREST)
        ov2.alpha_composite(t, ((n % cols) * 72 + 4, (n // cols) * 72 + 4))
    ov2.convert('RGB').save(os.path.join(out, 'overview_dark.png'))
    # map images
    if images:
        f = _font()
        W = 1100
        x = y = 10
        rowh = 0
        placed = []
        for im in images:
            s = 3 if im['image'].width <= 64 else 2
            w, h = im['image'].width * s, im['image'].height * s
            if x + w + 10 > W:
                x = 10
                y += rowh + 26
                rowh = 0
            placed.append((im, x, y, s))
            x += max(w, 90) + 14
            rowh = max(rowh, h)
        H = y + rowh + 30
        for bg, name in (((210, 210, 218, 255), 'map_images_light.png'), ((28, 18, 42, 255), 'map_images_dark.png')):
            sh = Image.new('RGBA', (W, H), bg)
            d = ImageDraw.Draw(sh)
            for im, px, py, s in placed:
                sh.alpha_composite(im['image'].resize((im['image'].width * s, im['image'].height * s), Image.NEAREST), (px, py))
                d.text((px, py + im['image'].height * s + 4), im['id'], fill=(20, 20, 30) if bg[0] > 100 else (230, 220, 240), font=f)
            sh.convert('RGB').save(os.path.join(out, name))

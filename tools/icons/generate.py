"""Generator of the Quest Icons library (textures, item models, item definitions, language entries, item tags, map
images and the library index the mod reads). Run from the repository root:

  python3 tools/icons/generate.py                     Minecraft 1.21.4 and newer (26.x): items/ + models/item
  python3 tools/icons/generate.py --legacy            before 1.21.4 (1.21.1, 1.20.1): models/item only
  python3 tools/icons/generate.py --tag-folder items  before 1.21 (1.20.1): tags/items/ instead of tags/item/
  python3 tools/icons/generate.py --sheets DIR        also contact sheets (4x, light and dark, with names)

Outputs (under src/main/resources): assets/questsicons/textures/item/icons/<category>/<id>.png,
models/item/<id>.json, items/<id>.json, lang/en_us.json (icon keys rewritten, other keys kept),
assets/questsicons/icon_library.json, textures/gui/map/<id>.png, data/questsicons/tags/item/icons/<category>.json
and data/questsicons/tags/item/icons.json (all icons).
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import manifest  # noqa: E402
import mapimages  # noqa: E402
from pixelart import check_texture  # noqa: E402

NS = 'questsicons'


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            if f.read() == text:
                return
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


def _generated_key(key, assets):
    """Was this item name written by the generator (its item has no model any more)?"""
    iid = key.split('.', 2)[2]
    return '.' not in iid and not os.path.exists(os.path.join(assets, 'models', 'item', iid + '.json'))


def texture_path(icon):
    if icon['existing']:
        return 'item/' + icon['id']
    return 'item/icons/%s/%s' % (icon['category'], icon['id'])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--resources', default='src/main/resources')
    ap.add_argument('--legacy', action='store_true', help='no items/ definitions (Minecraft before 1.21.4)')
    ap.add_argument('--tag-folder', default='item', help="'item' (1.21+) or 'items' (1.20.x)")
    ap.add_argument('--sheets', default='', help='folder for contact sheets')
    a = ap.parse_args()
    res = a.resources
    assets = os.path.join(res, 'assets', NS)
    data = os.path.join(res, 'data', NS)

    icons = manifest.ICONS
    cats = manifest.CATEGORIES
    # Textures.
    for icon in icons:
        if icon['existing']:
            continue
        im = icon['canvas'].render()
        check_texture(im, icon['id'])
        p = os.path.join(assets, 'textures', texture_path(icon) + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
    # Models and item definitions.
    for icon in icons:
        write_json(os.path.join(assets, 'models', 'item', icon['id'] + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': '%s:%s' % (NS, texture_path(icon))}})
        if not a.legacy:
            write_json(os.path.join(assets, 'items', icon['id'] + '.json'),
                       {'model': {'type': 'minecraft:model', 'model': '%s:item/%s' % (NS, icon['id'])}})
    # Files of icons that are no longer in the manifest (only generated ones: their texture is under item/icons/).
    ids = {i['id'] for i in icons}
    tex_root = os.path.join(assets, 'textures', 'item', 'icons')
    for root, _dirs, files in os.walk(tex_root):
        for fn in files:
            if fn.endswith('.png') and fn[:-4] not in ids:
                os.remove(os.path.join(root, fn))
    model_dir = os.path.join(assets, 'models', 'item')
    for fn in os.listdir(model_dir):
        iid = fn[:-5]
        if iid in ids or not fn.endswith('.json'):
            continue
        with open(os.path.join(model_dir, fn), encoding='utf-8') as f:
            if '"%s:item/icons/' % NS not in f.read():
                continue
        os.remove(os.path.join(model_dir, fn))
        item_def = os.path.join(assets, 'items', fn)
        if os.path.exists(item_def):
            os.remove(item_def)
    # Map images.
    images = mapimages.build()
    image_ids = {im['id'] for im in images}
    map_dir = os.path.join(assets, 'textures', 'gui', 'map')
    if os.path.isdir(map_dir):
        for fn in os.listdir(map_dir):
            if fn.endswith('.png') and fn[:-4] not in image_ids:
                os.remove(os.path.join(map_dir, fn))
    for im in images:
        p = os.path.join(assets, 'textures', 'gui', 'map', im['id'] + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        check_texture(im['image'], im['id'])
        im['image'].save(p)
    # Language: rewrite the generated keys, keep everything else in its order.
    lang_path = os.path.join(assets, 'lang', 'en_us.json')
    with open(lang_path, encoding='utf-8') as f:
        lang = json.load(f)
    gen = {}
    for icon in icons:
        gen['item.%s.%s' % (NS, icon['id'])] = icon['name']
    for cid, cname, _tab in cats:
        gen['questsicons.icons.category.' + cid] = cname
        # the name recipe viewers show for the category's item tag (#questsicons:icons/<category>)
        gen['tag.item.%s.icons.%s' % (NS, cid)] = 'Quest Icons: ' + cname
    gen['tag.item.%s.icons' % NS] = 'Quest Icons'
    for tid, tname, _icon in manifest.TABS:
        if tid != 'questitems':
            gen['itemgroup.questsicons.' + tid] = tname
    for im in images:
        gen['questsicons.map_image.' + im['id']] = im['name']
    for cid, cname in mapimages.CATEGORIES:
        gen['questsicons.map_image.category.' + cid] = cname
    out = {}
    for k, v in lang.items():
        if k.startswith('item.%s.' % NS) and k not in gen and _generated_key(k, assets):
            continue   # an icon that was removed from the manifest
        out[k] = gen.pop(k, v)
    out.update(gen)
    write_json(lang_path, out)
    # Item tags: one per category, plus every icon in questsicons:icons.
    tag_dir = os.path.join(data, 'tags', a.tag_folder)
    for cid, _cname, _tab in cats:
        values = ['%s:%s' % (NS, i['id']) for i in icons if i['category'] == cid or cid in i['also']]
        write_json(os.path.join(tag_dir, 'icons', cid + '.json'), {'replace': False, 'values': values})
    write_json(os.path.join(tag_dir, 'icons.json'), {'replace': False, 'values': ['%s:%s' % (NS, i['id']) for i in icons]})
    # The index the mod reads at start-up (registration order = creative-tab order = picker order).
    lib = {
        'tabs': [{'id': t[0], 'icon': t[2]} for t in manifest.TABS],
        'categories': [{'id': c[0], 'tab': c[2], 'icons': [
            {'id': i['id'], 'keywords': i['keywords']} for i in icons if i['category'] == c[0]]} for c in cats],
        'map_images': [{'id': im['id'], 'category': im['category'], 'width': im['image'].width,
                        'height': im['image'].height, 'grid_width': im['grid'][0], 'grid_height': im['grid'][1]}
                       for im in images],
        'map_image_categories': [c[0] for c in mapimages.CATEGORIES],
    }
    write_json(os.path.join(assets, 'icon_library.json'), lib)
    print('%d icons (%d new), %d categories, %d map images' % (len(icons), sum(not i['existing'] for i in icons),
                                                                len(cats), len(images)))
    for cid, cname, _ in cats:
        print('  %-12s %3d' % (cid, sum(i['category'] == cid for i in icons)))
    if a.sheets:
        import sheets
        sheets.render_all(a.sheets, icons, images, assets)


if __name__ == '__main__':
    main()

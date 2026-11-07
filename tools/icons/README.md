# Icon library generator

Every icon of Quest Icons is defined once in `manifest.py` (ids, English names, categories, creative tabs, keywords)
and the art modules it imports (`art_themes.py`, `art_chapters.py`, `art_world.py`; pixel grids drawn with
`pixelart.py`, glyphs in `font.py`, shapes in `shapes.py`). Pictures for the quest map are in `mapimages.py`.

```
python3 tools/icons/generate.py                      # Minecraft 1.21.4+ / 26.x: items/ definitions + models/item
python3 tools/icons/generate.py --legacy             # before 1.21.4 (1.21.1, 1.20.1): models/item only
python3 tools/icons/generate.py --legacy --tag-folder items   # 1.20.x: tags in data/questsicons/tags/items/
python3 tools/icons/generate.py --sheets DIR         # also contact sheets (4x, light and dark backgrounds)
```

It writes, under `src/main/resources`:

| Output | Path |
| --- | --- |
| Textures | `assets/questsicons/textures/item/icons/<category>/<id>.png` (the 32 icons of 1.0–1.2 keep `textures/item/<id>.png`) |
| Item models | `assets/questsicons/models/item/<id>.json` |
| Item definitions (1.21.4+) | `assets/questsicons/items/<id>.json` |
| Names | `assets/questsicons/lang/en_us.json` (icon, category, tab and picture keys rewritten, all other keys kept) |
| Item tags | `data/questsicons/tags/item/icons.json` and `icons/<category>.json` |
| Map pictures | `assets/questsicons/textures/gui/map/<id>.png` |
| Library index | `assets/questsicons/icon_library.json` |

The mod reads `icon_library.json` at start-up (`icons/IconLibrary.java`): it registers one item per icon in this order
(`item/ModItems.java`), builds the creative tabs (`item/ModItemGroup.java`) and fills the editor's icon and picture
pickers. Porting to another Minecraft version therefore needs only the generator run with the right flags plus the
usual API changes in those three classes; adding icons needs no Java change at all.

Rules: never rename or remove a released id (quest files and modpacks use them); new icons go at the end of their
category; textures are 16×16 vanilla style (a hue-shifted ramp of five tones per material, light from the top left,
a one-pixel darker outline drawn automatically, no pure black – `check_texture` refuses it).

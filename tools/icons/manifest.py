"""The icon library of Quest Icons: every icon is defined here once (id, English name, category, keywords and its
art). generate.py turns this into textures, item models, item definitions, the language file, item tags and the
library index the mod reads at start-up (assets/questsicons/icon_library.json).

Rules: ids never change once released (quest files and modpacks refer to them); new icons go at the end of their
category. The 32 icons of 1.0 to 1.2 keep their hand-made textures (existing=True)."""
from pixelart import Canvas, grid
import font
from shapes import (glyph_badge, fill, stamp, center_stamp, mask_circle, mask_rsquare, mask_shield, mask_octagon,
                    mask_diamond, mask_triangle, flip_h, flip_v, rot_cw, rot_ccw, transpose, parse)

# Creative tabs: (id, English name, icon id). The first one is the mod's original tab "Quest Icons".
TABS = [
    ('questitems', 'Quest Icons', 'quest_crown'),
    ('letters_numbers', 'Quest Icons: Letters & Numbers', 'letter_a'),
    ('symbols', 'Quest Icons: Symbols & Arrows', 'status_check'),
    ('ranks', 'Quest Icons: Tiers & Ranks', 'medal_gold'),
    ('themes_craft', 'Quest Icons: Tech, Magic & Nature', 'tech_battery_full'),
    ('themes_adventure', 'Quest Icons: Adventure & Worlds', 'combat_crossed_swords'),
    ('themes_world', 'Quest Icons: Building, Trade & Life', 'econ_coin_gold'),
]

# Categories in picker order: (id, English name, creative tab). Each has the item tag questsicons:icons/<id>.
CATEGORIES = [
    ('classic', 'Classic', 'questitems'),
    ('letters', 'Letters', 'letters_numbers'),
    ('numbers', 'Numbers', 'letters_numbers'),
    ('symbols', 'Math & Punctuation', 'symbols'),
    ('arrows', 'Arrows', 'symbols'),
    ('status', 'Checks & Status', 'symbols'),
    ('ui', 'Interface', 'symbols'),
    ('tiers', 'Tiers', 'ranks'),
    ('ranks', 'Medals & Ranks', 'ranks'),
    ('progress', 'Progress & Stages', 'ranks'),
    ('tech', 'Tech & Energy', 'themes_craft'),
    ('magic', 'Magic', 'themes_craft'),
    ('nature', 'Nature & Farming', 'themes_craft'),
    ('food', 'Food & Cooking', 'themes_craft'),
    ('combat', 'Combat & Bosses', 'themes_adventure'),
    ('exploration', 'Exploration & Travel', 'themes_adventure'),
    ('dimensions', 'Dimensions', 'themes_adventure'),
    ('mining', 'Mining, Ores & Gems', 'themes_world'),
    ('building', 'Building & Decoration', 'themes_world'),
    ('storage', 'Storage & Logistics', 'themes_world'),
    ('economy', 'Economy & Shop', 'themes_world'),
    ('social', 'Social & Team', 'themes_world'),
    ('time', 'Time & Weather', 'themes_world'),
]

ICONS = []      # dicts: id, name, category, keywords, canvas (None for existing textures), extra_tags
_IDS = set()


def add(id, name, category, canvas=None, keywords='', existing=False, also=()):
    assert id not in _IDS, id
    assert any(c[0] == category for c in CATEGORIES), category
    _IDS.add(id)
    if not existing:
        assert canvas is not None, id
    ICONS.append(dict(id=id, name=name, category=category, canvas=canvas, keywords=keywords, existing=existing,
                      also=list(also)))


def g(text, **pal):
    """A grid with a palette given as keyword arguments: g('...', y='gold', r='red:3')."""
    return grid(text, pal)


# Default palette letters for hand-drawn grids (any icon may override them).
P = dict(
    y='gold', i='iron', c='copper', w='wood', d='darkwood', s='stone', S='darkstone', p='paper', l='leather',
    r='red', R='darkred', o='orange', Y='yellow', g='green', G='darkgreen', L='lime', a='cyan', b='blue', B='navy',
    v='purple', m='magenta', k='pink', n='brown', W='white', e='grey', D='dark', q='cream', x='skin', z='bone',
    f='fire', u='water', t='glass', h='diamond', E='emerald', A='amethyst', U='ruby', Z='sapphire', T='topaz',
    j='lava', I='ice', C='cloud', N='netherite', O='coal', Q='quartz', K='redstone', J='lapis',
)


def d(text, **over):
    """A hand-drawn grid with the default palette P (keyword overrides, e.g. W='white:4')."""
    pal = dict(P)
    pal.update(over)
    return grid(text, pal)


# =====================================================================================================================
# Classic: the 32 icons of Quest Icons 1.0 to 1.2 (hand-made textures, ids kept for old quest files)
# =====================================================================================================================
for _id, _name, _also in [
    ('chapter_lock', 'Chapter Lock', ['ui']), ('end_lock', 'End Lock', ['ui']),
    ('quest_backpack', 'Backpack Icon', ['exploration']), ('quest_crown', 'Crown Icon', ['ranks']),
    ('quest_dungeon', 'Dungeon Icon', ['combat']), ('quest_key', 'Key Icon', ['ui']),
    ('quest_portal_one', 'Green Portal Icon', ['dimensions']), ('quest_portal_two', 'Purple Portal Icon', ['dimensions']),
    ('quest_portal_three', 'Blue Portal Icon', ['dimensions']), ('quest_portal_four', 'Yellow Portal Icon', ['dimensions']),
    ('quest_portal_five', 'Grey Portal Icon', ['dimensions']), ('logo_fabric', 'Dreamcraft Logo', []),
    ('icon_compass', 'Compass Icon', ['exploration']), ('icon_sword', 'Sword Icon', ['combat']),
    ('icon_shield', 'Shield Icon', ['combat']), ('icon_pickaxe', 'Pickaxe Icon', ['mining']),
    ('icon_magic', 'Magic Orb Icon', ['magic']), ('icon_gear', 'Gear Icon', ['tech']),
    ('icon_house', 'House Icon', ['building']), ('icon_food', 'Food Icon', ['food']),
    ('icon_map', 'Map Icon', ['exploration']), ('icon_star', 'Star Icon', ['ranks']),
    ('icon_trophy', 'Trophy Icon', ['ranks']), ('icon_skull', 'Skull Icon', ['combat']),
    ('icon_heart', 'Heart Icon', ['social']), ('icon_question', 'Question Icon', ['symbols']),
    ('icon_exclamation', 'Exclamation Icon', ['symbols']), ('icon_raft', 'Raft Icon', ['exploration']),
    ('icon_potion', 'Potion Icon', ['magic']), ('icon_chest', 'Chest Icon', ['storage']),
    ('icon_tree', 'Tree Icon', ['nature']), ('icon_moon', 'Moon Icon', ['time']),
]:
    add(_id, _name, 'classic', existing=True, also=_also)

# =====================================================================================================================
# Letters and numbers: one badge style (blue tile, cream glyph)
# =====================================================================================================================
for ch in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ':
    add('letter_' + ch.lower(), 'Letter %s Icon' % ch, 'letters', glyph_badge(font.BOLD[ch], 'blue'),
        keywords='abc alphabet ' + ch.lower())

for n in list(range(0, 21)) + [25, 30, 32, 50, 64, 99]:
    add('number_%d' % n, 'Number %d Icon' % n, 'numbers', glyph_badge(font.number(n), 'orange'),
        keywords='digit count %d' % n)

# =====================================================================================================================
# Math and punctuation (purple badges)
# =====================================================================================================================
SYMBOL_GLYPHS = [
    ('plus', 'Plus', """
...##...
...##...
...##...
########
########
...##...
...##...
...##..."""),
    ('minus', 'Minus', """
########
########"""),
    ('multiply', 'Multiply', """
##....##
###..###
.######.
..####..
..####..
.######.
###..###
##....##"""),
    ('divide', 'Divide', """
...##...
...##...
........
########
########
........
...##...
...##..."""),
    ('equals', 'Equals', """
########
########
........
........
########
########"""),
    ('not_equal', 'Not Equal', """
.....##.
########
########
...##...
..##....
########
########
.##....."""),
    ('percent', 'Percent', """
###...##
###..###
###.###.
...###..
..###...
.###.###
###..###
##...###"""),
    ('hash', 'Hash', """
.##..##.
.##..##.
########
.##..##.
.##..##.
########
.##..##.
.##..##."""),
    ('less_than', 'Less Than', """
.....##
...####
.####..
###....
.####..
...####
.....##"""),
    ('greater_than', 'Greater Than', """
##.....
####...
..####.
....###
..####.
####...
##....."""),
    ('infinity', 'Infinity', """
.####..####.
##..####..##
##..####..##
.####..####."""),
    ('plus_minus', 'Plus-Minus', """
...##...
...##...
########
########
...##...
...##...
........
########
########"""),
    ('question', 'Question Mark', '?'),
    ('exclamation', 'Exclamation Mark', '!'),
    ('ampersand', 'Ampersand', """
.####...
##..##..
##..##..
.####...
.###..##
##.####.
##..##..
.###.###"""),
    ('at', 'At Sign', """
.######.
##....##
##.####.
##.#..##
##.#####
##......
.#######"""),
    ('dollar', 'Dollar', """
...##...
.######.
##.##...
.######.
...##.##
.######.
...##..."""),
    ('asterisk', 'Asterisk', """
...##...
##.##.##
.######.
..####..
.######.
##.##.##
...##..."""),
    ('slash', 'Slash', """
......##
.....##.
....##..
...##...
..##....
.##.....
##......"""),
    ('dot', 'Dot', """
..##..
.####.
######
######
.####.
..##.."""),
]
for sid, sname, art in SYMBOL_GLYPHS:
    rows = font.BOLD[art] if len(art) == 1 else parse(art)
    add('symbol_' + sid, sname + ' Icon', 'symbols', glyph_badge(rows, 'purple'), keywords='math sign ' + sid)

# =====================================================================================================================
# Arrows
# =====================================================================================================================


def arrow_right(mat):
    c = Canvas()
    for y in range(2, 14):
        k = y - 2 if y <= 7 else 13 - y
        for x in range(8, 8 + k + 1):
            c.set(x, y, mat)
    for y in range(6, 10):
        for x in range(1, 8):
            c.set(x, y, mat)
    return c


_DIAG = """
................
......########..
.......#######..
........######..
.......#######..
......########..
.....#####.###..
....#####...##..
...#####.....#..
..#####.........
.#####..........
.####...........
..##............
................"""


def arrow_diag(mat):
    return grid(_DIAG, {'#': mat})


for mat, nm in [('cream', ''), ('green', 'green_'), ('red', 'red_'), ('sky', 'blue_'), ('yellow', 'yellow_')]:
    label = {'': '', 'green_': 'Green ', 'red_': 'Red ', 'blue_': 'Blue ', 'yellow_': 'Yellow '}[nm]
    r = arrow_right(mat)
    add('arrow_%sright' % nm, '%sArrow Right Icon' % label, 'arrows', r, keywords='direction next')
    add('arrow_%sleft' % nm, '%sArrow Left Icon' % label, 'arrows', flip_h(r), keywords='direction back')
    add('arrow_%sup' % nm, '%sArrow Up Icon' % label, 'arrows', rot_ccw(r), keywords='direction up')
    add('arrow_%sdown' % nm, '%sArrow Down Icon' % label, 'arrows', rot_cw(r), keywords='direction down')
    if nm == '':
        dg = arrow_diag(mat)
        add('arrow_up_right', 'Arrow Up-Right Icon', 'arrows', dg, keywords='diagonal')
        add('arrow_up_left', 'Arrow Up-Left Icon', 'arrows', flip_h(dg), keywords='diagonal')
        add('arrow_down_right', 'Arrow Down-Right Icon', 'arrows', flip_v(dg), keywords='diagonal')
        add('arrow_down_left', 'Arrow Down-Left Icon', 'arrows', flip_h(flip_v(dg)), keywords='diagonal')

add('arrow_double_right', 'Fast Forward Icon', 'arrows', g("""
................
................
..#.......#.....
..##......##....
..###.....###...
..####....####..
..#####...#####.
..######..######
..######..######
..#####...#####.
..####....####..
..###.....###...
..##......##....
..#.......#.....
................""", **{'#': 'cream'}), keywords='skip fast double')
add('arrow_double_left', 'Rewind Icon', 'arrows', flip_h(ICONS[-1]['canvas']), keywords='back rewind double')
add('arrow_left_right', 'Arrow Left-Right Icon', 'arrows', g("""
................
................
................
....#......#....
...##......##...
..###......###..
.##############.
################
################
.##############.
..###......###..
...##......##...
....#......#....
................""", **{'#': 'cream'}), keywords='swap exchange both')
add('arrow_up_down', 'Arrow Up-Down Icon', 'arrows', rot_cw(ICONS[-1]['canvas']), keywords='both vertical')
add('arrow_cycle', 'Cycle Icon', 'arrows', g("""
................
.....######.....
...##########...
..###......###..
..##........##..
.##..........##.
.##..........##.
.##.......######
.##........####.
..##........##..
..###...........
...#########....
.....######.....
................""", **{'#': 'lime'}), keywords='repeat refresh loop again')
add('arrow_split', 'Split Icon', 'arrows', g("""
................
..#####...#####.
..####.....####.
..####.....####.
..#####...#####.
..##.###.###.##.
......#####.....
.......###......
.......###......
.......###......
.......###......
.......###......
.......###......
.......###......
................""", **{'#': 'cream'}), keywords='branch choose fork path')
add('arrow_merge', 'Merge Icon', 'arrows', flip_v(ICONS[-1]['canvas']), keywords='join combine')
add('arrow_download', 'Download Icon', 'arrows', g("""
................
.....######.....
.....######.....
.....######.....
.....######.....
.##############.
..############..
...##########...
....########....
.....######.....
......####......
.##...........#.
.##############.
..############..
................""", **{'#': 'sky'}), keywords='get receive import')
add('arrow_upload', 'Upload Icon', 'arrows', g("""
................
.......##.......
......####......
.....######.....
....########....
...##########...
..############..
.....######.....
.....######.....
.....######.....
.....######.....
.##...........#.
.##############.
..############..
................""", **{'#': 'lime'}), keywords='send export give')

# =====================================================================================================================
# Checks and status
# =====================================================================================================================
CHECK = """
................
................
............###.
...........####.
..........####..
.........####...
.##.....####....
.###...####.....
..###.####......
...#######......
....#####.......
.....###........
................"""
CROSS = """
................
................
..###......###..
..####....####..
...####..####...
....########....
.....######.....
.....######.....
....########....
...####..####...
..####....####..
..###......###..
................"""
add('status_check', 'Check Mark Icon', 'status', grid(CHECK, {'#': 'lime'}), keywords='done yes ok complete tick')
add('status_cross', 'Cross Icon', 'status', grid(CROSS, {'#': 'red'}), keywords='no wrong fail x')


def boxed(inner_rows, ink, box='white'):
    c = Canvas()
    fill(c, mask_rsquare(14, 14, 1, 1, 1), 'dark', 'b')
    fill(c, mask_rsquare(12, 12, 2, 2, 1), box, 'f')
    for y in range(3, 13):
        c.set(2, y, box, '1')
    for x in range(2, 14):
        c.set(x, 2, box, '1')
    if inner_rows:
        center_stamp(c, inner_rows, ink, 'b', box=(3, 3, 10, 10))
    return c


add('status_checkbox_empty', 'Empty Checkbox Icon', 'status', boxed(None, 'lime'), keywords='todo task open')
add('status_checkbox_checked', 'Checked Box Icon', 'status', boxed(parse("""
.......##
......###
.....###.
##..###..
###.##...
.####....
..##....."""), 'green'), keywords='done task complete')
add('status_checkbox_crossed', 'Crossed Box Icon', 'status', boxed(parse("""
##....##
###..###
.######.
..####..
..####..
.######.
###..###
##....##"""), 'red'), keywords='failed task')


def circle_badge(rows, mat, ink='white', ink_mode='4', dy=0):
    c = Canvas()
    fill(c, mask_circle(), mat, 's')
    center_stamp(c, rows, ink, ink_mode, shadow=(mat, '0'), dy=dy)
    return c


add('status_info', 'Info Icon', 'status', circle_badge(parse("""
##
##
..
##
##
##
##
##"""), 'blue'), keywords='information help hint i')
add('status_question', 'Question Badge Icon', 'status', circle_badge(font.BOLD['?'], 'green'), keywords='help unknown')
add('status_exclamation', 'Exclamation Badge Icon', 'status', circle_badge(font.BOLD['!'], 'orange'), keywords='important alert')

_warn = Canvas()
fill(_warn, mask_triangle(), 'yellow', 'b')
stamp(_warn, parse("""
##
##
##
##
..
##"""), 7, 6, 'dark', '2')
add('status_warning', 'Warning Icon', 'status', _warn, keywords='caution danger alert triangle')

_stop = Canvas()
fill(_stop, mask_octagon(), 'red', 'b')
stamp(_stop, ["##########", "##########"], 3, 7, 'white', '4')
add('status_stop', 'Stop Icon', 'status', _stop, keywords='halt error no entry forbidden')

_no = Canvas()
fill(_no, mask_circle(), 'red', 'b')
fill(_no, mask_circle(10, 3, 3), 'white', 'f')
for i in range(10):
    for j in (0, 1):
        _no.set(3 + i + j - 0, 3 + i, 'red', '2') if 3 + i + j < 13 else None
add('status_forbidden', 'Forbidden Icon', 'status', _no, keywords='not allowed no ban')

_plus = circle_badge(parse("""
..##..
..##..
######
######
..##..
..##.."""), 'green')
add('status_add', 'Add Icon', 'status', _plus, keywords='plus new create')
add('status_remove', 'Remove Icon', 'status', circle_badge(["######", "######"], 'red'), keywords='minus delete')
add('status_new', 'New Badge Icon', 'status', d("""
................
................
.......YY.......
......YYYY......
.YYYYYYYYYYYYYY.
..YYYYYYYYYYYY..
...YYYYYYYYYY...
....YYYYYYYY....
....YYYYYYYY....
...YYYYYYYYYY...
...YYYY..YYYY...
..YYY......YYY..
..Y..........Y..
................""", Y='yellow:b'), keywords='new star burst sparkle')
add('status_locked', 'Padlock Icon', 'status', d("""
................
.....eeeeee.....
....ee....ee....
....e......e....
....e......e....
....e......e....
..yyyyyyyyyyyy..
..yyyyyyyyyyyy..
..yyyyyDDyyyyy..
..yyyyyDDyyyyy..
..yyyyyyDyyyyy..
..yyyyyyyyyyyy..
..yyyyyyyyyyyy..
................""", e='iron'), keywords='lock locked closed secure')
add('status_unlocked', 'Open Padlock Icon', 'status', d("""
.......eeeeee...
......ee....ee..
......e......e..
......e......e..
......e.........
......e.........
..yyyyyyyyyyyy..
..yyyyyyyyyyyy..
..yyyyyDDyyyyy..
..yyyyyDDyyyyy..
..yyyyyyDyyyyy..
..yyyyyyyyyyyy..
..yyyyyyyyyyyy..
................""", e='iron'), keywords='unlock open free')
add('status_heart_broken', 'Broken Heart Icon', 'status', d("""
................
................
..rrrr...rrrrr..
.rrrrrr.rrrrrrr.
.rrrrrr..rrrrrr.
.rrrrrrr..rrrrr.
.rrrrrrrr.rrrrr.
..rrrrrr.rrrrr..
...rrrrr..rrr...
....rrrrr.rr....
.....rrrr.r.....
......rrr.......
.......r........
................"""), keywords='death fail lost')


import art_themes  # noqa: E402,F401  (themed icons, appended in order)
import art_chapters  # noqa: E402,F401
import art_world  # noqa: E402,F401

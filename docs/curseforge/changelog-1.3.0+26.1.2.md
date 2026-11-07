## Quest Icons 1.3.0 (Minecraft 26.1.2, Fabric)

**For Minecraft 26.1.2 (26.1.x) with Fabric Loader 0.18.4 or newer and Fabric API. Java 25.** The first Quest Icons
for 26.1: FTB Quests for 26.1 is NeoForge-only, so on Fabric this is the quest book, and `/quests import ftbquests`
brings an FTB Quests book over. Same features and file format as on 26.3.

### New: 470 quest icons
From 32 to 470 icons, all 16×16 in vanilla style, made for every kind of modpack. Thanks for the ideas in the
comments: letters, NPC markers and science / industrial icons are in.
- **Letters A–Z** and **numbers 0–20, 25, 30, 32, 50, 64 and 99** as badges.
- **Math and punctuation:** plus, minus, times, divide, equals, not equal, percent, hash, less / greater than,
  infinity, plus-minus, question and exclamation marks, ampersand, at, dollar, asterisk, slash, dot.
- **Arrows** in five colours and all directions, diagonals, fast forward, rewind, both ways, cycle, split, merge,
  download, upload.
- **Checks and status:** check mark, cross, check boxes, info, question and exclamation badges, warning, stop,
  forbidden, add, remove, "new" burst, padlocks, broken heart.
- **Interface:** home, settings, bookmark, search, visible / hidden, bell, trash, pencil, save, folder, list, power,
  play, pause, link, puzzle piece, pushpin, crosshair, open book, hint.
- **Tiers I–X** as shields from wood to netherite, plain tier shields, **medals**, trophies, stars, rosettes, rank
  chevrons, crowns, a podium, **progress bars**, pie charts, start and finish flags, steps and stage dots.
- **Chapter themes:** tech and energy (batteries, lightning, plug, circuit chip, robot, gears, pipe, tank, solar panel,
  atom, radiation, magnet, rocket, satellite, computer, light bulb, flask, redstone, conveyor, drill), magic, nature
  and farming, food and cooking, combat and bosses, exploration and travel, dimensions (Overworld, Nether, End, planets,
  moon, galaxy, black hole, ocean, Deep Dark, sky islands, clouds, twilight forest, mining world, rift), mining (6
  gems, 12 ingots, 11 ores and more), building, storage and logistics, economy and shops, social and teams (speech
  bubbles, **NPC quest giver and turn-in markers**, team banners) and time and weather.
- The 32 icons you already use keep their ids and look.
- New creative tabs (Letters & Numbers, Symbols & Arrows, Tiers & Ranks, Tech, Magic & Nature, Adventure & Worlds,
  Building, Trade & Life) and item tags for other quest mods: `#questsicons:icons` (all) and one per category, e.g.
  `#questsicons:icons/letters`, `#questsicons:icons/tech`.

### New: 56 pictures for the quest map
Arrows, curved arrows, dividers, dotted lines, frames, panels to group quests, ribbon banners ("START", "BOSS",
"GOAL", "BONUS", "SECRET", "NEW", "OPTIONAL", "FINISH", "TIER I–V" and blank ones), title plaques and a scroll header.

### New: edit everything in game – no SNBT needed
Open the book, click **Edit** (operators) and build your quests with forms and pickers:
- **Quests:** right-click the empty map → *New quest here…*; a form for title, subtitle, description (with a short help
  for colours and translation keys), icon, tasks, rewards, dependencies ("all" or "one of"), shape, size, position and
  every flag (optional, hidden, invisible, lines, toast).
- **Tasks of every type** with their own form: item (pick from all items, your inventory or the item in your hand;
  "any of" lists, tags like `#minecraft:logs`, whole mods `@modid`; count, take away or detect, item data), checkmark,
  kill (mob picker), advancement and criterion, dimension, biome, structure, XP, statistic, observation and location
  (*Use my position*).
- **Rewards of every type:** item, XP, levels, command (placeholders listed), random from a reward table, choice,
  loot table, advancement; team reward, auto claim and "not in Claim all".
- **Icon picker:** the new icon library first, by category and with search, every other item in its own tab.
  Works for quests, chapters, groups, the book, tasks and rewards (click a task or reward icon in the quest window).
- **Map:** drag quests (snap to the grid, toggle with *Snap*), **Shift-drag** from one quest to another to link them,
  click a line to remove it, **Ctrl-click** or **Ctrl-drag** to select several quests and move them together,
  **Copy / Paste** (also Ctrl+C / Ctrl+V), Duplicate, *Move to chapter…*, Delete (asks again), and **Undo** (Ctrl+Z)
  for the last steps.
- **Pictures:** *Add picture here…* opens the picture picker; drag pictures around, edit size, rotation, colour,
  hover text and "only after quest".
- **Chapters and groups:** *+ Chapter* and *+ Group* buttons, drag chapters to reorder them (or into another group),
  forms for chapter (title, subtitle, icon, group, default shape, size of new quests, hidden / invisible) and group.
- **Reward tables** (also the four Loot Crates) and **book settings** have their own screens.
- Every field is checked before saving: a typo in a mob, item, biome, advancement or loot table turns the field red
  instead of saving a broken quest. Save writes the same SNBT files as before and every player sees the change at
  once; Cancel drops it. The server checks operator rights for every change.

### Also changed
- The Dream Star recipe for Dreamcraft takes the **Tamacraft Heart** (the renamed Tamagotchi mod; the old Tamagotchi
  Heart still works).
- Deleting a reward table also deletes its file.

## Quest Icons 1.2.0 (Minecraft 26.3)

### New: bring your FTB Quests chapters to 26.3
FTB Quests stops at Minecraft 26.1.2. One command turns an FTB Quests book into a Quest Icons book, so a pack can move
on to 26.2 or 26.3 and keep its quests.
- Reads every FTB Quests layout: 1.20 and older (texts in the chapter files), 1.21 (texts in `lang/en_us.snbt` or
  `lang/en_us/`) and 26.1 (JSON5 files).
- Imports chapter groups, chapters (title, subtitle, icon, order, default shape, hidden chapters, pictures on the map),
  quests (position, size, shape, title, subtitle, description with colour codes, dependencies, "one of" dependencies,
  optional and hidden quests) and reward tables.
- Tasks: item (tags and FTB item filters too, submit or detect, item data), checkmark, kill, advancement, dimension,
  biome, structure, XP, statistic, observation and location. Tasks Quest Icons does not have (fluid, energy, game
  stage, KubeJS) become checkmark tasks with a clear title.
- Rewards: item, XP, levels, command, random, loot, choice, "all of table" and advancement.
- FTB's quest ids are kept: importing again updates the book instead of doubling it.
- Old item data is upgraded the way Minecraft upgrades old worlds (enchantments, potions, names, renamed items).
- A report lists everything that is not exactly the same, and every id that does not exist in your game (a missing mod).

### How to import from FTB Quests
1. Copy the pack's `config/ftbquests/quests` folder into your 26.3 instance or server, at the same place
   (`config/ftbquests/quests`).
2. Run `/quests import ftbquests` (operators). This is only a preview: you get a summary in chat and the full list in
   `config/questsicons/ftbquests-import-report.txt`.
3. Run `/quests import ftbquests run`. Your current quest files are backed up to `config/questsicons/backups/`, the book
   is replaced and every player gets it right away.

- `/quests import ftbquests merge` keeps your own chapters and adds the FTB ones.
- Another folder: `/quests import ftbquests run <folder>` (an instance or `config` folder works too).
- Quest progress is not imported: players start the book fresh.

### Also new
- Optional tasks, a random bonus amount for item rewards, item tasks that accept any of several items or every item of
  a mod (`@modid`), "fuzzy" item data matching, map pictures that appear after a quest, the quest shape "none" (only
  the icon), hidden chapters, hiding the lines to the next quests, and command rewards with permission level 3 or 4 and
  the placeholders `{chapter}`, `{team}` and `{member_count}`.
- Quest texts understand `&#RRGGBB` colours and translation keys inside a line (`{mypack.text}`).
- Very big quest books (thousands of quests) now reach the players on a server.

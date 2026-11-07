## Quest Icons 1.3.0 (Minecraft 1.21.1, NeoForge)

**For Minecraft 1.21.1 with NeoForge 21.1 (21.1.0 or newer). Java 21. No other mod needed.** Install it on the server
and on every client.

This is the first Quest Icons for NeoForge, with the full quest book: everything of Quest Icons 1.0 to 1.3 (made for
Minecraft 26.x) now also runs on 1.21.1.

### A complete quest book
- Chapters and chapter groups, a quest map with 10 quest shapes, dependency lines and pictures, tasks, rewards, teams,
  "Claim all", toasts and pinned quests. Open it with **J**, the Quest Book item, the button in the inventory or
  `/quests open`.
- 11 task types (item, checkmark, kill, advancement, dimension, biome, structure, XP, statistic, observation,
  location) and 8 reward types (item, XP, levels, command, random, choice, loot table, advancement).
- Four Loot Crates filled from the book's reward tables, the Mystery Reward, and the Dream Star trophy with its
  pedestal.
- Quest files in `config/questsicons/quests` (SNBT, laid out like FTB Quests).

### New in 1.3: 470 quest icons
From 32 to 470 icons, all 16×16 in vanilla style: letters A–Z, numbers, math and punctuation, arrows, checks and
status badges, interface symbols, tiers I–X, medals, trophies, progress bars, and chapter themes (tech, magic, nature,
food, combat, exploration, dimensions, mining, building, storage, economy, social, time and weather). New creative tabs
and item tags for other quest mods: `#questsicons:icons` (all) and one per category, e.g. `#questsicons:icons/letters`.

### New in 1.3: 56 pictures for the quest map
Arrows, dividers, frames, panels, ribbon banners ("START", "BOSS", "GOAL", "TIER I–V" ...), title plaques and a scroll
header.

### New in 1.3: edit everything in game – no SNBT needed
Click **Edit** in the book (operators) and build quests with forms and pickers: quests, every task and reward type,
the icon picker (library first, then every item), dragging and linking on the map, multi-select, copy and paste, undo,
pictures, chapters and groups, reward tables (also the Loot Crates) and book settings. Every field is checked before
saving; the server checks operator rights for every change.

### Import from FTB Quests
`/quests import ftbquests` turns an FTB Quests book (1.20, 1.21 or 26.1 layout) into a Quest Icons book: preview first,
then `run` (backs up your quest files) or `merge`. A report lists everything that is not exactly the same.

### Notes for NeoForge 1.21.1
- JEI (and other recipe viewers) list all icons and items; search `@questsicons`.
- Same features and file format as on 26.3: quest files can be moved between the versions.
- A dropped Dream Star is protected the 1.21.1 way (it floats on lava and cannot be destroyed except by the void).
- Big quest books and big edits are sent in several packets, so imported books with thousands of quests reach every
  player.
- The Dream Star recipe for Dreamcraft needs Tech Reborn, Mythic Upgrades, Wizards, Paladins & Priests, Beach Craft and
  Tamacraft (or Tamagotchi); without them the vanilla recipe is used.

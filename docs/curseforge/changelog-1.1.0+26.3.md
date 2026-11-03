## Quest Icons 1.1.0 (Minecraft 26.3)

### New: the Dream Star
- **Dream Star:** the endgame trophy of Dreamcraft, like the ATM Star of All The Mods packs. "Proof that you finished
  Dreamcraft." It shines, cannot burn in fire or lava, survives explosions, never despawns when dropped and unlocks the
  challenge "Living the Dream".
- **Recipe in Dreamcraft:** key items from the whole pack: 2 Ametrine Ingots, a Quest Crown, a netherite wizard or holy
  staff, a Nether Star, a Lapotronic Energy Orb, a Tamagotchi Heart, a Dragon Egg or Dragon Head and the Heart of the
  Tide. This recipe only exists when all of these mods are installed.
- **Recipe everywhere else:** 2 Totems of Undying, a Dragon Egg or Dragon Head, 2 Blocks of Netherite, a Nether Star,
  2 Enchanted Golden Apples and a Heavy Core.
- **Dream Star Pedestal:** right-click it with the star to put it on display: the star floats, turns slowly, sparkles and
  lights up the room. Take it back with an empty hand. Breaking the pedestal drops the star.
- The Quest Book tooltip names the key that opens the book, and a team invite in chat can be clicked to join.

### Fixed
- **Escape in the quest book** closes the open quest, reward picker or menu first, not the whole book (26.3 numbers the
  keys differently).
- While you pick a "choose one" reward, clicks no longer reach the buttons behind the picker, and the picker is wide
  enough for its title. A pick that does not fit the reward any more no longer uses it up.
- Buttons scrolled out of the quest panel no longer react to clicks.
- **Teams:** after joining a team and restarting the server, a player could land back in an empty team of their own.
  Worlds with this problem are repaired when they load.
- A typo in a quest file (an item tag, mob, biome, structure, statistic or advancement id) no longer causes errors every
  tick. A task that fails is switched off with one message in the log until `/quests reload`.
- Hand-written quests without an id no longer overwrite each other.
- XP tasks that take XP points use the XP you have right now (levels spent on enchanting no longer count).
- Chapters show 99 % instead of a tick when one quest is still missing, and only the quest that finishes a chapter
  announces it.
- The Mystery Reward no longer hands out creative-only blocks (bedrock, spawners, end portal frames, ...) or items of
  experiments that are switched off.
- Loot Crates hand out big rewards in normal stacks.
- A reward or Loot Crate entry naming an item that does not exist (a typo or a removed mod) no longer hands out a
  Barrier block. The crate stays closed and the log names the broken entry.

# MysticChest

**Mystic chests like on FunTime.** Players buy chests or find them in the world and open them with a roulette, mystery cards or a full loot chest. Admins build the reward pool **from their own inventory** with one command.

**Minecraft 1.12.2 → 1.21.x · Spigot / Paper · Java 8+ · English and Russian**

---

## Why MysticChest

- **Fill loot in seconds.** Put the items you want in your inventory and run `/mystic loot add poor 25`. Do it again for more. Tune weights, chances and commands in a built-in GUI editor.
- **Four ways to open a chest**, set per tier:
  - 🎰 **Roulette**: a CS:GO-style scroll or a single flip
  - 📦 **Full chest**: a chest full of loot, take what you like
  - ⚡ **Instant**: straight to the inventory
  - 🃏 **Pick**: mystery cards, choose your prize
- **Four ways to spawn chests**, several profiles at once:
  - random place in the world, next to a random player, fixed points, or an **airdrop** falling from the sky
  - every N minutes, at fixed clock times, or when enough players are online
  - exact coordinates, a rough area, or just "about 150 blocks north-west"
- **Cooldowns for everything**: opening, buying, claiming, spawning. Daily limits, per-player win limits, a pity system that guarantees a rare reward after N dry openings.
- **Command rewards**: kits, ranks, anything, with a chance and "run as player / console".
- **Works with your economy**: auto-detects **ExcellentEconomy, Vault / VaultUnlocked, PlayerPoints** (and a CoinsEngine preset). Each tier can use its own, for example gold from Vault and donate tokens from PlayerPoints.
- **PlaceholderAPI** support for cooldowns, daily opens and pity.
- **Two languages** (EN / RU, or per player automatically) and your own translations.
- **Configs that explain themselves**: every option has a tutorial comment, old files upgrade automatically, mistakes produce readable warnings.
- **Light on the server**: no tasks while idle, background atomic saves, no memory leaks.

## Quick start

1. Put the jar in `plugins/` and start the server.
2. `/mystic give <you> poor`, right click to open or place it.
3. `/mystic loot add poor` fills the reward pool from your inventory.
4. `/mystic shop` lets players buy chests (needs an economy plugin).

Default tiers: **Poor, Solid, Rich, Elite, Crusher (donate), Admin, Aristocratic**. Add your own in `tiers.yml`.

## Commands

| Command | Description |
|---|---|
| `/mystic shop` | buy chests, right click to preview rewards |
| `/mystic preview <tier>` | rewards and chances |
| `/mystic give <player> <tier> [n]` | give chests |
| `/mystic spawn <tier> [here\|profile]` | place a chest / run a spawn profile |
| `/mystic loot add\|addcmd\|cmd\|weight\|remove\|list\|edit\|clear` | manage rewards |
| `/mystic point add\|remove\|list` | fixed spawn points |
| `/mystic economy` | see which economy plugins were detected |
| `/mystic reload`, `/mystic perf` | reload, live performance numbers |

## Placeholders

`%mysticchest_cooldown_<tier>%`, `%mysticchest_cooldown_formatted_<tier>%`, `%mysticchest_opens_today%`, `%mysticchest_pity_<tier>%`, `%mysticchest_active_chests%`

## Compatibility

Tested on **Paper 1.21.11** and **Paper 1.12.2**, with VaultUnlocked 2.20.3, ExcellentEconomy 2.8.0, PlayerPoints 3.3.3 and PlaceholderAPI 2.11.6. Versions in between should work but were not run. The CoinsEngine preset is not verified: check its names in `config.yml`. Items that don't exist on an older server are skipped silently.

## Links

- Full documentation: see the `docs` folder in the source repository
- Found a bug? Open an issue and attach the console output (turn on `debug: true` in `config.yml` for more detail).

## License

Free to use on your servers. Modification, commercial distribution and redistribution without crediting the author are not allowed. See the `LICENSE` file in the source repository.

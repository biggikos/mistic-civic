# MysticChest

[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2%20→%201.21.x-brightgreen)](#compatibility)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](#build)
[![Languages](https://img.shields.io/badge/lang-EN%20%7C%20RU-blue)](docs/documentation.md#languages)

Mystic chests in the style of **FunTime** for Spigot / Paper servers. Players buy chests or find them in the world, open them with a roulette, mystery cards or a full loot chest, and admins fill the reward pool **straight from their inventory**.

One jar, **Minecraft 1.12.2 → 1.21.x**, English and Russian.

## Features

- **Seven tiers** out of the box: Poor, Solid, Rich, Elite, Crusher (donate), Admin, Aristocratic. Add as many as you want.
- **Four ways to open** a chest, per tier: `ROULETTE` (single flip or CS:GO-style scroll), `FULL_CHEST`, `INSTANT`, `PICK` (mystery cards).
- **Structures** around chests: nine shapes (pyramid, temple, obelisk, stone ring, gate, spiral tower, arena, crystals, rune circle), each rolled with its own size and ruin level, in the biome's theme or a surprise one. They rise block by block, collapse afterwards and **restore the terrain**. Save **your own** with a wand and manage them in a GUI.
- **Animated, clickable announcements**: a typewriter effect, a shimmering title and buttons (track with an action-bar arrow, copy coordinates, compass, mute), configurable per event with channels, tier and permission filters.
- **Announcements that say everything**: type of the chest, rarest loot inside, coordinates, structure, time left, a sound for everyone, a **boss bar** and a countdown hologram.
- **Hunt, guards, capture, atmosphere**: be first to a chest for a bonus, fight guard mobs and bosses of your own design, capture contested chests in a duel, and feel a local midnight storm around an elite chest.
- **Leaderboard and achievements**: top lists, hologram boards, period rewards and configurable achievements.
- **RANDOM open mode**: roulette, full chest, mystery cards or instant, rolled by weight.
- **Four spawn modes** with several profiles at once: random in the world, next to a random player, fixed points, or an **airdrop** that falls from the sky. Triggers: every N minutes, at clock times, or when enough players are online.
- **Cooldowns and limits** for opening, buying, claiming and spawning. Daily limits, per-player win limits, a pity system for rare rewards, bypass permissions.
- **Edit loot in game.** `/mystic loot add <tier>` moves your whole inventory into the reward pool. A GUI editor changes weights, chances and commands; items can be dropped onto the window.
- **Effects**: particle aura around standing chests (ring, light pillar, spiral) in random or rainbow colours, a **cross of particle lines leading to the chest** that flashes when you walk through it, random-coloured fireworks, titles, sounds, optional potions/lightning, and a **chest compass**.
- **Command rewards** with chance and run-as-player/console, announcements for rare drops.
- **Economy**: auto-detects ExcellentEconomy, CoinsEngine, Vault/VaultUnlocked and PlayerPoints. A tier can use its own, e.g. gold from Vault and tokens from PlayerPoints.
- **PlaceholderAPI** placeholders for cooldowns, daily opens and pity.
- **Config with tutorials** (`##` comments), automatic upgrade of old files, validation with readable errors.
- **Light on the server**: zero Bukkit tasks while idle, file writes in the background and atomic, no memory growth after thousands of GUI openings.

## Quick start

1. Drop `MysticChest-x.y.z.jar` into `plugins/` and start the server.
2. Optional: install Vault (+ an economy plugin), ExcellentEconomy, PlayerPoints or PlaceholderAPI.
3. Give yourself a chest and open it:
   ```
   /mystic give <you> poor
   ```
   Right click in the air to open it, right click on a block to place it in the world.
4. Fill a tier with your own items: put them in your inventory, then
   ```
   /mystic loot add poor 25
   ```
   or open the editor with `/mystic loot edit poor`.
5. Players buy chests in `/mystic shop`.

Everything is configurable, see [`config.yml`](src/main/resources/config.yml) (every option is explained inside) and the [documentation](docs/documentation.md).

## Commands

| Command | Who | What |
|---|---|---|
| `/mystic shop` | everyone | buy chests (right click a chest to preview its rewards) |
| `/mystic preview <tier>` | everyone | rewards and chances |
| `/mystic list` | everyone | all tiers |
| `/mystic give <player> <tier> [n]` | admin | give chest items |
| `/mystic spawn <tier> [here\|profile]` | admin | place a chest or run a spawn profile now |
| `/mystic loot add\|addcmd\|cmd\|weight\|remove\|list\|edit\|clear <tier> …` | `admin.loot` | manage rewards |
| `/mystic point add\|remove\|list` | admin | fixed spawn points |
| `/mystic economy [currency]` | admin | what economy plugins were found, your balance |
| `/mystic perf` | admin | live performance numbers |
| `/mystic reload` | admin | reload all files |

Full list with permissions: [docs/documentation.md#commands-and-permissions](docs/documentation.md#commands-and-permissions).

## Compatibility

| | |
|---|---|
| Server | Spigot / Paper (and forks) **1.12.2 – 1.21.x** |
| Java | 8 or newer (the jar targets Java 8 bytecode) |
| Economy | Vault / VaultUnlocked, ExcellentEconomy, PlayerPoints, CoinsEngine (see notes) |
| Optional | PlaceholderAPI |

Tested on live servers: Paper 1.21.11 (Java 25) and Paper 1.12.2 (Java 8); with VaultUnlocked 2.20.3, ExcellentEconomy 2.8.0 (NightCore 2.16.6), PlayerPoints 3.3.3 and PlaceholderAPI 2.11.6. Versions in between are expected to work (the code avoids new APIs) but were not run. The CoinsEngine preset in `config.yml` is **not verified**; check its placeholder and command names for your version.

Items that do not exist on an old server (for example netherite on 1.12) are skipped silently when rewards are loaded.

## Build

```bash
gradle build        # runs the tests and creates build/libs/MysticChest-<version>.jar
```

Requires a JDK; compiles to Java 8 bytecode against Spigot API 1.16.5. [XSeries](https://github.com/CryptoMorin/XSeries) is shaded and relocated.

## Documentation

- [Full documentation](docs/documentation.md): configuration, modes, spawn profiles, cooldowns, loot, economy, placeholders, troubleshooting
- [Modrinth page text](docs/MODRINTH.md)

## License

Custom license, see [LICENSE](LICENSE). In short: free to use on your servers, **no modification, no commercial distribution, and any distribution must credit the author**. Editing the generated config, language and loot files is of course fine.

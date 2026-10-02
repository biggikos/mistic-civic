# MysticChest documentation

- [Concepts](#concepts)
- [Files](#files)
- [Languages](#languages)
- [Tiers](#tiers)
- [How chests are opened](#how-chests-are-opened)
- [Spawning chests](#spawning-chests)
- [Announcements, boss bar, timers](#announcements-boss-bar-timers)
- [Structures](#structures)
- [Events: beacon killer, death chest](#events-beacon-killer-death-chest)
- [Hunt, guards, atmosphere, capture](#hunt-guards-atmosphere-capture)
- [Leaderboard, stats, achievements](#leaderboard-stats-achievements)
- [Cooldowns, limits, pity](#cooldowns-limits-pity)
- [Rewards (loot)](#rewards-loot)
- [Economy](#economy)
- [Effects and announcements](#effects-and-announcements)
- [Commands and permissions](#commands-and-permissions)
- [PlaceholderAPI](#placeholderapi)
- [Performance](#performance)
- [Troubleshooting](#troubleshooting)

## Concepts

- A **tier** is a kind of chest (Poor, Rich, …) with a price, a name and a reward pool.
- A **chest item** is what a player holds. Right click uses it according to `use.click-air` / `use.click-block`.
- A **world chest** stands in the world until somebody opens it or its TTL runs out. Players place them, or **spawn profiles** create them.
- A **reward** is an item and/or commands with a weight. The chance of a reward is its weight divided by the total weight of the tier.

## Files

```
plugins/MysticChest/
  config.yml        global settings, every option is explained with ## comments
  tiers.yml         per-tier price, names, open mode, cooldown/limit overrides, pity
  loot/<tier>.yml   rewards of a tier (rewritten by /mystic loot and the editor)
  lang/en.yml       texts (en, ru shipped; add your own lang/<code>.yml)
  lang/ru.yml
  data/players.yml  cooldowns, daily counters, pity, per-player wins   (managed)
  data/active.yml   chests currently standing in the world             (managed)
  data/points.yml   fixed spawn points                                   (managed)
```

In the YAML files `## text` is an explanation and `# key: value` is an example you can uncomment. After editing run `/mystic reload`.

**Updating the plugin.** When a new version adds config sections, they are appended to your `config.yml` as text (your comments and values stay). A fresh template is written to `config.yml.new` so you can see new options inside existing sections. Language files are never overwritten: a missing key falls back to the built-in English text; delete a lang file to get the new version.

A wrong value never crashes the plugin: you get a console warning with the key path and the default is used.

## Languages

```yaml
language: en     # en | ru | auto
```

- `en`, `ru`: fixed language for everyone.
- `auto`: each player sees their client language, falling back to English.
- Any other code works once `lang/<code>.yml` exists (copy `en.yml` and translate).

Tier names can be per language:

```yaml
name: {en: "&6Rich chest", ru: "&6Богатый сундук"}
```

Colors: `&a`, `&l`, … and `&#RRGGBB` on 1.16+. Inventory titles are cut to 32 characters on servers older than 1.14.

## Tiers

`tiers.yml`; the key (`poor`, `rich`, …) is the id used in commands.

| Key | Meaning |
|---|---|
| `name` | display name (string or one per language) |
| `icon` | shop item and world block (default `CHEST`; a non-block material falls back to a chest block) |
| `price`, `currency` | cost, see [Economy](#economy) |
| `purchasable` | `false` = only given or spawned, never sold |
| `rolls` | rewards per opening: `5` or `{min: 3, max: 5}` |
| `open-mode` | `ROULETTE`, `FULL_CHEST`, `INSTANT`, `PICK` (default: `default-open-mode`) |
| `ttl-seconds` | how long a world chest of this tier waits |
| `cooldowns` | `{open-seconds, buy-seconds, claim-seconds}` |
| `limits` | `{max-opens-per-day, max-purchases-per-day}` |
| `pity` | `{after: 15}` |
| `hologram` | text above the world chest (`{tier}` = name) |

Anything omitted falls back to `config.yml`.

## How chests are opened

**SHARED (the default, like the mystic on FunTime).** When a world chest wakes up and someone opens it, it turns into a real chest full of loot. The chest **stays**: everybody can open it and grab what is left, all players see the same inventory live. It disappears when the last item is taken or when its time is up. The first opener gets the hunt bonus straight into the inventory and runs the command rewards. Cooldowns and daily limits count once per player per chest. A chest *item* opened in the hand opens as `FULL_CHEST`. Set `default-open-mode` (or `open-mode` on a tier) to another mode to go back to personal openings.

**Structures can fix the mode**: `/mystic structure set <name> mode VOLCANO` (or `mode: VOLCANO` in `structures.yml`, `TIER` = no override). The built-in `volcano` erupts its loot by default: every reward is launched out of the chest as items for everyone to catch.


`default-open-mode` and the tier's `open-mode`:

| Mode | Behaviour |
|---|---|
| `ROULETTE` | animated spin. `roulette.style: SCROLL` slides a belt of items under a pointer, `SINGLE` flips the middle slot. A tier with several rolls spins once per reward. Closing the window early hands out the remaining prizes immediately; nothing is ever lost. |
| `FULL_CHEST` | a chest GUI (`full-chest.rows`, 1–6) with all rewards. The player takes what they like; the rest goes to the inventory on close. |
| `INSTANT` | rewards go straight to the inventory, no window. |
| `RANDOM` | a random one of the four above on every opening, weighted by `random-mode.weights`. With `reveal-at-spawn: true` the way is rolled when the chest appears and announced ("Type: mystery cards"). `roulette.style: RANDOM` also flips SINGLE/SCROLL per spin. |
| `PICK` | `pick.cards` face-down cards, the player picks `pick.picks`. Afterwards the others are shown dimmed. If the player idles for 60 s, the picks are made automatically. |

What a click does:

```yaml
use:
  click-air: OPEN         # OPEN | NONE
  click-block: PLACE      # PLACE (put the chest in the world) | OPEN | NONE
```

"Classic FunTime": `click-air: NONE`, `click-block: PLACE`.

A world chest can only be opened by the first player to click it. With `cooldowns.claim-seconds` a chest placed by a player is reserved for them for that long. Chests cannot be broken, blown up or pushed by pistons (each protection can be switched off under `protection:`). `respect-regions: true` honours WorldGuard-style plugins when placing.

## Spawning chests

`spawn.profiles.<name>` in `config.yml`; any number run side by side. Disable one with `enabled: false`.

```yaml
spawn:
  profiles:
    main:
      enabled: true
      mode: RANDOM_WORLD
      trigger: {type: INTERVAL, minutes: 30}
      min-players: 1
      worlds: [world]
      tier-weights: {poor: 50, solid: 30, rich: 15, elite: 5}
      announce: EXACT
      max-active: 3
```

**Modes**

| Mode | Where the chest appears |
|---|---|
| `RANDOM_WORLD` | random surface point around the world spawn (`radius`, `min-distance-from-spawn`, `avoid-ground`); stays inside the world border |
| `NEAR_PLAYER` | near a random online player (`min-distance`..`max-distance`); `notify-player` tells them privately |
| `FIXED_POINTS` | one of the points saved with `/mystic point add <name>` (optionally limited by `names`) |
| `AIRDROP` | falls from `height` blocks over `fall-seconds`; lands at `airdrop.location` (`RANDOM_WORLD` or `NEAR_PLAYER`) |

**Triggers**

| Trigger | Fields |
|---|---|
| `INTERVAL` | `minutes` |
| `TIMES` | `times: ["12:00", "20:00"]` (server clock) |
| `ONLINE_THRESHOLD` | `players`, `cooldown-minutes`: fires when that many players are online, at most once per cooldown (checked every 30 s) |

**Announce**: `EXACT` (coordinates), `REGION` (rounded to 100 blocks), `HINT` (distance and direction from each player), `NONE`.

Chunks with chests (and structures) are kept loaded while the chest exists (Paper/Spigot 1.13+; on older servers the unload is vetoed). After a crash the next start removes leftover chest blocks and holograms.

Force a profile now: `/mystic spawn <tier> <profile>`; place at your feet: `/mystic spawn <tier> here`.

## Announcements, boss bar, timers

When a chest appears, every recipient (see `announce.on-spawn`: type, radius, world-only) gets, in their own language:

```
✦ MYSTIC CHEST APPEARED! Rich chest
Type: mystery cards | Wakes up in: 4m
Coordinates: 2000 -59 2000 (world)
Place: Ring of standing stones
It disappears in 4m 59s. Use /mystic compass to find it!
```

**Animated and clickable.** Multi-line announcements appear line by line like a typewriter, each with a rising tick; the title shimmers through colours; a row of buttons ends the message: **[➤ Track]** (an arrow and the distance in the action bar - `/mystic track`), **[Coords]** (copy the coordinates), **[✦ Compass]**, **[✖ Mute]**. Track, Coords and Compass only exist when the spawn profile announces `EXACT` (with `REGION` or `HINT` the place stays secret). `/mystic mute` hides announcements for that player. The same `[[label|action|hover]]` syntax (`run:`, `suggest:`, `copy:`, `url:`) works in **any** line of the lang files, and `/mystic help`, `/mystic list`, `/mystic chests`, `/mystic top` (stat chips, period switch) and `/mystic structure list` are clickable.

**Per-event settings** (`announce:` in `config.yml`, one block each for `on-spawn`, `on-open`, `on-rare`, `on-expire`, `on-hunt`, `on-guards`): `enabled`, `channels` (any of CHAT, TITLE, ACTIONBAR), `radius`, `world-only`, `tiers` (only these tiers), `permission`, `mutable`, `sound` (+ `volume`, `pitch`), `animated`, `line-delay-ticks`, `line-sound`, `title-animation` (SHIMMER, FADE, NONE) and `buttons`. Titles come from `announce.titles.<event>` in the lang file. `navigation:` sets the arrow's duration, refresh rate and arrival radius.

- **Type** is how it opens (roulette, full chest, mystery cards, instant, random); **Wakes up in** is the activation timer (see below). The spawn card no longer lists the loot.
- `announce: EXACT | REGION | HINT | NONE` in the spawn profile controls the location line (coordinates, rough area, or distance and direction).
- `announce.on-spawn.sound` is played to **every** recipient (not at the chest), so nobody misses it.
- Texts are lists in `lang/*.yml` (`announce.spawned.exact`, …); empty lines (for example no structure) are dropped.
- **Boss bar** (`bossbar:`): one bar per standing chest with tier, time left, coordinates and open type; it drains as the time runs out. `viewers: ALL | WORLD | RADIUS`, `color: AUTO` (tier colour), `style`.
- **Hologram timer**: a second line under the chest name counts down (`world-chest.hologram.countdown`).
- **`/mystic chests`** lists standing chests with their time left (coordinates only with `mysticchest.chests.coords`).
- **Spawn rhythm**: `trigger: {type: INTERVAL, minutes: 45, jitter-percent: 30}` makes gaps irregular (31–58 minutes); `cooldowns.spawn-seconds: 600` is a hard minimum between any two automatic spawns; `max-active` caps chests per profile; `TIMES` and `ONLINE_THRESHOLD` triggers give fixed or population-based events.
- **Clearings**: `prefer-flat: 70` makes 70% of spawns look for flat, treeless ground first.

## Structures

Chests can appear inside a building: seventeen built-in shapes (`pyramid`, `temple`, `obelisk`, `henge`, `gate`, `tower` with an outer spiral stair, `colosseum` arena, `crystals`, `runes`, and the worked-out ones: `ruined_portal` (broken nether portal with crying obsidian, magma and lava pools), `volcano` (hollow cone with a lava crater, a lava river between rock lips and a stepped trail to the chest ledge), `shipwreck`, `dragon_bones`, `castle_ruin`, `witch_hut`, `graveyard`, `nether_outpost` (bridge over a contained lava stream)), plus your own. Every spawn rolls its own size, height, ornaments and ruin level, so no two look alike; the theme is the biome's most of the time (`theme-surprise-percent`) or fully random (`theme: RANDOM`). It rises layer by layer, the chest waits on top or on the altar, and after the chest is opened or times out the building collapses and the original terrain returns (also after a crash). `structures:` in `config.yml` sets the chance, theme (`AUTO` from the biome, or DESERT, STONE, NETHER, END, FROST, OCEAN), ruin level (`decay`), build speed and protection.

**Your own structures**

1. `/mystic structure wand`, then left/right click two opposite corners around your building.
2. Put a **chest** inside the selection where the mystic chest should appear.
3. `/mystic structure save <name>`. The chest is detected automatically, air is not stored, the lowest layer sits on the ground, a foundation fills gaps below it.
4. `/mystic structure edit` opens the catalog: weights with live chance %, on/off, fixed theme, preview (`F`), delete (`Q` twice). `/mystic structure preview <name>` builds it in front of you for 40 seconds.

**Sign markers (the fast way to build a real encounter).** Put signs inside the selection before saving; their first line decides what they become, the signs themselves are removed:

| First line | Meaning |
|---|---|
| `[chest]` | the main chest cell (instead of placing a chest block) |
| `[loot]` + second line = tier (e.g. `elite`) | an extra mystic chest of that tier here (no second line = same tier as the main one). The structure collapses when the **last** of its chests is gone |
| `[guard]` | a guard stands exactly here (several signs = several spots, used in turn; the rest spawn at random) |
| `[boss]` | the boss of a boss level stands here |

`/mystic structure save` reports how many markers it found.

**Tags, filters and tuning** (`/mystic structure info <name>` shows them, `/mystic structure set <name> <key> <value>` changes them, `-` clears a list; all stored in `structures.yml`): `tags` (built-ins have some, e.g. `nether`, `ruins`, `castle`; use them as `shapes: [tag:nether]` in a spawn profile or a tier), `tiers` (only for these chests), `biomes` (part of a biome name, e.g. `desert,badlands`), `worlds`, `rotate` (false = never turned), `debris` (multiplier of the rubble), `weight`, `theme`, `enabled`. Rubble around a structure uses the structure's own materials (built-ins have a palette per shape, saved ones use their three most used blocks). `/mystic structure preview <name> r0..r3` shows a saved structure turned 0-3 quarter turns.

**Sits in the landscape**: the floor is level with the ground, trees and plants on the site are cleared, columns under the floor are filled down to the ground, and where the ground beside the building is lower, earth (grass on top) is filled in and slopes down away from it (`structures.blend`, `width`). **Rubble** (`structures.debris`): rocks, clusters, toppled columns, broken stubs and now and then a piece of an arch are scattered up to `radius` (30) blocks around, appearing from the building outwards; the amount varies by ±40% per spawn, it only lands on free ground away from player-made blocks, and it is removed together with the structure unless `keep-after-collapse: true`. Tune or switch off per profile or tier: `structure: {debris: {radius: 40, pieces: 80}}`.

Saved structures keep their exact blocks, are rotated randomly (`rotate: true`) and do not clear the terrain around them (only trees and grass). Sites that are steep, in water or contain player-made blocks (chests, doors, beds, signs…) are skipped and a plain chest appears instead. Per spawn profile or tier you can force a structure: `structure: {shape: gate, theme: end, chance: 100}`.

**Share structures between servers.** `/mystic structure export <name>` writes `plugins/MysticChest/exchange/<name>.yml`. Copy that file into the `exchange/` folder of another server and run `/mystic structure import <file> [new name] [overwrite]`. Files are checked on import (size limits, valid blocks); built-in names are protected, an existing name needs `overwrite`. Files saved on 1.12 and on 1.13+ are not compatible with each other.

**Outlines (glow).** `duel.glow` (+ `glow-color`, `glow-contested-color`) makes the capturer glow in a colour (it switches while an enemy stands in the zone); `guards.glow`, `glow-color` and `boss-glow-color` do the same for guards and the boss. Any chat colour name (`RED`, `GOLD`, `AQUA`...). A guard level or a single mob can override `glow` and `glow-color`; a tier can override the whole block like any other setting.

## Activation: the chest sleeps first

By default a new world chest does **not** open at once. It *sleeps* (`activation:` in `config.yml`, per tier `activation: {seconds: 240}` in `tiers.yml`; the shipped tiers use 60 s for poor up to 420 s for the death chest, the admin chest has none). While it sleeps the hologram and the boss bar count down to the wake-up, guards are already there, atmosphere and aura run, and anybody who clicks is told how long is left. When the time is up everybody gets the "awake" message (`announce.on-activate`, same channels and buttons as the spawn card; profiles that announce only a region or a hint get the matching short version, `announce: NONE` stays silent), a sound and a firework play, and only now does the lifetime (`ttl-seconds`) start; the hunt bonus window counts from the wake-up. The spawn card shows "wakes up in" and the total time until the chest disappears. `/mystic activate <id|all>` wakes chests early (admin). Set `activation.enabled: false` (or `activation: {enabled: false}` on one tier) for the old behaviour where a chest opens immediately. The beacon event does not use it.

## Volcano eruptions

A chest standing in the `volcano` structure makes it erupt (`eruption:` in `config.yml`, per tier override possible): a warning rumble with an action bar alert, smoke and fire over the crater, then lava bombs fly on arcs and land up to `bomb-radius` away. A bomb that lands within `hit-radius` of a survival player does `damage` and sets them on fire. Nothing is placed or destroyed; bombs are particles and sound. Eruptions only run while a player is within `view-range`, so an empty area costs nothing. Interval, duration, bomb rate, damage and sounds are all configurable, `enabled: false` turns it off.

## Why didn't a chest appear? `/mystic debug`

Shows, per spawn profile, when it fires next and what blocks it right now (disabled, too few players, `max-active` reached, global cooldown, no valid tier, no fixed points), then the last 15 decisions the plugin made (skipped spawns, rejected structure sites such as "too steep", postponed beacon events, successful spawns). `/mystic debug clear` empties the list. No `debug: true` needed.

## Events: beacon killer, death chest

**Beacon killer** (`beacon-event:` in `config.yml`, off by default): announced ahead (boss bar countdown plus warnings at `warn-seconds`), it appears at a random spot as a platform with a real beacon on an iron pyramid and **four elite chests** around it. Inside `zone-radius` players get harsher potion effects at every **stage** (`stages`, freely editable) and **coins every second** - `start + growth × seconds spent inside`, at most `max` - paid to the economy (`currency`, or a `command`) when they leave the zone or it ends. Dying in the zone loses the unpaid coins (`lose-on-death`). Chests are looted like any other (capture, hunt), guards are not spawned. After `duration-seconds` everything collapses and the terrain returns; the top earners are announced. `trigger` is `INTERVAL` (with jitter) or `TIMES`. Run or stop one by hand: `/mystic event beacon start|stop|status` (a manual start ignores `min-players`).

**Death chest**: give a tier `deathzone: {enabled: true, radius: 25}` (the `death` tier is included). Players inside **glow** (or get any `effects`), a death inside drops `drop-rolls` extra rewards from the tier's pool for the killer, the kill is announced and can run `kill-commands`; the *zonekills* stat is counted. Put it on a PvP arena: add a point with `/mystic point add pvp_arena`, enable the `death_chest` spawn profile (FIXED_POINTS, TIMES), and the tier already requires a 20-second **capture**.

**New open modes**: `VOLCANO` - the chest erupts, lava and flames burst out and every reward is launched as an item in a fountain (`volcano:` duration, spread, launch power); `PINATA` - hit the standing chest (right click) and a reward falls out with every hit, the last hit breaks it (`pinata:` hits, cooldown). Both work in `RANDOM` (`random-mode.weights`) and per tier. A pinata needs a standing chest; opened from an item it becomes a volcano in front of you.

**Tidier announcements**: one card per spawn with the type, the guards (`Guards: 4 (Boss)`), the loot, the place and the time; the separate guard alert is off by default (`announce.on-guards`).

## Hunt, guards, atmosphere, capture

Each of these is configured in `config.yml` and can be overridden **per tier** in `tiers.yml` (a tier key wins over the global one).

**Hunt**: open a world chest within `hunt.window-seconds` of its appearance and you get `bonus-rolls` extra rewards, optional `commands`, an announcement and a point in the *hunts* leaderboard. `tiers.yml`: `hunt: {window-seconds: 60, bonus-rolls: 3}`.

**Guards**: `guards.chance` percent of chests get guards (mobs around the chest, `radius`, pulled back past `leash`). While they live the chest is locked (`lock-chest`). A **level** is a recipe under `guards.levels.<name>`: a list of `mobs` (type, count, name, health, helmet/chestplate/leggings/boots/weapon, effects) and optionally a `boss` (same fields plus a `bossbar` and `commands` for the killer, `{player}`). Make as many levels as you like; a tier chooses its own chance and level weights:

```yaml
# tiers.yml
elite:
  guards: {chance: 60, levels: {veteran: 70, boss: 30}}
```

Gear never drops. Guards that vanish without dying (peaceful difficulty, despawn) are counted as gone so a chest never stays locked; the console warns when the world is on PEACEFUL.

**Atmosphere**: a mood only around the chest. Players within `radius` get their own time of day and weather (for example a midnight storm) and harmless lightning flashes; everybody else, and the world itself, are untouched, and leaving the radius restores the normal sky at once. `tiers.yml`: `atmosphere: {enabled: true, time: MIDNIGHT, weather: STORM, radius: 45}`. Options: `time` (DAWN, NOON, DUSK, NIGHT, MIDNIGHT, 0–24000, NONE), `weather` (CLEAR, RAIN, STORM, NONE), `lightning-interval-seconds`, `lightning-radius`, `ambient-sound`.

**Capture** (a duel for the chest): when other players are near (`detect-radius`) opening a chest starts a capture. The boss bar fills while the capturer stands in the `zone-radius` **alone**; any enemy in the zone drains it by `decay-per-second`. The capture fails if the capturer dies or leaves, and then anyone can start again. Alone, a player simply opens the chest (`trigger: ALWAYS` forces the capture every time). Same scoreboard team = no contest. `tiers.yml`: `duel: {enabled: true, capture-seconds: 15}`.

## Leaderboard, stats, achievements

Counted per player: `opens`, `rares`, `hunts`, `guards`, `bosses`, `captures`; for the current period (`leaderboard.period`: DAILY, WEEKLY, MONTHLY, ALL) and for all time.

- `/mystic top [stat] [all]`, `/mystic stats [player]`.
- Hologram boards: `/mystic board create <name> <stat> [all]` at your position (refreshed every 30 s), `remove`, `list`.
- At the end of a period the top players of `reward-stat` get the commands in `leaderboard.rewards` (`{player}`, `{rank}`, `{value}`, `{stat}`) and the winners are announced.
- **Achievements** (`achievements:` in `config.yml`): a stat, a threshold, a title/subtitle (a string or `{en: .., ru: ..}`), optional commands and a public announcement. Add as many as you like.
- Placeholders: `%mysticchest_top_<stat>_<rank>_name%`, `%mysticchest_top_<stat>_<rank>_value%`, `%mysticchest_toptotal_...%` (all time), `%mysticchest_stat_<stat>%`, `%mysticchest_stattotal_<stat>%`.

## Cooldowns, limits, pity

```yaml
cooldowns:
  scope: PLAYER_TIER   # PLAYER | PLAYER_TIER | GLOBAL
  open-seconds: 0
  buy-seconds: 0
  claim-seconds: 0
  spawn-seconds: 0
limits:
  max-opens-per-day: 0
  max-purchases-per-day: 0
  max-active-world-chests: 20
```

`0` means off. Scopes for the open cooldown: `PLAYER` (one cooldown for any chest), `PLAYER_TIER` (per tier), `GLOBAL` (everybody shares the cooldown of a tier). Every value can be overridden per tier. Cooldowns survive restarts and crashes. Players with `mysticchest.bypass.cooldown` / `mysticchest.bypass.limits` ignore them.

**Pity**: `pity: {after: 15}` in a tier. After 15 openings without a *rare* reward, the next opening is guaranteed to contain one. "Rare" means a chance below `loot.rare-below-percent` (default 3%).

**Per-player win limit**: `limit-per-player: 1` on a reward; a player can win it at most that many times, even inside one opening.

## Rewards (loot)

### In game

| Command | Effect |
|---|---|
| `/mystic loot add <tier> [weight] [--keep] [--hand]` | moves **all** items of your inventory/hotbar (not armor/offhand) into the pool, each stack as one reward; `--keep` leaves the items with you, `--hand` adds only the held item. Run it as many times as you like. |
| `/mystic loot addcmd <tier> <weight> <command…>` | a command-only reward; the icon is your held item |
| `/mystic loot cmd <tier> <#> add [p:] [50%] <command…>` | add a command to reward `#` (`p:` run as the player, `50%` chance) |
| `/mystic loot cmd <tier> <#> remove <n>` / `list` / `clear` | manage commands |
| `/mystic loot weight <tier> <#> <weight>` | change the weight |
| `/mystic loot remove <tier> <#> [--return]` | delete (and optionally get the item back) |
| `/mystic loot list <tier>` | list with chances |
| `/mystic loot edit <tier>` | open the GUI editor |
| `/mystic loot clear <tier> confirm` | delete everything |

Identical items are merged (their weights add up) when `loot.merge-identical` is on.

**Faster ways to fill a pool**

| Command | Effect |
|---|---|
| `/mystic loot fill <tier> [weight] [--keep]` | opens an empty chest window: drop everything that should become a reward, close it, every stack is added (`--keep` gives the items back) |
| `/mystic loot preset list` | ready sets: `food`, `diamond-gear`, `resources`, `redstone`, `nether` and your own |
| `/mystic loot preset <tier> <name> [x2] [--replace]` | adds a preset to the pool (`x2` doubles its weights, `--replace` empties the pool first); items missing on your server version are skipped and counted |
| `/mystic loot preset save <tier> <name>` | saves the pool as your own preset (`plugins/MysticChest/presets/`) |
| `/mystic loot copy <from> <to> [--replace]` | copies a pool to another tier |
| `/mystic loot check <tier>` | total weight, rare share, entries with commands, duplicates, entries lost to unsupported items |
| `/mystic loot export <tier> [name]` / `import <file> <tier> [--replace]` | share a pool through `plugins/MysticChest/exchange/loot-<name>.yml` |

In the GUI editor the number keys **3-7** set the weight to 100 / 40 / 12 / 4 / 1 (common … legendary).

### GUI editor

Every reward shows its chance, weight, amount, commands and flags.

| Action | Result |
|---|---|
| Left / right click | weight +1 / −1 (Shift: ±10) |
| `F` (swap hands) | type commands in chat: `give {player} diamond 1`, `p: spawn`, `25% kit vip {player}`, `-2` removes command 2, `clear`, `cancel` |
| Number key `1` | toggle "announce to everyone" |
| Number key `2` | toggle "give the item" (off = command-only reward) |
| `Q` twice | delete |
| Drop an item on the window, or Shift-click it in your inventory | add as a reward |
| Bottom row | pages, add whole inventory, add held item, switch tier, close |

### File format

`loot/<tier>.yml`:

```yaml
entries:
  - id: a1b2c3
    material: NETHER_STAR        # or item: <full serialized item with NBT>
    amount: 1                    # or min: / max:
    weight: 2
    broadcast: true
    limit-per-player: 1
    permission: ""
    give-item: true
    commands:
      - run: CONSOLE
        command: kit crusher {player}
        chance: 100
```

Plain items are stored as `material:` (readable and valid across versions); items with names/enchants/NBT are stored as a full `item:` (valid for the Minecraft version they were saved on). The file is rewritten, without comments, whenever loot is changed in game.

## Economy

```yaml
economy:
  provider: AUTO
```

| Provider | Notes |
|---|---|
| `AUTO` | first installed of ExcellentEconomy, CoinsEngine, Vault, PlayerPoints |
| `VAULT` | Vault / VaultUnlocked and the economy plugin behind it; the tier currency name is ignored |
| `EXCELLENT_ECONOMY` | multi-currency, needs PlaceholderAPI; charges with console commands |
| `COINS_ENGINE` | same mechanism as above, preset **not verified** |
| `PLAYER_POINTS` | integer points through the PlayerPoints API |
| `NONE` | no purchases; use `/mystic give` |

Per tier you can name the provider in `currency`:

```yaml
currency: gold                      # default provider, currency "gold"
currency: "excellenteconomy:coins"  # ExcellentEconomy, currency "coins"
currency: playerpoints              # PlayerPoints
currency: vault                     # Vault
```

Short forms `ee`, `ce`, `pp` work too. This lets gold come from Vault and donate tokens from PlayerPoints.

**ExcellentEconomy / CoinsEngine** read the balance from a PlaceholderAPI placeholder and charge by console command. The templates are in `config.yml` because names differ between versions. Verified on ExcellentEconomy 2.8.0: it registers one command per currency, so the defaults are `{currency} take {player} {amount}` and `%excellenteconomy_balance_raw_{currency}%`. The currency (for example `gold`) must exist in that plugin.

Check what the plugin found: `/mystic economy`, and your balance of a currency: `/mystic economy <currency>`.

## Effects and announcements

`effects.open | tick | win | rare | spawn | expire`: `sound` (names like `ENTITY_PLAYER_LEVELUP` or a namespaced key such as `minecraft:block.bell.use`; both work on every version), `volume`, `pitch`, `particle`, `count`, and for players `title`, `subtitle`, `actionbar` (placeholders `{player}`, `{item}`, `{tier}`). Empty means off. Particles and sounds only reach players within `performance.effects-view-distance`.

`announce.on-spawn | on-open | on-rare`: `type` (`CHAT`, `TITLE`, `ACTIONBAR`, `NONE`), `radius` (`-1` = everyone), `world-only`. Messages are sent in each player's own language. Rewards below `rare-below-percent` or marked `broadcast: true` use `on-rare`.

### Aura, fireworks, compass and extra effects

- **Aura** around every standing world chest: `effects.aura.style` = `RING`, `PILLAR` (a light column), `SPIRAL` or `NONE`, with `particle`, `radius`, `height` and a soft chime (`sound`, `sound-interval-seconds`). With a `DUST`/`REDSTONE` particle (1.13+) it is tinted in the tier colour. One shared animation that only runs while chests exist and only draws for players within `effects-view-distance`.
- **Tier colour**: the first colour code of the tier name (`&6` = gold), or `effects.color: "#ff55ff"` in `tiers.yml`. Per tier you can also override `effects.aura.style` and `effects.aura.particle`.
- **Random colours around the chest**: `aura.color-mode` is `TIER`, `RANDOM` (every particle a new colour) or `RAINBOW` (a moving rainbow); it applies to `DUST` particles. Fireworks use 2–3 random colours per burst (`firework.colors: RANDOM`) or the tier colour (`TIER`).
- **Guide lines**: a cross of four particle lines (N, E, S, W) leads to every standing chest, starts outside the structure and follows the ground; a light pulse runs along them towards the chest. Walk through a line and the particles around you change colour for `flash-seconds` (2) - only you see it. `effects.lines`: `enabled`, `length`, `step`, `interval-ticks`, `color-mode`.
- **Fireworks** (instant and harmless) in the tier colour: `effects.firework` (`on-rare`, `on-spawn`, `type`, `count`, `flicker`, `trail`).
- **Expire puff** when an unopened chest times out: `effects.expire`.
- **Extra player effects** in any effect block (`open`, `win`, `rare`, …): `potions: ["SPEED:10:1"]`, `lightning: true` (a flash, no damage), `firework: true`.
- **Chest compass**: `/mystic compass` gives an item; right click points it at the nearest standing chest and tells the distance and direction (`compass:` in `config.yml`, permission `mysticchest.compass`).

`performance.low-resource: true` switches aura, particles, holograms and fireworks off.

## Commands and permissions

| Command | Permission (default) |
|---|---|
| `/mystic shop` | `mysticchest.shop` (everyone) |
| `/mystic preview <tier>` | `mysticchest.preview` (everyone) |
| `/mystic list`, `/mystic help` | – |
| using chests | `mysticchest.use` (everyone) |
| `/mystic compass` | `mysticchest.compass` (everyone) |
| `/mystic chests`, `/mystic track [id]` | `mysticchest.chests` (everyone); coordinates: `mysticchest.chests.coords` (op) |
| `/mystic mute` | everyone |
| `/mystic structure …` (incl. `export`, `import`) | `mysticchest.admin.structure` (op) |
| `/mystic debug [clear]` | `mysticchest.admin` (op) |
| `/mystic activate <id\|all>` | `mysticchest.admin` (op) |
| `/mystic event beacon start\|stop\|status` | `mysticchest.admin` (op) |
| `/mystic top`, `/mystic stats` | `mysticchest.top` (everyone) |
| `/mystic board …` | `mysticchest.admin` (op) |
| `/mystic give`, `spawn`, `reload`, `perf`, `point`, `economy` | `mysticchest.admin` (op) |
| `/mystic loot …` and the editor | `mysticchest.admin.loot` (op) |
| ignore cooldowns and claim locks | `mysticchest.bypass.cooldown` (nobody) |
| ignore daily limits | `mysticchest.bypass.limits` (nobody) |

Aliases: `/mc`, `/mistic`.

## PlaceholderAPI

Registered automatically when PlaceholderAPI is installed.

| Placeholder | Value |
|---|---|
| `%mysticchest_cooldown_<tier>%` | seconds until the player can open that tier (0 = now) |
| `%mysticchest_cooldown_formatted_<tier>%` | `1h 5m 3s` or `Ready` |
| `%mysticchest_opens_today%` | chests opened today |
| `%mysticchest_pity_<tier>%` | openings since the last rare reward |
| `%mysticchest_active_chests%` | chests standing in the world |

## Performance

The plugin is built to cost almost nothing while idle.

- One shared timer for every deadline (chest TTL, spawn profiles, delayed saves) and one shared animation ticker that only runs while someone is spinning a roulette. `/mystic perf` shows "Bukkit tasks owned: 0" on an idle server.
- Files are written on a background thread, debounced, and atomically (temp file + rename), so a crash never leaves a half-written file.
- Loot rolls use cumulative weights and a binary search; GUI items are cached per language until loot changes.
- Measured on Paper 1.21.11: a loot roll takes tens of microseconds; opening a roulette costs a few milliseconds of server GUI work (the very first opening after start is slower while the JVM warms up). No objects stay in memory after GUIs close.

`performance:` in `config.yml`: `async-io`, `save-interval-seconds`, `gui-cache`, `max-concurrent-animations`, `effects-view-distance`, `particle-limit`, `low-resource` (no particles, no holograms).

## Troubleshooting

| Problem | Check |
|---|---|
| "No economy is connected" when buying | `/mystic economy`; install Vault + an economy plugin, or set the tier `currency` to a provider that exists |
| "Not enough funds" with plenty of money (ExcellentEconomy) | the currency id in `tiers.yml` must exist there; watch the console for "cannot read the balance of currency …" |
| A chest item does nothing | `use.click-air` / `use.click-block`; the `mysticchest.use` permission; region plugins cancelling the click |
| Chests don't spawn | profile `enabled`, `min-players`, `worlds` names, `max-active`; set `debug: true` to see why each attempt was skipped |
| Some rewards missing on an old server | items that don't exist in that version are skipped silently |
| Text shows `<some.key>` | a missing key in your custom lang file |

Turn on `debug: true` for extra console output.

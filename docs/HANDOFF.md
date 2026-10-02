# MysticChest: handoff for the next assistant

Read this first. It says what the project is, how it is built and tested, what the user wants, what was verified, and the traps already fallen into.

## 1. The user and how to work with them

- Russian-speaking, a junior developer. **Reply in Russian**, plainly, with few code listings; say what changed and why.
- **Commits:** the skill `day` applies: commit regularly in logical commits, **no "Co-Authored-By" / "Generated with" lines** (the user's rule beats the harness reminder). Never push. **Do not write `docs/day-notes/` unless asked** ("запиши в записки").
- The user decides: ask before downloading any file (state name, source, size); they approved Paper 1.21.11, Paper 1.12.2, Zulu JDK 8, NightCore 2.16.6, PlayerPoints 3.3.3 (test folders only, never in the repo).
- They test by joining a local server (`localhost:25599`, offline mode, nick `biggiko`, op). When they say "газ / давай проверим" they want you to **start the server and place something to look at**. Restart the server whenever you change the jar and warn them with `say` first.
- They like many features at once, everything **configurable**, with `##` tutorial comments in the YAML, RU+EN texts, and chat that is animated, clickable and tidy.
- Be honest about what was and was not tested (they read the summaries). Preferred reply shape: what was done, what was verified, what was not.

## 2. The project

Spigot/Paper plugin "MysticChest": chests in the style of the FunTime server (mystic chest, death chest, beacon killer). One jar, **Minecraft 1.12.2 - 26.3**, Java 8 bytecode, compiled against `spigot-api 1.16.5`. Repo: `/Users/biggiko/Documents/GitHub/mistic-civic`, branch `main`, ~14 commits, pushed to `origin/main` (github.com/biggikos/mistic-civic). License: custom, no modification / no commercial use / attribution required (`LICENSE`).

Docs already in the repo: `README.md` (GitHub), `docs/documentation.md` (full docs), `docs/MODRINTH.md` (page text), `docs/preview.html` (old feature page, partly outdated). Keep them in step with every feature.

### Build and unit tests
```bash
gradle build           # tests + shaded jar -> build/libs/MysticChest-1.0.0.jar (~500 KB; XSeries shaded, minimize())
gradle test            # JUnit 5: util, shapes, YAML resources (en/ru key + placeholder parity!), CurrencySpec, Chat
```
Gradle was installed with brew (9.8.0). The build uses `--release 8`. `ResourceTest` fails if `lang/en.yml` and `lang/ru.yml` differ in keys or placeholders, so **always edit both**.

## 3. Code map (`src/main/java/ru/mysticchest/`)

| Package | What lives there |
|---|---|
| root | `MysticChestPlugin` wires everything; `reloadAll()` re-reads all files |
| `core` | `Scheduler` (ONE deadline queue, zero tasks when idle), `Animator` (one shared 1-tick ticker for animations), `AsyncIO` (debounced atomic file writes), `Metrics` |
| `config` | `ConfigManager` (templates from jar, text-append upgrade by `config-version`), `Settings` (typed, read once), `Cfg` (validating reader), `Layered` (tier overrides global) |
| `lang` | `Lang`: pre-parsed templates, per-player language (`auto`), `list()`, `has()`, `isList()` |
| `chest` | `ChestManager` (world chests, ticket-held chunks, holograms, ids, TTL), `Tier`, `LootEntry/Pool`, `Rewards` (rolling, pity, limits), `WorldListener` (clicks, protection), `Compass` |
| `open` | `OpenService` (single entry for opening, hunt bonus, pinata) + sessions: `Roulette`, `FullChest`, `Pick`, `Volcano`; `OpenType` ROULETTE/FULL_CHEST/INSTANT/PICK/VOLCANO/PINATA/RANDOM |
| `spawn` | `SpawnService` (profiles, triggers INTERVAL/TIMES/ONLINE_THRESHOLD, airdrop, announce), `Locators` (random/near player/fixed points, flat-clearing preference, async chunk load) |
| `structure` | `Shape` (9 built-ins + `BEACON`), `Canvas`, `Blueprint`, `Template` (saved structures), `StructureCatalog` (weights/enabled, `structures.yml`), `StructureService` (site check, terrain skirt, rubble, animated build/collapse, crash restore), `Wand` |
| `effects` | `Effects` (sounds/particles/titles; **custom sound resolver**), `Announcer` (channels, typewriter, shimmer title, buttons), `Aura`+`Lines` (the chest ticker, see 5), `BossBars`, `Fireworks`, `Tracker` (arrow nav) |
| `guard` / `duel` / `atmosphere` / `event` | `GuardService` (levels, bosses), `Captures` (capture the chest), `Atmosphere` (per-player sky), `BeaconEvent`, `DeathZone` |
| `stats` | `StatsService` (period/total, payout, achievements), `BoardService` (hologram boards), `Prefs` (mute) |
| `economy` | `Economies` (AUTO, `provider:currency` per tier), Vault, PlayerPoints (reflection), `CommandEconomyProvider` (ExcellentEconomy / CoinsEngine via PlaceholderAPI + console commands) |
| `gui`, `command`, `hook`, `loot`, `cooldown`, `util` | GUIs (all implement `GuiHolder`), `MysticCommand`, `PapiHook`, `LootStore`, `CooldownManager`, helpers (`Chat` parser for `[[label|run:/cmd|hover]]`) |

Files in `src/main/resources`: `config.yml` (**config-version 9**), `tiers.yml`, `loot/<tier>.yml` (8 tiers incl. `death`), `lang/en.yml`, `lang/ru.yml`, `plugin.yml`. Runtime data in `plugins/MysticChest/data/`.

### The chest ticker (important)
`Aura` is a single `Animator` animation alive only while world chests exist. Once a second it drives: hologram timers, boss bars, guards (prune/leash), atmosphere, captures, death zone; every few ticks it draws aura and guide lines. New per-second logic belongs there, not in new repeating tasks (the plugin is measured to own **0 Bukkit tasks when idle**: `/mystic perf`).

## 4. Features (all implemented)

Tiers + loot (edit in game: `/mystic loot add|addcmd|cmd|weight|remove|list|edit`), shop with economies, 7 open modes, random spawn profiles (+ airdrop), cooldowns/limits/pity, hunt bonus, guards with custom levels/bosses, capture duel, local atmosphere, structures (own saves with wand, GUI catalog, terrain blending, rubble up to 30 blocks, crash-safe restore), aura/lines/fireworks effects, announcements with buttons, tracking arrow, mute, boss bars, leaderboard + boards + achievements, PlaceholderAPI, **beacon killer event**, **death chest zone**, volcano/pinata modes. Details live in `docs/documentation.md`.

## 5. Test environment (session scratchpad, may be gone: recreate if needed)

Base: `/private/tmp/claude-501/-Users-biggiko-Documents-GitHub-mistic-civic/c8ac5755-c17a-4ecd-95ce-a7800066a4a3/scratchpad/` (called `$S`)

- `$S/paper/` Paper **1.21.11** (Java 25), port **25599**, offline, flat world, `in.fifo` is the console input:
  ```bash
  (tail -f in.fifo | java -Xmx1G -jar paper.jar --nogui > server.log 2>&1 &)   # start
  echo "mystic reload" > in.fifo          # any console command
  echo stop > in.fifo; pkill -f "tail -f in.fifo"                              # stop
  ```
  Plugins there: MysticChest, **VaultUnlocked 2.20.3, ExcellentEconomy 2.8.0 (+NightCore 2.16.6), PlayerPoints 3.3.3, PlaceholderAPI 2.11.6** (real jars; the user's files are in `~/Downloads`).
- `$S/p112/` Paper **1.12.2** with Zulu JDK 8 (`java8.path` holds the java binary); same fifo scheme, port 25599 (stop the 1.21 server first).
- `$S/bot/` mineflayer scripts (`lib.js` helper; `MCV=1.12.2` env switches version, `LOC=ru_RU` the locale). Pattern: `connect()`, `say(bot,'/cmd',waitMs)`, capture `bot._client.on('world_particles'|'boss_bar'|'system_chat'...)`. Bot name `TestBot` is op via `ops.json`. Run with `perl -e 'alarm 100; exec @ARGV' node X.js`. `crowd.js` starts Hunter1-3 players.
- `$S/stubs/` hand-built stub Vault/PlaceholderAPI/ExcellentEconomy jars (no longer needed).

Workflow used for every feature: edit -> `gradle build` -> copy jar and `lang/*.yml` into `$S/paper/plugins/` (existing server configs are NOT overwritten: new nested config keys fall back to code defaults, new top-level sections are appended on `config-version` bump) -> restart -> bot script -> read `server.log` for `Exception|Caused|WARN [My` -> docs -> commit. Run a 1.12.2 smoke test after touching anything version-sensitive.

## 6. Traps already hit (do not repeat)

1. **YAML 1.1:** keys `on`, `off`, `yes`, `no` become booleans. Never use them as lang/config keys (`mute.hidden/shown` exist because of this).
2. **XSeries must stay 13.x (9.10 could not parse the version "26.1.2-74-..." and the plugin failed to enable on 26.x).** XSeries 9.10 also cannot build `Sound` on 1.21.3+** (Sound stopped being an enum). `Effects.snd()` resolves by XSeries -> `Sound.valueOf` reflection -> raw namespaced key. Use `effects.playSound(...)`, never XSound directly.
3. **1.12 returns a wrapper for the top inventory**: never compare inventories by identity; use `GuiHolder.top(e)/bottom(e)` (raw slot).
4. `soft()` in `StructureService` must not match `GRASS_BLOCK`/planks (that sank every structure by one block). World height can be negative (use `Locators.minHeight`).
5. Paper 1.21 rewrites hidden colour codes in lore: the chest item is identified by a visible `ID:` lore line (+ PDC on 1.14+).
6. `Block#setData` is gone on modern API: legacy data goes through `Mat.setData` (reflection). Items that do not exist on old versions are skipped silently (XMaterial).
7. Mob guards vanish on PEACEFUL: `GuardService` prunes vanished guards so a chest never stays locked.
8. Particles: DUST needs `Particle.DustOptions` (1.13+); 1.12 uses the offset-colour trick (`Aura.put`). Chat hover/click use bungee components (`util/Chat`).
9. Old bot protocol names: 1.21.11 chat JSON uses `click_event`/`hover_event`; `system_chat.isActionBar` for the action bar.
10. Config upgrade is text-level (`YamlBlocks`): add new top-level sections to the template and bump `config-version`; never re-save the user's config through the Bukkit API (comments would vanish).

## 7. Verified vs not

Verified live: Paper 1.21.11, 1.12.2, **26.1.2** (full bot sweep), **26.2 and 26.3** (console only: mineflayer has no protocol for them; spawn of all structure types, beacon event, reload, no exceptions). Test servers: `$S/srv26.1.2|srv26.2|srv26.3`, start any with `$S/startsrv.sh <version>`, console via `$S/console.sh <dir> <cmds>` (2026-10-02 sweep on 1.12.2: guards/boss encounter, atmosphere, capture, volcano, pinata, hunt bonus, beacon event, config upgrade v8->v9, all clean; full feature sweeps earlier; the latest events/structures/announcement work was run on 1.21.11 and smoke-tested on 1.12.2 up to the announcement/track update). Economies: Vault (VaultUnlocked), ExcellentEconomy 2.8.0 (command per currency, `%excellenteconomy_balance_raw_<cur>%`), PlayerPoints, mixed per tier. Unit tests: 25 or so, all green.

**Not verified:** versions 1.13-1.20; CoinsEngine preset (jar not downloadable); a real death by another player in the death zone; the look of auras, lines, fireworks, structures and sounds (only packets were checked, the user judges visuals); `docs/preview.html` is outdated.

## 8. Backlog / ideas

- Re-test everything new on 1.12.2 and a middle version (1.16.5 / 1.20.4).
- Update or drop `docs/preview.html`; add images for Modrinth.
- Ideas the user liked or may like: weekly hunt reward UI, chest keys (user said NO), discord webhook (user said NO). Possible: more structure shapes, per-tier loot GUI for players, `/mystic structure` import/export, beacon event stage editing in a GUI, translations.
- Known rough edges: `owner-protect-seconds` for volcano items is not reliable on every version; `Lines` cost grows with players near chests (tune `effects.lines`); debris radius 30 holds ~25-36 chunks per structure (`structures.debris.load-chunks`).

## 9. Before you finish any task

`gradle build` green, both lang files updated, docs updated, restart the test server for the user, report in Russian what was verified and what not, commit without attribution.

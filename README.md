# MysticChest

Mystic chests in the style of FunTime for Spigot/Paper **1.12.2 → 1.21.x** (one jar, Java 8 bytecode, RU / EN).

* **Open modes** per tier: `ROULETTE` (SINGLE / SCROLL), `FULL_CHEST`, `INSTANT`, `PICK`
* **Spawn profiles**: `RANDOM_WORLD`, `NEAR_PLAYER`, `FIXED_POINTS`, `AIRDROP` (triggers: `INTERVAL`, `TIMES`)
* **Cooldowns & limits**: open / buy / claim / spawn, per day limits, pity, bypass permissions
* **Loot editing in game**: `/mystic loot add <tier>` moves your whole inventory into the reward pool, full GUI editor with weights, chances and commands
* **Economy**: Vault, ExcellentEconomy (multi-currency via PlaceholderAPI + console commands) or none
* **PlaceholderAPI**: `%mysticchest_cooldown_<tier>%`, `%mysticchest_cooldown_formatted_<tier>%`, `%mysticchest_opens_today%`, `%mysticchest_pity_<tier>%`, `%mysticchest_active_chests%`
* **Languages**: `language: en | ru | auto` (first key of `config.yml`), custom `lang/<code>.yml`

## Build

```bash
gradle build          # tests + shaded jar -> build/libs/MysticChest-1.0.0.jar
```

Needs JDK 8+ to run the build (targets Java 8 bytecode). Compiled against spigot-api 1.16.5; version differences are handled with XSeries and reflection.

## Files

| File | Purpose |
|---|---|
| `config.yml` | global settings; every option is documented with `##` comments |
| `tiers.yml` | per-tier price, name per language, open mode, cooldown/limit overrides, pity |
| `loot/<tier>.yml` | rewards (rewritten by `/mystic loot` and the editor) |
| `lang/*.yml` | texts; missing keys fall back to the built-in English text |
| `data/*.yml` | cooldowns, live chests, fixed points (managed by the plugin) |

Old config files are upgraded in place: missing top-level sections are appended as text (comments are kept) and the fresh template is written to `config.yml.new`.

## Commands & permissions

| Command | Permission |
|---|---|
| `/mystic shop`, `/mystic preview <tier>`, `/mystic list` | `mysticchest.shop`, `.preview` (default: everyone) |
| `/mystic give <player> <tier> [n]`, `spawn <tier> [here\|profile]`, `reload`, `perf`, `point add\|remove\|list` | `mysticchest.admin` (op) |
| `/mystic loot add\|addcmd\|cmd\|weight\|remove\|list\|edit\|clear <tier> …` | `mysticchest.admin.loot` (op) |
| opening chests | `mysticchest.use` (everyone) |
| ignore cooldowns / daily limits | `mysticchest.bypass.cooldown`, `mysticchest.bypass.limits` |

## Notes

* ExcellentEconomy mode reads balances through a PlaceholderAPI placeholder and charges with console commands; both are configurable in `config.yml` because they differ between versions. Verified against real ExcellentEconomy 2.8.0 (+ NightCore 2.16.6 and VaultUnlocked 2.20.3) on Paper 1.21.11: it uses one command per currency id (`money take <player> <amount>`) and `%excellenteconomy_balance_raw_<currency>%`.
* `/mystic perf` shows live numbers (chests, animations, timer queue, owned Bukkit tasks, save stats, roll/open timings).

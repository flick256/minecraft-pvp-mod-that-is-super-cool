# SparBot

A Fabric mod for **Minecraft Java Edition 26.2** that adds PvP sparring bots. A bot plays like a real
player at a chosen skill level, from Beginner to Pro, and it is **fully mortal**: it takes damage, knockback
and fall damage through the same vanilla code as a human, uses up totems, drops its items and dies for real.

The mod is server-side only. It works in singleplayer (the integrated server), on LAN and on dedicated
servers, and players don't need to install anything.

> Status: **milestone 2**: sword duels at a chosen skill level, a full kit system with capture and
> personal layouts, and bots that eat and manage their hotbar.
> See [docs/architecture.md](docs/architecture.md) for the full plan.

## Requirements

| | Version |
|---|---|
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.161.0+26.2 or newer |

Put `sparbot-<version>.jar` and Fabric API in the `mods` folder.

## Quick start

```
/sparbot spawn Sparky pro              spawn a Pro-level bot where you stand
/sparbot fight Sparky <your name>      make it fight you (it also auto-targets nearby players by default)
/sparbot info Sparky                   see what it is thinking (tactic, utility scores, reaction delay)
/sparbot stats Sparky                  hit rate, crit rate, damage, combos, kills and deaths
/sparbot remove Sparky
```

## Commands

All commands need the permission level set in the config (default: gamemasters, i.e. op level 2).

| Command | What it does |
|---|---|
| `/sparbot spawn <name> [profile] [kit]` | Spawns a bot at your position, logging it in like a player |
| `/sparbot remove <bot>` | Disconnects the bot |
| `/sparbot kill <bot>` | Kills it like `/kill`: a real death, drops follow `keepInventory` |
| `/sparbot respawn <bot>` | Respawns a dead bot (manual respawn mode) |
| `/sparbot fight <bot> <entity>` | Assigns an opponent (another player, bot or mob) |
| `/sparbot stop <bot>` | Clears the assigned opponent |
| `/sparbot profile <bot> <profile>` | Changes skill level at runtime |
| `/sparbot kit set <bot> <kit>` | Re-equips a kit (a full kit reset) |
| `/sparbot kit capture <id> [mode]` | Saves **your current inventory** as a kit, slot for slot (marked user-supplied and verified) |
| `/sparbot kit info <kit>` | Shows a kit's provenance (source, version, confidence, deviations) |
| `/sparbot layout capture <id> <kit>` | Saves where **you** keep that kit's items as a personal layout |
| `/sparbot layout set <bot> <layout>` / `clear <bot>` | Makes a bot copy a layout, or go back to the kit's default |
| `/sparbot list` / `profiles` / `kits` / `layout list` | Lists bots, skill profiles, kits and layouts |
| `/sparbot stats <bot>` / `info <bot>` | Fight statistics and the current decision trace |
| `/sparbot reload` | Reloads the config, profiles and kits |

## Skill profiles

The bundled presets are `beginner`, `casual`, `intermediate`, `advanced` and `pro`. A profile is a JSON file
that sets:
- reaction time and simulated ping
- aim: turn speed, smoothing, jitter, overshoot and tracking lead
- clicking: CPS distribution and how strictly the bot waits for the attack cooldown
- reach misjudgement
- technique skills: crit, W-tap, S-tap, strafe, jump reset and spacing
- mistake rate and panic threshold

To add your own, copy a preset from
`sparbot-core/src/main/resources/sparbot/profiles/` into `config/sparbot/profiles/`, change it, then run
`/sparbot reload`. The validator rejects profiles beyond human limits, such as reactions under 100 ms or more than 20 CPS.

## Kits

Kits are JSON files with exact armor, offhand and all 36 inventory slots, plus enchantments, potions and effects.
Any other item data (durability, names, fireworks, trims and so on) goes in `components`, written in the same
syntax as `/give`, for example `"components": "minecraft:damage=120"`.

The easiest way to make a kit is to set up your own inventory and run `/sparbot kit capture <id>`.
A **layout** keeps the kit's contents but moves items to your own preferred slots. Make one with
`/sparbot layout capture`, then give it to a bot with `/sparbot layout set`.
Every kit has a **provenance** block stating its source, game version, confidence and whether it is verified,
plus every deviation made when translating it from an older version. Custom kits go in `config/sparbot/kits/`.

Bundled kits:
- `basic_sword`: SparBot's own duel kit (unenchanted diamond gear).
- `mctiers_sword_recreation`: an **unverified** third-party recreation of the MCTiers-style Sword kit.

No exact MCPVP kit layouts are published anywhere, so none are bundled. See
[docs/kit-research.md](docs/kit-research.md). Send screenshots of the layouts you want and they will be
added as verified kits.

## Fairness and mortality guarantees

These rules never relax, and the build fails if any of them breaks:

- **Mortal:** the bot is a plain survival `ServerPlayer`. It has no invulnerable flag, no creative or spectator
  mode and no flight. Nothing cancels or scales its damage, and it gets no free healing.
- **Watched:** a mortality guard checks every tick and removes any bot that gains invulnerability, creative
  abilities, flight or join protection, for example from `/gamemode creative`.
- **Equal damage:** a GameTest checks that the bot loses exactly the same health and armor durability as a
  real player for the same hit, across 24 combinations of damage type (mob, generic, fall, fire, explosion,
  player) and amount.
- **Knockback, fall damage and totems:** GameTests check the bot takes knockback and fall damage, really
  uses up a totem, and dies.
- **Real deaths:** dying drops items according to `keepInventory`, sends the normal death message and
  counts in the stats.
- **Same rules as a player's client:**
  - it attacks only by sending the same packets a client sends, so the server checks them in the same way
  - its crosshair raycast, the 10-tick lockout after a missed swing, its sprint rules and its movement
    input are copied from the decompiled 26.2 client
  - clicks per second, turn speed and simulated ping are capped for every input, whatever decides it.
- **Limited knowledge:** the bot only perceives opponents within its awareness radius and line of sight. Out
  of sight, it only remembers where it last saw them.
- **Human item handling:**
  - switching hotbar slots takes the profile's reaction time and, as in vanilla, resets the attack charge
  - while the inventory screen is open the bot can't move, look around or attack
  - clicks inside the inventory need the screen open first and are rate-limited
  - clicks are swallowed while an item (food, shield, bow) is in use, exactly like the vanilla client
  - food, golden apples and totems are finite and really consumed

## Configuration (`config/sparbot.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch for server owners; `false` removes all bots |
| `commandPermission` | `gamemasters` | `all`, `moderators`, `gamemasters`, `admins` or `owners` |
| `maxBots` | `8` | Limit on bots at the same time |
| `awarenessRadius` | `32` | How far a bot can perceive opponents |
| `autoTarget` / `autoTargetBots` | `true` / `false` | Whether bots fight the nearest player, and whether that includes other bots |
| `maxCps` | `20` | Server-wide click-rate cap on top of each profile |
| `respawnMode` | `auto` | `off` (bot leaves after dying), `manual` or `auto` |
| `autoRespawnDelayTicks` | `60` | Delay before an automatic respawn |
| `kitOnRespawn` | `true` | Re-equip the kit on respawn (start of a new round) |
| `defaultProfile` / `defaultKit` | `intermediate` / `basic_sword` | Used when `/sparbot spawn` omits them |
| `logDecisions` | `false` | Log every tactic change |

## Building

```
./gradlew build
```

This runs the core unit tests and the in-game GameTests on a headless 26.2 server.
`./gradlew runServer` and `./gradlew runClient` start a development game.

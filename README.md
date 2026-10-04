# SparBot

A Fabric mod for **Minecraft Java Edition 26.2** that adds PvP sparring bots. A bot plays like a real
player at a chosen skill level, from Beginner to Pro, and it is **fully mortal**: it takes damage, knockback
and fall damage through the same vanilla code as a human, uses up totems, drops its items and dies for real.

The mod is server-side only. It works in singleplayer (the integrated server), on LAN and on dedicated
servers, and players don't need to install anything.

> Status: milestones 1-7 (sword, combat, NoDebuff, mace, spear, crystal, cart and UHC modes; matches,
> Elo, recording and replay, an in-game menu) plus a duel simulator and **learned melee**: a network
> trained by imitation and self-play, under the same human limits as every bot. Bots fight at a chosen
> skill level and **playstyle**, with any technique switchable per bot. A debug view shows what a bot
> is thinking.

## What a bot can do (milestone 3)

| Mechanic | Behaviour (26.2 rules checked in source) |
|---|---|
| Melee | Times hits to the attack cooldown, crits (falling, not sprinting, more than 90% charge; skilled players jump in at full charge), W-tap, S-tap, strafing, jump reset. Reads your swings to judge your charge: stays just outside your reach while you are charged, feints in and out to draw a swing, rushes in when you miss, combos only while you are recharging. See [docs/sword-tiers.md](docs/sword-tiers.md) for what each level does |
| Learned melee | Optional: a neural network trained in a duel simulator (imitating the scripted pro, then playing itself) drives the movement and clicks, held to the same human limits. Bundled: `sword`, and `uhc` (also decides the shield and the axe). Train your own in game with `/sparbot train uhc <name>`. See [docs/machine-learning.md](docs/machine-learning.md) |
| Reflexes | Busy with a bucket, blocks or crystals, a bot still takes the free hit when you walk into reach, jump-resets the knockback and sidesteps instead of standing still |
| Shield | Block-hits (raises the shield while recharging, lowers it in time to swing); guards against a drawn bow; avoids blocking an axe user when skilled |
| Axe | Switches to the axe when the opponent raises a shield (an axe hit disables it for 5 s); circles a shield if it has no axe |
| Bow / crossbow | Used at 8-40 blocks; full 20-tick draw; crossbow charged in 25 ticks, then fired; aim solved with the real arrow physics and leading moving targets |
| Ender pearl | Closes 16-45 block gaps (lands just short of the opponent) or escapes when low and cornered; aim solved with real pearl physics |
| Golden apples / food | Eats a golden apple when low (not in the opponent's face unless desperate), and food when hungry and safe |
| Totems | After a pop, puts a new totem in the offhand: the swap key from the hotbar, or opening the inventory (taking human time) |
| Retreat | Disengages below the panic threshold (earlier with worn-out armor), eats while running, then re-engages |
| Splash healing (NoDebuff) | Pots down at its own feet below the profile's pot threshold, and double-pots if still low (26.2: the heal scales with distance from the impact) |
| Hotbar refill | When safe, opens the inventory and number-keys pots, pearls and golden apples into empty hotbar slots; gives up if the opponent closes in |
| Buffs | Drinks or splashes Speed, Fire Resistance and Strength again when they wear off and the opponent is not close |
| Spear | Keeps about 3 blocks away and jabs on a full charge (26.2: a jab hits only from 2 to 4.5 blocks); from a gap, sprints in holding a charge (damage 1 + closing speed x the spear's multiplier), but not at an opponent running straight away; falls back to the sword when the opponent gets inside 2 blocks |
| End crystals | Puts obsidian down next to the opponent, a crystal on it, and hits the crystal (26.2: power-6 blast, damage falls off over 12 blocks); only uses spots it can reach to hit (blocks reach 4.5, crystals only 3) and, when skilled, only crystals closer to the opponent than to itself |
| TNT minecarts | Starts a cart only when the opponent is predictable (webbed, standing still or running straight in), puts the rail where they will be by the time the arrow arrives, then finishes the combo: cart on the rail and a quick Flame-bow shot (26.2: a burning arrow makes the cart explode at once, power 4 + 1.5 x arrow speed x a random fraction); never shoots through the opponent; refills carts from the inventory, since they don't stack |
| Lava, water and cobwebs (UHC) | Webbed itself, it waters the web away (flowing water destroys a web in 26.2) and does it again if you drain it, then scoops the water back up; webs you where you stand, or where you'll land after a knock-up; spams lava: pours it onto a webbed opponent's web (it lands on their head) or under their feet, scoops it back a few ticks later and pours again, never leaving it down; puts itself out with water when burning; walks around lava and fire instead of into them |
| Shield stun | Axe hit on a raised shield (disables it for 5 s), then a pre-planned instant switch back to the sword or mace and a hit before the opponent can react; skill decides how often the follow-up is ready |
| Leftover fluids | Sees water and lava source blocks around it: scoops up any it can reach with an empty bucket (its own or yours) when it's safe, blocks up lava next to it, swims out of water pockets |
| Block boost (UHC/SMP) | A few blocks out: puts a block ahead, sprint-jumps onto it and launches off it into the fight |
| Blocks (UHC) | Getting low with a golden apple to eat and the opponent coming in: stops, builds a wall between them (two blocks for a beginner, three wide and two high for a pro), then eats behind it straight away |
| Mace and wind charges | Looks straight down and throws a wind charge at its feet to launch, swaps to the mace, then smashes on the way down (26.2: +4 damage per block fallen up to 3, +2 up to 8, +1 after; the wind charge's own launch causes no fall damage) |

How often each tool is used depends on the profile's item skills, so a Beginner rarely re-totems or
pearls and a Pro almost always does.

Bundled kits for trying it out: `sparbot_combat` (everything above), `sparbot_ranged` (bow focus) and
`sparbot_nodebuff` (splash healing, speed, fire resistance) `sparbot_mace` (mace, wind charges), `sparbot_spear` (spear, sword), `sparbot_crystal` (end crystals, obsidian, totems), `sparbot_cart` (rails, TNT minecarts, Flame bow) and `sparbot_uhc` (lava, water, cobwebs, bow). All are SparBot originals, not copies of any
server's layout.
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
| `/sparbot kit give <kit> [players]` | Equips you (or the players) with a kit, replacing the inventory, like a server's kit menu (not during a match) |
| `/sparbot technique <bot> [<technique> on\|off]` | Shows or switches a bot's techniques: `strafe`, `wtap`, `stap`, `jumpreset`, `crits`, `spacing`, `reading`, `feints`, `combos`, `blockhit`, `shieldstun`, `reflexes` |
| `/sparbot model <bot> <model\|off>` / `models` | Has a bot fight in melee with a learned model (bundled: `sword`, `uhc`), or the scripted melee again |
| `/sparbot train [sword\|uhc] <name> [generations] [from]` / `train status` / `train stop` | Trains a model in the background (imitation, then self-play in the duel simulator) and saves every new best to `config/sparbot/models/<name>.json` |
| `/sparbot kit info <kit>` | Shows a kit's provenance (source, version, confidence, deviations) |
| `/sparbot layout capture <id> <kit>` | Saves where **you** keep that kit's items as a personal layout |
| `/sparbot layout set <bot> <layout>` / `clear <bot>` | Makes a bot copy a layout, or go back to the kit's default |
| `/sparbot style set <bot> <style or mix>` | Changes how the bot fights, e.g. `kiter` or `aggressive_rusher:0.7,kiter:0.3` |
| `/sparbot debug <bot>` / `debug off` | Shows the bot's tactic, top scores, distance, health and charge on **your** action bar, and a spark where its crosshair points |
| `/sparbot arena create <id> <from> <to>` | Saves an arena (and a snapshot of its blocks, used to reset it between rounds) |
| `/sparbot arena spawn <id> a\|b` | Sets a side's spawn to where you stand, facing where you look |
| `/sparbot arena reset <id>` / `arena list` | Restores an arena's blocks and clears drops, arrows and pearls |
| `/sparbot match start <mode> <arena> <a> <b>` | Starts a match; `a` and `b` are bot names or online players |
| `/sparbot match stop <arena>` / `match list` | Stops a match (unrated) / shows running matches |
| `/sparbot modes` | Game modes (`sword_duel`, `combat_duel`, `ranged_duel`, `nodebuff`, `mace_duel`, `spear_duel`, `crystal_duel`, `cart_duel`, `uhc_duel`, `benchmark`, plus your own) |
| `/sparbot benchmark <arena> <profileA> <profileB> <n> [styleA styleB]` | Plays `n` bot-vs-bot matches and rates them |
| `/sparbot record start <name> <players>` / `stop <name>` / `list` | Records a fight (bots or humans) to `config/sparbot/recordings/<name>.spbr`: every tick, each fighter's position, state and inputs |
| `/sparbot replay <name>` / `replay stop <name>` | Plays a recording back where it was recorded, with mannequins standing in for the fighters |
| `/sparbot config` / `config get <key>` / `config set <key> <value>` | Shows or changes a setting (validated, saved to `config/sparbot.json`) |
| `/sparbot menu` (or press **B**) | Opens the SparBot menu, if SparBot is also on your client |
| `/sparbot elo` | The Elo ladder: every bot configuration and player that has played a match |
| `/sparbot list` / `profiles` / `kits` / `style list` / `layout list` | Lists bots, skill profiles, kits, playstyles and layouts |
| `/sparbot stats <bot>` / `info <bot>` | Fight statistics and the current decision trace |
| `/sparbot reload` | Reloads the config, profiles and kits |

## The menu

With SparBot installed on your client too, press **B** (rebindable in Controls) or run `/sparbot menu`:
- **Bots:** every bot with its profile, style and health, and buttons to fight it, kill or respawn it, or remove it.
  **Tech** opens the bot's page: its melee (scripted or a learned model) and an on/off switch per technique
- **Spawn:** name, profile, kit and playstyle, then *Spawn bot*
- **Kits:** every kit with an *Equip* button that gives it to you (replacing your inventory)
- **Settings:** every setting from `config/sparbot.json` except `commandPermission`

Every button just sends a `/sparbot` command, so the server checks permissions and values as if you had
typed it. Without SparBot on the client, everything still works through commands.

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

## Matches

1. Build or pick an area, then run `/sparbot arena create myarena ~-10 ~-1 ~-10 ~10 ~6 ~10`.
2. Stand where each side starts and run `/sparbot arena spawn myarena a`, then `... b`.
3. Run `/sparbot match start sword_duel myarena Bob <you>` (or two bot names).

**How a round works:**
- **Start:** the arena is reset, both sides are teleported to their spawns with a fresh kit, and a
  countdown holds them in place.
- **End:** a death, or falling out of the arena, ends the round.
- **Timeout:** decided by health, or a draw, depending on the mode.

**Your inventory is safe.** It's saved to disk before the kit replaces it, and given back when the match
ends, or on your next join if the server stops mid-match.

**Elo:** every finished match is rated. Bots are rated per profile and playstyle (for example
`bot:pro/wtap_combo`) and humans per name, so `/sparbot elo` tells you how strong each level really is.
Add your own modes in `config/sparbot/modes/*.json` (kit, rounds to win, round length, countdown,
timeout rule, and `"naturalRegeneration": false` for UHC rules).

**UHC rules apply to the two fighters only.** Game rules are server-wide in 26.2, so instead of turning
off `natural_health_regeneration` for everyone, a mode without natural regeneration switches it off for
its two fighters, human and bot alike, until the match ends.

## Playstyles

A playstyle is *how* a bot likes to fight; the skill profile is *how well*. Any profile can be combined
with any style, and styles can be mixed by share.

| Style | Plays like |
|---|---|
| `balanced` | No preferences |
| `aggressive_rusher` | Always pressing; rarely retreats, pearls in, trades hits for crits |
| `wtap_combo` | Sprint-reset combos: lots of W-taps and S-taps, keeps spacing |
| `defensive_shield` | Turtles behind the shield, block-hits, heals early, backs off when hurt |
| `kiter` | Keeps about 12 blocks away and shoots |
| `pearl_aggro` | Closes every gap with ender pearls |
| `no_strafe` | Fights in a straight line, no side-strafing in melee (like many top sword players) |
| `heavy_strafe` | Circles the opponent almost constantly |

A style is JSON with:
- `tacticWeights`: multipliers on the utility AI's tactic scores
- `skillBias`: shifts to profile skills, such as `wTapSkill: +0.3`
- an optional `preferredRange`

Put your own in `config/sparbot/playstyles/`.

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
- `sparbot_*`: SparBot's own kits for each mode (combat, ranged, nodebuff, mace, spear, crystal, cart, uhc).
- `pvphq_sword`, `pvphq_uhc`, `pvphq_smp`, `pvphq_mace`, `pvphq_crystal`: PvPHQ's kits, transcribed from
  kit-editor screenshots. Confidence is **medium**: enchantments aren't visible in the screenshots and some
  items were read by colour. Each kit's `provenance.notes` lists exactly what to confirm.
- `mctiers_sword_recreation`: an **unverified** third-party recreation of the MCTiers-style Sword kit.

See [docs/kit-research.md](docs/kit-research.md).

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
  - it reports its own movement each tick like a client, so it can't sweep while running and its
    projectiles carry its momentum, as a player's do
  - clicks per second, turn speed and simulated ping are capped for every input, whatever decides it.
- **Limited knowledge:** the bot only perceives opponents within its awareness radius and line of sight. Out
  of sight, it only remembers where it last saw them.
- **Human item handling:**
  - switching hotbar slots takes the profile's reaction time and, as in vanilla, resets the attack charge
  - while the inventory screen is open the bot can't move, look around or attack
  - clicks inside the inventory need the screen open first and are rate-limited
  - clicks are swallowed while an item (food, shield, bow) is in use, exactly like the vanilla client
  - blocks and crystals are placed only on the block under its crosshair, within the normal 4.5-block
    reach, through the same packet a client sends
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
| `disabledTechniques` | `""` | Techniques new bots start with switched off, comma-separated (e.g. `strafe,feints`) |
| `defaultModel` | `""` | Learned melee model new bots start with (`""` or `none`: the scripted melee) |
| `logDecisions` | `false` | Log every tactic change |
| `recordMatches` | `false` | Record every match (for replays, and later for Super Mode training) |

## Building

```
./gradlew build
```

This runs the core unit tests and the in-game GameTests on a headless 26.2 server
(`-PgametestFilter=<test id>` runs only some). `./gradlew :sparbot-core:tournament` prints how the skill
levels do against each other in the duel simulator; `./gradlew :sparbot-core:trainSword` trains a model.
`./gradlew runServer` and `./gradlew runClient` start a development game.

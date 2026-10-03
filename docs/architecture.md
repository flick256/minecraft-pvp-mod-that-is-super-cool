# SparBot architecture

## Toolchain (verified 2026-10-03)

Checked against meta.fabricmc.net, maven.fabricmc.net, the Fabric 26.2 blog post and the decompiled 26.2 game.

- Minecraft 26.2 (released 2026-06-16; 26.3 also exists), Java 25 (from Mojang's version manifest).
- 26.x is unobfuscated: Mojang names, no Yarn, `implementation` instead of `modImplementation`.
- Fabric Loom 1.17.21 (plugin id `net.fabricmc.fabric-loom`), Gradle 9.7.1, Loader 0.19.5,
  Fabric API 0.161.0+26.2.

## Modules

```
sparbot-core/        pure Java, no Minecraft/Fabric on the classpath (enforced by NoGameImportsTest)
  profile/           SkillProfile (JSON), presets, validation of human limits
  sense/             Observation, SelfState, TargetState, PerceptionDelay
  act/               Inputs (human inputs only), InputShaper (turn cap, CPS cap, latency)
  aim/               AimController (smoothing, jitter, overshoot)
  brain/             Policy interface, utility AI (DuelBrain + tactics), Movement edge safety
  item/              ItemKind, ItemInfo, InventoryState (what the bot knows about its items)
  kit/               Kit model with provenance, KitValidator, Layout, bundled kits
  style/             Playstyle (tactic weights, skill biases, preferred range), presets, mixing
  match/             GameMode (JSON rules), MatchState (pure round/countdown/best-of engine)
  stats/             FightStats, Elo, EloLadder
src/main/            the Fabric mod (server side)
  bot/               BotPlayer body, BotConnection, ClientEmulator, Perception, MortalityGuard, BotManager
  kit/ profile/      registries; KitApplier validates kits against the live registries
  match/             Arena + ArenaRegistry (snapshots, reset, cleanup), Match, MatchManager, Benchmark,
                     EloStore, PlayerBackup (humans' inventories on disk during matches)
  style/ debug/       PlaystyleRegistry; DebugOverlay (action bar and crosshair particles for one viewer)
  command/ config/ stats/ mixin/
src/gametest/        in-game tests run by `./gradlew build`
```

## How a tick works

1. `BotPlayer.tick()` (the bot is ticked by the world like every player):
   1. Applies knockback the "client" received (see below).
   2. Checks the mortality guard.
   3. `Perception` builds an `Observation`.
   4. The `Policy` decides; `InputShaper` enforces human limits.
   5. `ClientEmulator` sends the resulting packets to the bot's own packet listener.
2. Vanilla `ServerPlayer.tick()` runs.
3. `doTick()` runs vanilla physics, hunger and so on.

The brain sees the opponent with two delays:
- **Decisions** (tactics, reacting to events) use the opponent as it was one reaction time plus half the
  ping ago.
- **Aim tracking** uses only the network delay plus one tick of visual-motor lag, with velocity lead.
  This is how humans track continuously while deciding slowly.

## Things 26.2 does that a fake player must handle

- **Fall damage:** vanilla trusts the *client* with player movement and fall damage, so
  `Entity#move` skips fall damage for client-authoritative players. The bot has no client, so
  `BotPlayer#isClientAuthoritative()` is false. Vanilla's own GameTest mock players do the same.
- **Knockback:** when a player is hit, vanilla sends the knockback to that player's client and resets the
  server-side velocity (`Player#causeExtraKnockback`). `BotConnection` queues that motion packet and the
  bot applies it on its next tick, as a client would. Explosion knockback is already applied server-side,
  so the explosion packet's copy is ignored to avoid applying it twice.
- **Join protection:** players are invulnerable until their client reports it has loaded. The bot sends
  `ServerboundPlayerLoadedPacket` immediately after joining and respawning.
- **Disconnecting:** vanilla only closes a connection after the disconnect packet is delivered, so
  `BotConnection` reports every dropped packet as delivered.
- **Respawning:** vanilla creates a new `ServerPlayer` on respawn. `PlayerListMixin` makes it a new
  `BotPlayer` attached to the same bot.
- **Saved data:** bots never load saved player data (26.2 loads it in the login configuration phase,
  which bots skip), so every spawn starts clean.

## Brain (utility AI)

Every tick each tactic scores itself and the highest score acts. The active tactic gets a +0.05 bonus
so the bot doesn't dither. The bands are in `Scores`: melee 0.6, specialists 0.7 (bow, rod, guard,
pearl), and 0.8+ for survival (retreat, heal, re-totem). "Will this player use X?" is rolled from
the profile's skill once per decision window (`Decision`), not every tick.

A playstyle multiplies each tactic's score by its weight and biases the profile's skills before the
brain is built, so the same brain plays every style.

Tactics: pot, refill, buff (NoDebuff), mace (wind-charge launch and smash), kite (ranged styles only), engage (melee, block-hit, axe, crits, W/S-tap), retreat, heal, retotem, ranged, guard, pearl,
rod, search. `Ballistics` simulates arrows, pearls and fishing hooks in the exact per-tick order vanilla
uses, to find the pitch that lands on target.

## Milestones

| # | Milestone | Status |
|---|---|---|
| M0 | Toolchain, core/mod split, CI | done |
| M1 | Mortal bot body, perception, constrained inputs, skill profiles, sword duel, mortality tests | done |
| M2 | Full kit system: slot-for-slot JSON with any item component, provenance, validator, kit and layout capture, eating, hotbar and offhand swaps, inventory clicks | done |
| M3 | Combat, part 1: shields and axe disabling, shield block-hitting, jump reset, S-tap, bows, crossbows, rods, pearls, totems, golden apples, retreat and heal | done |
| M4 | Playstyles (weighted tactics plus skill biases, mixable at runtime), kite tactic, server-side debug overlay | done |
| M5 | Matches: data-driven game modes, arenas with block snapshots, rounds, cleanup, player inventory backups, bot-vs-bot, Elo ladder and benchmark, training-world design (docs/training-world.md) | done |
| M6 | Mode packs: NoDebuff (done: pot, refill, buff tactics), Mace and wind charges (done), Spear, Crystal, Cart PvP, UHC | in progress |
| M7 | Recording and replay, config GUI | |
| M8 | Super Mode A: record your play, imitation learning, ONNX export, in-game inference | |
| M9 | Super Mode B: headless seeded simulation, league self-play | |
| M10 | Training-world pack | |

## Known limits so far

- **No mining or block placing.** Clicking a block only swings the arm, and right-click never targets
  blocks (eating while looking at a chest doesn't open it).
- **Right-click hand choice is predicted.** A real client decides between main hand and offhand by running
  the item's use logic locally; the bot predicts the same outcome from the item's data components.
- **No spears.** A bot holding a spear never clicks; spear attacks arrive in M6.
- **No dodging.** The bot raises its shield against a drawn bow but doesn't sidestep arrows in flight.
- **One playstyle:** a general duel brain. Playstyles arrive in M4.

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
  kit/               Kit model with provenance, KitValidator, bundled kits
  stats/             FightStats, Elo
src/main/            the Fabric mod (server side)
  bot/               BotPlayer body, BotConnection, ClientEmulator, Perception, MortalityGuard, BotManager
  kit/ profile/      registries; KitApplier validates kits against the live registries
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

## Milestones

| # | Milestone | Status |
|---|---|---|
| M0 | Toolchain, core/mod split, CI | done |
| M1 | Mortal bot body, perception, constrained inputs, skill profiles, sword duel, mortality tests | done |
| M2 | Full kit system: slot-for-slot JSON, provenance, validator, capturing a player's own layout, eating, hotbar swaps | next |
| M3 | Combat, part 1: shields and axe disabling, shield block-hitting, jump reset, S-tap, bows, crossbows, rods, pearls, totems, golden apples, retreat and heal | |
| M4 | Playstyles (weighted tactics, mixable at runtime), client debug overlay | |
| M5 | Matches: game-mode modules, arenas, rounds, cleanup, bot-vs-bot, Elo benchmark, training-world extension points | |
| M6 | Mode packs: NoDebuff, Mace and wind charges, Spear, Crystal, Cart PvP, UHC | |
| M7 | Recording and replay, config GUI | |
| M8 | Super Mode A: record your play, imitation learning, ONNX export, in-game inference | |
| M9 | Super Mode B: headless seeded simulation, league self-play | |
| M10 | Training-world pack | |

## Known limits of milestone 1

- **Melee only.** The bot never uses items: no right-click, food, potions, pearls, shields or bows.
- **No mining.** Clicking a block only swings the arm.
- **No spears.** A bot holding a spear never clicks; spear attacks arrive in M6.
- **One playstyle:** a general duel brain. Playstyles arrive in M4.

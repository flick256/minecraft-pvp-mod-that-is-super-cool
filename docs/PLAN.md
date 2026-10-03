# SparBot: architecture plan and milestones

Status: **proposal, awaiting approval**. No mod code has been written yet.

## 1. Toolchain (verified 2026-10-03)

Read directly from FabricMC's git repositories. The Fabric websites and maven
are blocked by this build environment's network policy, so the values come
from repo tags and branches, not from fabricmc.net/develop.

| Component | Version | Source |
|---|---|---|
| Minecraft | `26.2` | `minecraft_version` on fabric-api branch `26.2` |
| Fabric API | `0.161.0+26.2` (latest 26.2 tag) | fabric-api git tags |
| Fabric Loader | `0.19.5` (latest) | fabric-loader git tags |
| Fabric Loom | `1.18.2` (latest stable), plugin id `net.fabricmc.fabric-loom` | fabric-loom tags, fabric-example-mod |
| Gradle | `9.7.1` | fabric-example-mod wrapper |
| Java | 25 (`options.release = 25`) | fabric-example-mod |
| Mappings | Mojang official (26.x ships unobfuscated) | Fabric porting docs |

Note: **Minecraft 26.3 is already out.** fabric-example-mod HEAD targets 26.3
with Fabric API `0.161.0+26.3`. This plan follows the requested 26.2.

### 26.2 API facts confirmed from source

- `ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)`
- `ServerGamePacketListenerImpl(MinecraftServer, Connection, ServerPlayer, CommonListenerCookie)`
- `CommonListenerCookie(GameProfile, int latency, ClientInformation, boolean transferred)`
  and `CommonListenerCookie.createInitial(GameProfile, boolean)`
- `PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)`
- `PlayerList.respawn(ServerPlayer, boolean alive, Entity.RemovalReason)`
- `Entity.isInvulnerableTo(ServerLevel, DamageSource)`
- Entity type constants live in `net.minecraft.world.entity.EntityTypes`.
  This is a 26.x rename, so 1.21.x names cannot be trusted.
- GameTests: `net.fabricmc.fabric.api.gametest.v1.@GameTest` (with `maxTicks`,
  `structure`, `environment`) on methods taking `GameTestHelper`.
- **Fabric API's `FakePlayer` cannot be used.** It returns `true` from
  `isInvulnerableTo`, and its `tick()` does nothing. SparBot subclasses
  `ServerPlayer` directly and overrides no damage, death or ability methods.
- Carpet (reference only, no dependency) registers its fake player through
  `placeNewPlayer` with a server-bound fake `Connection`. It drives movement
  by running the vanilla living-entity tick from `tick()`, because no client
  is there to simulate physics.

### Still to verify from decompiled 26.2 code (first task of M0)

None of this can be checked until Minecraft jars can be downloaded:
`Player.attack` (cooldown scaling, crit and sprint-knockback conditions),
`LivingEntity.hurtServer` / `actuallyHurt` / armor and toughness math, i-frames
(`invulnerableTime`), shield `blocks_attacks` component and axe disabling, mace
smash formula, spear charge/lunge, wind charge, the `GameTestHelper` mock-player
helpers, the permission model (fabric-permission-api-v1 exists in 26.2), and the
Loom 1.18 GameTest DSL. Findings go into `docs/vanilla-notes-26.2.md`, citing
class and method for each, before any combat logic depends on them.

## 2. Project layout

```
sparbot/
├─ core/            pure Java, NO net.minecraft / net.fabricmc imports
│  ├─ math/         vectors, angles, distributions, seeded RNG
│  ├─ perception/   Observation snapshot model (immutable records)
│  ├─ action/       InputFrame, HumanInputLimiter, LatencyQueue
│  ├─ motor/        aim model, click scheduler, movement intent → keys
│  ├─ skill/        SkillProfile + JSON codec + presets
│  ├─ decision/     Policy interface, utility AI, behavior trees, tactics
│  ├─ style/        Playstyle = weighted tactic set; runtime mixing
│  ├─ kit/          Kit model, provenance, validator (spec-level)
│  ├─ match/        round/match state machines, stats, Elo
│  └─ record/       replay + dataset format (shared with ML)
├─ mod/             Fabric mod (server side + main entrypoint)
│  ├─ bot/          BotPlayer, BotConnection, BotManager, BotGuard
│  ├─ bridge/       Observation builder, InputFrame executor
│  ├─ kit/          Kit → ItemStack application, registry validation
│  ├─ mode/         GameMode modules (duel, nodebuff, crystal, …)
│  ├─ arena/        arena regions, reset, cleanup
│  ├─ command/      /sparbot …
│  └─ config/       sparbot.json (server-owner restrictions)
├─ mod/src/client/  debug overlay, GUI (optional, client only)
├─ mod/src/gametest/ GameTests
└─ training/        Python (Super Mode), added in M8
```

`core` is a plain Gradle `java-library`. `mod` depends on it and bundles it
with Loom `include`. An **ArchUnit test in `core` fails the build** if any
class imports `net.minecraft..`, `net.fabricmc..` or `com.mojang..`.

## 3. Runtime pipeline (one bot, one server tick)

```
 world ──► ObservationBuilder ──► PerceptionDelay ──► Policy ──► Motor ──► HumanInputLimiter ──► LatencyQueue ──► InputExecutor ──► vanilla
 (mod)     (radius + LOS filter)  (reaction time,    (scripted   (aim,     (rotation speed,      (simulated       (mod: same code
                                   ping/2)            or ML)      clicks)   CPS, reach intent)    ping/2)          paths a client hits)
```

1. **Body: `BotPlayer extends ServerPlayer`.** Joined through
   `PlayerList.placeNewPlayer` with a fake connection, so it shows in the tab
   list, receives death messages, and drops items by gamerule. Survival mode
   only. `tick()` calls `super.tick()` and then the vanilla living-entity
   movement tick, the step a real client's local simulation performs.
2. **Perception.** `ObservationBuilder` copies only what a player could see
   into an immutable `Observation`: entities within `awarenessRadius` with
   line of sight (or recently seen, with a decaying memory), their position,
   velocity, held items, visible armor and potion particles, own full state,
   projectiles, crystals, TNT, and a local voxel grid. No health of others
   unless shown (configurable: scoreboard health display on or off).
   `PerceptionDelay` makes the policy act on observations aged by the sampled
   reaction time plus half the simulated ping.
3. **Action layer.** `InputFrame` = {yaw delta, pitch delta, forward/back/
   left/right, jump, sneak, sprint, attack, use, hotbar slot, swap-offhand,
   drop}. `HumanInputLimiter` clamps rotation speed and acceleration, enforces
   minimum click interval / CPS cap, and refuses attacks outside the profile's
   reach intent. `LatencyQueue` delays frames by half the ping. The
   `InputExecutor` applies frames through **vanilla's own server handlers**
   (`handleInteract`, `handlePlayerAction`, `handleUseItem`,
   `handleSetCarriedItem`), feeding them the same packet objects a real client
   sends. Vanilla's reach, cooldown and validity checks then apply unchanged.
   Movement keys go into the player's input state and are simulated by the
   vanilla physics tick.
4. **Decision.** `interface Policy { InputIntent decide(Observation, BotMemory); }`.
   The scripted policy runs a utility AI: each `Tactic` scores itself, the
   playstyle weights multiply the scores, and hysteresis stops thrashing. The
   winning tactic runs its behavior tree. An ML policy (M8) implements the
   same interface and goes through the same Motor, Limiter and Latency stages,
   so a model cannot exceed human limits either. Scores are exported for the
   debug overlay.
5. **Skill profile (JSON).** Reaction time (log-normal: median, sigma, floor),
   aim (spring tracking gain, max deg/s, jitter, overshoot, flick accuracy),
   CPS (mean, sd, burst chance), reach intent (mean, sd: low skill swings
   from too far), crit timing accuracy, W-tap/S-tap/jump-reset/shield-timing
   success rates, mistake rate, panic threshold (health → erratic/retreat),
   simulated ping (mean, jitter), resource discipline (when to heal or pearl).
   Presets: `beginner`, `novice`, `intermediate`, `advanced`, `expert`, `pro`,
   plus user files in `config/sparbot/profiles/*.json`.
6. **Playstyle (JSON).** Tactic weights plus parameters. Ships `rusher`,
   `wtap_combo`, `defensive_shield`, `kiter`, `pearl_aggro`, `crystal_sweat`,
   `mace_spear_wind`, `cart`, `balanced`. Profile and playstyle are chosen
   independently and can be swapped live (`/sparbot set <bot> style …`).

## 4. Mortality and fairness enforcement

- `BotPlayer` overrides **none** of: `isInvulnerableTo`, `isInvulnerable`,
  `hurtServer`, `actuallyHurt`, `knockback`, `die`, `heal`, `isSpectator`,
  `isCreative`, `onUpdateAbilities`, `setInvulnerable`, `getAbilities`.
  **Unit test:** reflection over `BotPlayer` and every superclass up to
  `ServerPlayer` fails if any of these is declared below `ServerPlayer`.
- **No damage-related mixins.** A test parses `sparbot.mixins.json` and fails
  if any mixin targets `LivingEntity`, `Player`, `ServerPlayer`,
  `DamageSource*` or `CombatRules`.
- **`BotGuard` (runtime, every tick):** asserts `abilities.invulnerable`,
  `mayfly`, `flying` and `instabuild` are false, `isInvulnerable()` is false,
  game mode is survival or adventure, and no Resistance V+ effect was added by
  SparBot. On violation it logs an error, despawns the bot, and refuses to
  respawn it until an admin clears it.
- **GameTests.** (a) A bot and a vanilla mock `ServerPlayer` wear identical
  armor and are hit with identical `DamageSource`s (player melee, arrow,
  explosion, fall, fire, lava, drowning, void, starvation, mace). Remaining
  health must match exactly. (b) A bot at 1 HP dies, drops its inventory when
  `keepInventory=false` and keeps it when `true`, and its death message is
  broadcast. (c) A totem in the offhand is consumed on lethal damage.
  (d) The bot gets the same i-frames and knockback as the mock player.
- **Respawn modes** `off | manual | auto`. Auto respawns through
  `PlayerList.respawn` (the vanilla path) and then re-applies the kit. A death
  is always recorded as a loss.
- **Training wheels** (off by default, labeled `TRAINING WHEELS` in config and
  `/sparbot info`): only lowers the bot's *outgoing* damage, through a named
  vanilla attribute modifier on `attack_damage`. It never touches the damage
  the bot takes, and never sets invulnerability.

## 5. Kits

```json
{
  "id": "mcpvp_sword",
  "provenance": { "source": "MCPVP", "mode": "Sword", "originalVersion": "1.8.9",
                  "confidence": "UNVERIFIED | USER_SUPPLIED | VERIFIED | ORIGINAL",
                  "evidence": "screenshot 2026-10-03 from user", "deviations": ["…"] },
  "armor":   { "head": {...}, "chest": {...}, "legs": {...}, "feet": {...} },
  "offhand": { "item": "minecraft:shield" },
  "slots":   { "0": { "item": "minecraft:diamond_sword", "count": 1,
                      "enchantments": { "minecraft:sharpness": 1 } }, "...": {} },
  "effects": [ { "id": "minecraft:speed", "amplifier": 0, "duration": -1 } ],
  "hotbarLayoutOverrides": { "player-uuid": { "0": 3, "3": 0 } }
}
```

- The **core validator** checks slot ranges (0–35, armor, offhand), stack
  counts, duplicate slots, that provenance is present, and that every
  deviation is documented when `originalVersion` ≠ 26.2. The **mod
  validator** also checks item, enchantment and effect IDs against the 26.2
  registries, enchantment compatibility, and level caps.
- Real-server kits are **not shipped until you provide the layouts**. Until
  then only `ORIGINAL` kits (clearly labeled as mine) are included.
- `/sparbot kit capture <name>` saves your current inventory as a custom
  layout; `/sparbot kit layout` remaps an existing kit onto your hotbar order.

## 6. Milestones

Each milestone ends with: `./gradlew build` green, unit tests and GameTests
green, and a manual in-game check described in the milestone notes.

| # | Milestone | Done when |
|---|---|---|
| **M0** | Toolchain + skeleton. Gradle multi-project, wrapper 9.7.1, Loom 1.18.2, core + ArchUnit test, empty mod that loads, GitHub Actions CI (build + unit + GameTest). Decompile 26.2 and write `vanilla-notes-26.2.md`. | CI green; mod loads on a 26.2 dedicated server. |
| **M1** | **Basic sword duel at a chosen skill level.** BotPlayer spawn/kill/respawn, perception, action layer with limits and latency, aim/click motor, 6 skill presets + custom JSON, duel tactics (approach, sprint-reset hits on cooldown, crits, strafe, W-tap, retreat at low HP), one ORIGINAL sword kit, config incl. server-owner switches, permissions, all mortality tests. | `/sparbot spawn Bob skill=intermediate kit=sword` fights you; the bot can lose and die; GameTests prove damage parity. |
| M2 | Full melee: shields + axe disables, jump-reset, S-tap, combos, eating/gapples, totems, armor durability awareness, splash/drink potions (NoDebuff). Match runner (bot-vs-player, bot-vs-bot, best-of-N), per-fight stats. | Bot-vs-bot match produces a stats report. |
| M3 | Kit system complete (schema, validators, provenance, capture/layout), mode modules: Sword, Axe/Shield, NoDebuff, UHC-lite; arenas with reset + cleanup. | Every shipped kit passes validator tests against its spec. |
| M4 | Ranged and mobility: bow/crossbow lead prediction, rod, pearls, trident, wind charges, mace smash, spear, cobwebs, water/lava clutches, block placement, elytra + fireworks. | GameTests per mechanic. |
| M5 | Crystal PvP (crystals, obsidian, anchors, totem cycling, self-damage awareness), Cart PvP (TNT minecarts, rails, bows). | Bot wins/loses crystal fights legitimately; can blow itself up at low skill. |
| M6 | All playstyles + runtime mixing, client debug overlay, config GUI, replay record/playback, Elo benchmark ladder (headless bot-vs-bot). | Elo table per preset published by a gradle task. |
| M7 | UHC extended (gathering, early game), Money-SMP kit fights, modes defined fully in config. | Config-only mode runs end to end. |
| M8 | Super Mode A: record your play into the dataset format, Python imitation training, ONNX export, async in-game inference with per-tick budget and scripted fallback. | Model trained on a recording runs in-game. |
| M9 | Super Mode B: headless sped-up multi-bot sim (vanilla `/tick sprint`), seeded bot RNG, league self-play, skill-conditioned policies, shaped rewards. | Reproducible training run from a seed. |
| M10 | Training-world pack: arena/spawn markers, drills (aim, bridge, clutch, parkour), map format. Bot still works in any world. | Drill world loads and scores you. |

Reproducibility caveat for M9: SparBot's own randomness is fully seeded.
Minecraft's world simulation is not perfectly deterministic (entity RNG, chunk
loading), so simulation runs will be close but not bit-identical.

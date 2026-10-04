# Machine learning in SparBot

SparBot's bots can fight in melee with a small neural network instead of the hand-written rules. The
network is trained by playing, so it can find timing and spacing nobody wrote down, but it plays
under exactly the same human limits as every other bot: the same reaction delay, aim model, click
limit and ping. It doesn't get to see or do anything a player couldn't.

## How it fits together

```
 Minecraft (real server)                 Duel simulator (sparbot-core, no Minecraft)
 ─────────────────────────               ────────────────────────────────────────────
 Perception ─► Observation ─┐         ┌─ SimFighter: 26.2 movement + melee rules
                            ▼         ▼
                       DuelBrain (same code in both)
                  reaction delay ─► tactics ─► reflexes
                       melee: EngageTactic (scripted)
                           or LearnedMeleeTactic (network)
                            │
                            ▼
                     InputShaper (ping, click and turn limits)
                            │
 ClientEmulator ◄───────────┴──────────────► SimFighter
 (real packets)                               (same rules, ~270 fights/s on 4 cores)
```

- **Duel simulator** (`core/sim`): vanilla 26.2 flat-ground movement and melee re-implemented from the
  decompiled game code (`LivingEntity#travelInAir`, `jumpFromGround`, `knockback`, `hurtServer`,
  `Player#attack`, `CombatRules`). Unit tests check it against walk speed (4.317 blocks/s), sprint speed
  (5.612 blocks/s), jump height (1.2522) and armored sword damage. Policies see the same `Observation`
  and go through the same `InputShaper` as in game.
- **The network** (`core/ml/Mlp`): 24 inputs, two hidden layers of 32 (tanh), 9 outputs, 2,153
  weights. One decision takes about a microsecond.
- **What it sees** (`MeleeFeatures`): distance, the opponent's and its own movement towards and across,
  its own charge and the ticks until it is ready, the opponent's charge as judged from their swings,
  on ground, sprinting, falling, hurt timers, health, whether the opponent faces it, whether the
  crosshair is on them and whether they are in reach. All of it delayed like the scripted brain's view.
- **What it decides**: forward/back, left/right, jump, sprint, click. Aiming stays with the profile's
  aim model, so a learned bot aims no better than its profile allows.

## Training: imitation, then self-play

1. **Imitation** (`Imitation`): the scripted pro fights 400 simulator fights; its 80,000-odd melee
   decisions become examples, and the network learns to press the same keys (cross-entropy, Adam).
   About 15 seconds. This gives a player that already knows the basics, instead of one that has to
   discover walking forward by chance.
2. **Self-play** (`SelfPlay`): evolution strategies (Salimans et al., 2017). Each generation tries 32
   small random changes to the weights in mirrored pairs (+change and -change), plays every changed
   network 12 fights against the same opponents, and moves the weights towards the changes that won
   (centred-rank fitness, Adam). The opponents are a league: the scripted advanced and pro bots plus
   snapshots of the network from earlier generations, so it can't win with one trick that only beats
   one opponent. About 0.3 seconds a generation on 4 cores.
3. Every 10 generations the network plays 200 fights against the scripted pro; the best version is kept.

Why evolution strategies: no gradients through the game are needed, the simulator stays simple, every
candidate runs on its own core, and the networks are small enough that a few hundred candidates
cover them. The whole loop needs no GPU.

## Results so far

The bundled `sword` model: imitation, then 300 generations (under 3 minutes on 4 cores).

| | Simulator | Real game (GameTests) |
|---|---|---|
| Learned (pro limits) vs scripted pro | 0.97 | 6 of 8 fights won |

The same network under each level's limits, against the scripted bot of that level (simulator):

| Limits | vs scripted same level | vs scripted pro |
|---|---|---|
| beginner | 0.55 | 0.00 |
| casual | 0.61 | 0.00 |
| intermediate | 0.53 | 0.05 |
| advanced | 0.61 | 0.58 |
| pro | 0.96 | 0.97 |

The human limits dominate: under a beginner's reaction time and aim, the network plays like a slightly
better beginner. So one network covers every difficulty.

The gap between the simulator and the game (0.97 against 75%) has known causes: the simulator has no
natural regeneration, no block collisions other than the arena walls, and treats knockback arriving a
tick later exactly, where the real connection varies. Those can be added to the simulator as they turn
out to matter.

## Training your own

In game (no setup needed), as an operator:

```
/sparbot train mysword 300          # imitation, then 300 generations of self-play
/sparbot train status               # how it's going
/sparbot train stop                 # stop early; the best so far is kept
/sparbot train mysword2 300 mysword # continue from an existing model
/sparbot model Bob mysword          # Bob now fights with it (or use the menu: Tech, melee)
```

It runs in the background on all cores but one, saves every new best to
`config/sparbot/models/<name>.json`, and the model can be used straight away. From a checkout:

```
./gradlew :sparbot-core:trainSword -Pgenerations=300 -Pout=sword.json [-Pstart=old.json]
```

then copy the file to `config/sparbot/models/` and run `/sparbot reload`.

## Where this goes next

- **Learning from your fights.** The recorder already writes every tick of a fight (positions, state
  and inputs) to `config/sparbot/recordings`. The imitation step can learn from those instead of (or as
  well as) the scripted pro, so a bot picks up a real player's habits; self-play then sharpens them.
- **Per-level models.** Snapshots from different stages of training make natural opponents in between
  the levels.
- **More modes.** The same loop works for any mode the simulator can play: crystal, UHC and mace need
  blocks, items and explosions added to it.
- **A better simulator.** Regeneration, terrain and connection jitter, measured against recorded real
  fights.

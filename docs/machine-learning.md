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
- **The network** (`core/ml/Mlp`): two hidden layers of 32 (tanh). The `sword` network has 24 inputs and
  9 outputs (2,153 weights); the `uhc` network has 32 inputs and 11 outputs (2,475 weights). One decision
  takes about a microsecond.
- **Items in the simulator** (for UHC): hotbar switching (resets the charge), the shield (blocks hits from
  within 90 degrees of where its holder faces once it has been up 5 ticks; an axe hit disables it for
  5 s), golden apples (32 ticks; Absorption I and Regeneration II), slower movement while using an item,
  Protection armor and no natural regeneration.
- **What it sees** (`MeleeFeatures`): distance, the opponent's and its own movement towards and across,
  its own charge and the ticks until it is ready, the opponent's charge as judged from their swings,
  on ground, sprinting, falling, hurt timers, health, whether the opponent faces it, whether the
  crosshair is on them and whether they are in reach. All of it delayed like the scripted brain's view.
- **What the UHC network also sees**: whether the opponent is blocking or eating, whether they hold an
  axe, its own shield (ready or on cooldown), its absorption, whether the opponent's shield is disabled.
- **What it decides**: forward/back, left/right, jump, sprint, click (UHC: also shield up, and sword or
  axe). Aiming stays with the profile's aim model, so a learned bot aims no better than its profile
  allows. Eating, water, lava, webs and walls stay with the scripted tactics.
- **Where it hands over**: when either fighter is webbed, in water or lava, or the bot is burning, the
  scripted melee takes over for those ticks. The learned melee never clicks when a block is under the
  crosshair before the opponent, and it obeys the technique switches (strafe, block-hit).

## The UHC simulator

For UHC the simulator is a small voxel world (`SimWorld`): a walled 32 x 32 stone arena (sometimes with
uneven ground), with vanilla 26.2's rules re-done from the decompiled code:

- water and lava flow (`FlowingFluid`): water 7 blocks, a step every 5 ticks; overworld lava 3 blocks,
  every 30 ticks; two water sources make a third; lava next to water turns to obsidian (a source) or
  cobblestone (flowing), and lava flowing down into water makes stone;
- cobwebs (no collision, so fluids wash them away; a body in one moves at a quarter speed), lava (4
  damage and 15 s of fire), fire (1 damage a second, water puts it out), fall damage, swimming;
- the whole kit from the mod's kit files: buckets (a filled one empties next to the block the crosshair
  is on, an empty one takes a source), blocks and webs placed through the crosshair, the bow and arrows,
  golden apples, the shield and axe, breaking a cobweb with the sword, inventory swaps;
- what each bot sees is built the way the mod's Perception builds it in game (line of sight, nearby
  sources and webs), so the same scripted and learned brains run in it unchanged.

It was checked against the game with the parity GameTests (`UhcParityGameTests`): the same scripted
pros fighting under UHC rules in a 32 x 32 arena, in game and in the simulator.

| Per bot per fight | Game | Simulator |
|---|---|---|
| Fight length (ticks) | 1607 | 1673 |
| Time in melee / with the bow | 48% / 7.9% | 50% / 8.2% |
| Hits / swings / crits | 20 / 31 / 6.1 | 21 / 30 / 5.9 |
| Golden apples eaten | 5.1 | 5.5 |
| Webs placed, water pours, scoops | 5.8, 6.1, 7.9 | 5.8, 6.4, 7.6 |
| Getting out of a web (ticks) | 7-15 | 8-16 |

The simulator found real bugs in the scripted UHC play this way (see the changelog for 0.4.0), and it
runs about 30 full UHC fights a second on 4 cores.

## The UHC brain

The `uhc` model is a whole UHC brain: the melee network plus a **tactic chooser** (`LearnedTactics`), a
second network (90 inputs, two hidden layers of 32, 13 outputs) that shifts each UHC tactic's score up or
down by up to 0.3 every tick: engage, retreat, heal, ranged, guard, refill, lava, web, water, wall,
cleanup, boost, breakweb. It sees the fight as the brain sees it (distance, both players' health, webs,
water, fire, what the opponent holds or uses), its own kit, what lies around, the scripted scores and
which tactic is running. It can't make a tactic possible that isn't, and the tactics themselves still do
the aiming, timing and hands under the profile's limits. With all-zero outputs it plays exactly like the
scripted brain, which is where training starts.

Training (`UhcBrainTraining`) is evolution strategies over both networks together, in full-kit fights:
the scripted pro, the pro with the learned melee, and snapshots of the brain itself.

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
3. **Shaping**: winning is what counts, but the score also takes a little off for strafing more than
   30% of the time (constant circling beats bots and looks dizzy to a person) and adds a little per crit.
4. Every 10 generations the network plays 200 fights against the scripted pro; the best version is kept.

Why evolution strategies: no gradients through the game are needed, the simulator stays simple, every
candidate runs on its own core, and the networks are small enough that a few hundred candidates
cover them. The whole loop needs no GPU.

## Results so far

| Model | Training (4 cores) | Simulator vs scripted pro | Real game vs scripted pro |
|---|---|---|---|
| `sword` (0.3.0) | imitation + 400 generations, about 4 min | 0.98, strafing 7% of the time | 8 of 8 fights won |
| `sword` (0.2.0) | imitation + 300 generations | 0.97, strafing 45% of the time | 6 of 8 |
| `uhc` (0.4.0, whole brain) | the 0.3.0 melee + 60 generations in full-kit fights, about 11 min | 0.95 in full-kit UHC | 11 of 12 fights won |
| `uhc` (0.3.0, melee only) | imitation + 300 generations, about 8 min | 0.99 in flat melee | about even in full-kit UHC |

What the UHC brain found: against the scripted pro it stopped using lava, webs, walls and boosts and
wins by pressure (in melee 73% of the time, the bow at range, healing, getting out of webs and fire
quickly). Every player carries water and puts fire out within a couple of ticks, so in these fights the
scripted lava and web plays cost more time than they win. The scripted pro still uses all of it, so
practise against both.

What the network found on its own: in sword it barely strafes and wins with sprint-knockback hits
from the edge of reach (about 10 of its 11 hits a fight); extra reward for crits hardly changed that,
because against a strong opponent the sprint hits are simply better. In UHC it crits a lot (about 20
crits a fight).

The same network under each level's limits, against the scripted bot of that level (sword, 0.2.0
model):

| Limits | vs scripted same level | vs scripted pro |
|---|---|---|
| beginner | 0.55 | 0.00 |
| casual | 0.61 | 0.00 |
| intermediate | 0.53 | 0.05 |
| advanced | 0.61 | 0.58 |
| pro | 0.96 | 0.97 |

The human limits dominate: under a beginner's reaction time and aim, the network plays like a slightly
better beginner. So one network covers every difficulty.

Bots don't learn during a fight: a model is fixed while it plays, and learning happens when you train
(that is also why it can't be fooled into a bad habit mid-game).

## Running it on your own computer

Training is plain Java (JDK 25): no GPU, no Python, nothing to install beyond what Minecraft already
needs. It runs on Windows, Linux and macOS, including Apple silicon natively. It uses every core it is
given, and on a machine with more cores it also tries more changes per generation (4 per core), so a
bigger machine learns faster and better, not only sooner.

| Machine | Cores | Roughly (UHC brain, 100 generations) |
|---|---|---|
| This build machine | 4 | 18 minutes |
| A gaming PC | 8-16 | 2-4x faster |
| MacBook Pro M5 | 10 (4 performance, 6 efficiency) | about 2x faster |

For "a ton more practice", run longer (`1000` or more generations) or continue from a model you
already have; every best is saved as it goes, so you can stop at any time.

## Training your own

In game (no setup needed), as an operator:

```
/sparbot train uhc myuhc 300          # a UHC brain: from the bundled one, 300 generations of full-kit self-play
/sparbot train uhcmelee mymelee 300   # UHC melee only (flat fights)
/sparbot train sword mysword 300      # a sword model
/sparbot train status                 # how it's going
/sparbot train stop                   # stop early; the best so far is kept
/sparbot train uhc myuhc2 1000 myuhc  # continue from an existing model
/sparbot model Bob myuhc              # Bob now fights with it (or use the menu: Tech, melee)
```

It runs in the background on all cores but one, saves every new best to
`config/sparbot/models/<name>.json`, and the model can be used straight away. From a checkout:

```
./gradlew :sparbot-core:trainUhc -Pgenerations=300 -Pout=uhc.json [-Pstart=old.json]
./gradlew :sparbot-core:uhcLab -Pfights=200 -Pa=pro -Pb=pro     # full-kit fights with statistics
./gradlew :sparbot-core:trainSword -Pgenerations=300 -Pout=sword.json
```

then copy the file to `config/sparbot/models/` and run `/sparbot reload`.

## Where this goes next

- **Learning from your fights.** The recorder already writes every tick of a fight (positions, state
  and inputs) to `config/sparbot/recordings`. The imitation step can learn from those instead of (or as
  well as) the scripted pro, so a bot picks up a real player's habits; self-play then sharpens them.
- **Per-level models.** Snapshots from different stages of training make natural opponents in between
  the levels.
- **Stronger utility.** Web-then-lava and web-then-crit combos executed well enough that the brain
  chooses them, and an opponent league with more varied UHC players.
- **More modes.** The same loop works for any mode the simulator can play: crystal and mace need
  explosions and wind charges added to it.
- **A better simulator.** Regeneration, terrain and connection jitter, measured against recorded real
  fights.

# Sword: what each skill level does

Sword is the most technical mode. In 26.2 every swing resets the attacker's charge (12.5 ticks for a
sword), there is no hit stun, a sprinting full-charge hit knocks back extra and crits need a full
charge while falling and not sprinting. So a sword fight is about timing and distance, and the levels
differ in how much of that the bot understands, not only in aim and reaction time.

The numbers are in `sparbot-core/src/main/resources/sparbot/profiles/*.json`. Every technique can be
switched off per bot (`/sparbot technique <bot> <technique> off`, or the Tech button in the menu).

| | Beginner | Casual | Intermediate | Advanced | Pro |
|---|---|---|---|---|---|
| Reaction | 380 ms | 300 ms | 240 ms | 200 ms | 165 ms |
| Aim | slow, shaky, no lead | | | | fast, steady, leads movement |
| Clicking | spams (5 CPS, ignores the charge) | mostly spams | waits for a fair charge | waits for a full charge | always a full charge |
| Reach judgement | swings from too far | | | | exact |
| Strafing | barely | | | | some, and sideways out of a charged opponent's reach |
| W-tap / S-tap after a hit | no | rarely | sometimes | usually | almost always |
| Combos (distance after a hit) | no | rarely | sometimes | usually | almost always, unless the opponent is still charged |
| Reading swings | no | rarely | sometimes | usually | almost always |
| Spacing out of a charged opponent's reach | no | | sometimes | usually | almost always |
| Punishing a missed swing | no | | sometimes | usually | almost always |
| Feints | no | no | rarely | sometimes | often |
| Crits | rare, badly timed | occasional, badly timed | sometimes, timed | often, timed | very often: jumps in at full charge |
| Jump reset | no | rarely | sometimes | usually | almost always |

What the bot does with these (`SwordPlan` and `EngageTactic`):

- **Ready to hit**: sprint in and hit. A practised player jumps in at full charge from a jump away, so
  the hit lands as a crit (1.5x damage) and a trade goes their way.
- **Recharging after landing a hit**: W-tap or S-tap so the opponent flies to the edge of reach and the
  next hit lands there at full charge. Only while the opponent is recharging too: there is no hit stun,
  so chasing a charged opponent walks into their hit.
- **Recharging while the opponent is charged** (judged from how long ago their arm last swung): stay
  just outside their reach, sidestep, and sometimes step in and straight back out to draw a swing.
- **The opponent swung and nothing hit the bot**: they missed, and their charge is gone. Rush in.
- **Hit while busy** with buckets, blocks or crystals: take the free hit, jump-reset, sidestep (reflexes).

## Measuring it

`Tournament` (in `sparbot-core`, package `sim`) runs the levels against each other in the duel
simulator: about 270 full fights a second on 4 cores. With 400 fights per pair (score = share of
fights won, timeouts scored by remaining health):

```
                 beginner       casual intermediate     advanced          pro
beginner             0.50         0.17         0.01         0.00         0.00
casual               0.83         0.50         0.11         0.01         0.00
intermediate         0.99         0.90         0.50         0.10         0.08
advanced             1.00         0.99         0.90         0.50         0.32
pro                  1.00         1.00         0.92         0.68         0.50
```

(Before the revamp the steps were steeper: intermediate beat casual 0.98 and advanced beat
intermediate 0.98.)

Bot against bot the gaps look steep: two scripted players of the same level trade almost every hit,
so small edges in reaction and aim decide nearly every fight. Against a person the levels feel
closer. The reading, spacing and feints mostly pay off against opponents who make human mistakes
(swinging early, walking into reach while charged), which bots of the same level rarely do. This is
also why the next step is a learned policy trained against itself in the simulator (see
`docs/machine-learning.md`).

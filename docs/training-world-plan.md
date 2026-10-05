# Plan: a PvP training world that grows with the bot

Goal: a world that makes *you* better, built so that you and the bot are measured, trained and
matched the same way. Every number the bot is trained and tested on (hit distance, charge, utility
that lands, escape times, health swing after a play) is also what your drills score, so "beat the
Demon bot" and "pass the drill" mean the same thing, and what you do in the world feeds straight back
into how the bot trains.

## Principles

1. **One scoreboard.** The fight stats the bot already reports (hits from how far and how many beyond
   2.9, full-charge share, crits, webs and lava that caught, arrows that hit, time stuck in webs or on
   fire, health swing in the 60 ticks after a play) are recorded for you too, from the same code
   (`StatsTracker`, the recorder). Drills, the hub board and the weekly report all read them.
2. **The bot's tactics are the curriculum.** Every scripted tactic is a skill with a name, a trigger
   and a pass mark (lava on a webbed or eating opponent, the block line going back, inst-carts, the
   anchor-glowstone-totem chain, knocking out a cart, punish shots). A drill isolates one tactic: the
   bot does it to you, then you do it to the bot, with the same timing windows it uses.
3. **Same arenas as the simulator.** Flat and uneven UHC arenas, the cart lane and the crystal pit
   are built to the simulator's arena specs, so what a model learns there transfers, and parity tests
   keep the two in line.
4. **Your kits, your layouts.** Every station equips you with the mode's kit in your saved layout
   (/sparbot layout save), and the bot with the same kit.

## 1. The hub

A spawn hub with a door per discipline: Sword, UHC, Crystal and Anchor, Cart, Mace, Movement. Your
tier per discipline (Beginner to Demon), drill medals and Elo against bots show on a board and in a
Training tab in the menu. Doors to the next tier open when you pass its gate fight.

## 2. Stations

Each station spawns a bot with only the techniques that matter switched on (`/sparbot technique`),
runs a timed round, and scores you on one thing.

| Station | The bot | You're scored on |
|---|---|---|
| Charge and reach | Stands, then strafes | Full-charge share; hits from within 2.9 (no swinging at air) |
| Combos and spacing | Walks in, swings only in reach | Longest combo, hits landed vs taken while recharging |
| Crits | Stationary, then moving | Crits per minute, mistimed jumps |
| Anti-combo | Combos you | Jump resets, hits taken in a row |
| Shield and axe | Block-hits | Shields disabled, stun follow-ups within 4 ticks |
| Web and water | Webs you, then pours lava on you | Time to water or cut out (the bot's: about 0.5 s) |
| Lava | Gets webbed, eats apples | Lava that caught, scooped, never left down |
| Blocks | Rushes you while you're low | Blocks per second in a back-line, heal finished behind it |
| Bow | Eats, gets webbed, walks in | Punish shots that land at 4.5-8 blocks |
| Golden heads | Pressures you at low health | Head vs apple choice, heal under pressure |
| Inst-cart | Stands, then walks, then strafes | Rail to blast time, carts that hit |
| Cart defence | Inst-carts you | Carts knocked out before the arrow, blast damage taken |
| Crystal and anchor | Stationary, then moving | Place to blast time, anchor-glowstone-totem time, self-damage |

Each station has three stages; the bot's tier rises with yours (Beginner limits first, Demon at the
end), and its pass marks come from what the bot of that tier does in the simulator.

## 3. Gate fights and the Demon ladder

Each tier ends in a best-of-five against the learned bot of the next tier in its mode (`uhc` model on
the Demon profile for UHC). A ladder of Demon variants (lava-heavy, web-heavy, bow-heavy playstyles)
is the end game: the bot that wins by utility, held to human limits.

## 4. Coaching from recordings

After each round, the recording is analysed into coach lines, each linked to the station that trains
it and to the moment in the replay:

- "41% of your hits were below 0.9 charge."
- "You swung from 3.1-3.6 blocks 9 times; the Demon bot lands from 2.3 on average."
- "Webbed 4 times: 1.4 s to water out (the bot: 0.5 s)."
- "Your lava caught 2 of 6 times; poured on a jumping opponent it lands where they come down."
- "You stood in reach while recharging 14 times; it hit you 11 of those."

## 5. The bot learns from the world too

- **You in the league.** Your recorded fights become an imitation model of you (the same first step
  the bot's training uses), and that model joins the training league next to the scripted tiers and
  the bot's own snapshots, so the next training run is partly against how *you* play.
- **Mirror match.** Fight the imitation of yourself to see your habits from outside.
- **Weakness targeting.** The bot reads your stats and leans on your weakest skill (no jump resets:
  it combos; slow out of webs: it webs and lavas).
- **Adaptive difficulty.** Its limits move to keep your win rate near 50%.

## Build order

1. Training tab, hub board and the stats you already see for bots, recorded for you.
2. Sword stations (charge and reach, combos, crits) and the coach lines.
3. UHC stations (web and water, lava, blocks, bow, golden heads) and the UHC gate fights.
4. Cart and crystal stations, cart defence, anchors.
5. Adaptive difficulty, weakness targeting, then you-in-the-league training and the mirror match.

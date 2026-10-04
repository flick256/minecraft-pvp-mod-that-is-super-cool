# Plan: a PvP training world

Goal: a world that makes *you* better, not just a place to fight bots. The bots become sparring
partners and the world becomes the coach: drills that isolate one skill, feedback measured from what
you actually did, and a path from one skill to the next. Nothing here needs new combat rules; it all
builds on what SparBot already has (bots with switchable techniques, the recorder, the simulator,
arenas and matches, the menu).

## 1. The hub

A spawn hub with a portal or sign per discipline: Sword, UHC, Crystal, Mace, Utility, Movement. Each
leads to a small building of stations. Your progress (which drills you've passed, your best scores,
your Elo per mode) shows on a board in the hub and in a new **Training** tab in the menu.

## 2. Drills: one skill at a time

Each drill sets up a bot with only the techniques that matter switched on (already possible with
`/sparbot technique`), runs for a set time, and scores you on the one thing it trains.

| Drill | Set-up | Scored on |
|---|---|---|
| Cooldown timing | A bot that stands and blocks nothing | Share of your hits at full charge |
| W-tap / combos | A bot that walks at you; knockback on | Longest combo, hits landed at the edge of reach |
| Spacing | A bot that only swings when you're in reach | Hits landed vs hits taken |
| Crit timing | A stationary bot | Crits per minute, mistimed jumps |
| Whiff punishing | A bot that swings early on purpose | How fast you hit back after its miss |
| Anti-combo | A bot that combos you | Jump resets timed, hits taken in a row |
| Shield and axe | A bot that block-hits | Shields disabled, stun follow-ups landed within 4 ticks |
| Water clutch | A drop with a timer, then a webbed bot | Clutches landed, time to water out of a web |
| Lava control | A webbed bot | Pours, time to scoop, lava never left down |
| Blocks | Walls and boosts against a rushing bot | Time to a full wall, heal behind it |
| Crystal speed | Obsidian and a stationary bot | Place-to-detonate time, self-damage |
| Aim tracking | A strafing bot, no attacks | Share of time with the crosshair on its hitbox |

Each drill has three stages (pass marks rise), and the bot's level rises with you: beginner limits
first, pro limits at the end.

## 3. Coaching from recordings

The recorder already writes every tick of a fight (positions, inputs, charge, hits) for players too.
After each fight or drill, an analysis turns that into feedback a coach would give:

- **Charge:** "41% of your hits were below 0.9 charge; full-charge hits do 2.5x the damage."
- **Reach:** "You swung 9 times from out of reach (3.2-3.6 blocks)."
- **Spacing:** "You stood in its reach while recharging 14 times; it hit you 11 of those."
- **Crits:** "You jumped 22 times, 8 crits landed; most misses were jumping too early."
- **Combos:** "Longest combo 3; you W-tapped after 2 of 17 hits."
- **Utility:** "Water out of a web took 1.4 s (the bot does it in 0.4 s)."

Each line links to the drill that trains it. The replay viewer (already there) can jump to the moments
it mentions.

## 4. Sparring that teaches

- **Ghost mode:** fight a bot while a second, translucent bot shows what the pro model would press
  right now (where to stand, when to click), so you can see the better option as it happens.
- **Mirror match:** a bot trained on *your* recordings (imitation learning, the same first step the
  models use now) plays like you; fighting it shows you your habits from the outside.
- **Weakness targeting:** the bot reads your stats and leans on what you do worst (if you never
  jump-reset, it combos; if you swing early, it baits).
- **Adaptive difficulty:** the bot's limits move up or down to keep your win rate near 50%, the range
  where people learn fastest.

## 5. Progress

Per-mode Elo against bots (the ladder exists), drill medals, a weekly report in the menu (what
improved, what to drill next), and a "graduation" fight per stage against the matching model.

## Build order

1. Training tab + three sword drills (timing, crits, spacing) with scoring from live events.
2. Recording analysis with the feedback lines above.
3. UHC utility drills (water, lava, blocks) and the hub.
4. Adaptive difficulty and weakness targeting.
5. Mirror match (a model per player from their recordings) and ghost mode.

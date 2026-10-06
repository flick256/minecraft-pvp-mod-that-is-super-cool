# Roadmap: 0.9.0 and 1.0

## Where 1.0.0 landed

1.0.0 took the foundations of both plans below and shipped them together, skipping the long training runs:
- **Done:** skill drills for every skill (21 drills, three stages, medals) in a training hall; fight reports
  and coach lines after every match against a bot, the You page with your last ten; the Celestial Colosseum
  (600 blocks, background build, chunk-only resets) with waves and king of the hill; PvPHQ-kit modes for
  sword, crystal and mace; bots scooping water and lava at a human pace.
- **1.0.1:** the training runs: `sword_demon`, crystal and cart in the simulator and their learned brains,
  and drill pass marks tuned from what each tier really scores.
- **Still to come:** adaptive difficulty, weakness targeting, mirror matches, tiers and gate fights, queues
  and party fights, replay review, the in-game kit editor (sections 1-5 of 1.0 below), and the PvPHQ kit
  items still unverified.

Where SparBot is at 0.8.1: fair, mortal bots for sword, UHC, crystal and anchors, cart, mace, spear,
nodebuff and ranged; learned UHC brains (`uhc`, `uhc_demon`) that beat the scripted bots of their own
tier about 9 times in 10; shield stuns and the shield counter; PvPHQ kits; and the practice world (hub,
five arenas with grandstands, and the Arcane Colosseum). What is still thin: the practice world only
*hosts* fights, it doesn't *train* you; crystal, cart and mace bots are scripted only; the full GameTest
suite hasn't been run since 0.7.0; several kit items are still unverified.

## 0.9.0: "Train"

The practice world becomes a place that makes you better, and everything shipped so far is made solid.

### 1. Solid ground first
- Run the whole GameTest suite and the client GameTest on every release, and fix whatever fails (it has
  not been run in full since 0.7.0).
- Practice dimension in the GameTest server too (create the level at runtime if the data pack stem isn't
  baked in), so the arenas, pads and resets have server tests, not only the client one.
- Confirm the unverified PvPHQ kit items (cart potions, shulker contents, high tier slots 8, 11 and 20),
  and offer PvPHQ kits for every arena (crystal, sword, mace), with SparBot's own kits as an option.
- Upgrading old worlds: rebuild only what changed (per-arena versions instead of one version for the
  whole practice world).

### 2. Your stats, the same as the bot's
- Record the fight stats bots already have (hits and from how far, full-charge share, crits, combos taken
  and given, webs, lava and water that worked, arrows that hit, heals, blocks and stuns) for players too.
- A **You** page in the menu: your numbers per mode next to the bot tier you fight, and how they change
  over the last ten fights.
- Short coach lines after each fight ("41% of your hits were below full charge", "you stood in reach
  while recharging 14 times", "1.4 s to water out of webs; the pro does it in 0.5 s").

### 3. Training stations
The first stations from `docs/training-world-plan.md`, built into the hub as a training wing:
- **Sword:** charge and reach, combos and spacing, crits, anti-combo (jump resets).
- **Shield:** block-hitting, stunning a shield, the shield counter (the bot does each one to you, then
  you do it to the bot).
- **UHC:** web and water, lava on a webbed opponent, block lines going back, punish shots with the bow.
- Each station runs three stages, scores you with the stats above against the pass mark the bot of the
  next tier reaches, and gives a bronze, silver or gold medal.

### 4. The Colosseum as a game
- Free for all with bots: waves (two bots, then three, then a pro, then a demon), last one standing wins.
- King of the hill on the compass rose, and team fights (you and a bot against two bots).
- Spectating from the royal boxes, with the match's names and health floating over the field.

### 5. Brains
- The learned sword brain trained against the Demon too (`sword_demon`), and the learned brains used by
  default for sword kits like UHC already does (only if they win in the simulator).
- Crystal and cart in the simulator (crystals, anchors, rails, carts and blasts), so their bots can be
  measured, tuned and later trained like UHC.

**0.9.0 is done when:** every test passes; your stats and coach lines show after a fight; at least ten
stations give medals; the colosseum runs waves and king of the hill; `sword_demon` exists or the
simulator says it isn't better.

## 1.0: "Complete"

Everything a PvP player needs to practise alone or with friends, polished, documented and dependable.

### 1. Bots that grow with you
- **Adaptive difficulty:** the bot's limits move a little each fight to keep your win rate near 50%,
  within human limits, never past the Demon's.
- **Weakness targeting:** the bot reads your stats and leans on what you're worst at (no jump resets: it
  combos; slow out of webs: it webs and lavas).
- **You in the league:** your recorded fights become an imitation model of you, which joins the training
  league, so the next brain is partly trained against how you play. A **mirror match** against that model.
- Learned crystal and cart brains, if the simulator from 0.9.0 shows they beat the scripted ones.

### 2. Progression
- Tiers per discipline (Beginner to Demon) on a board in the hub and in the menu, with Elo against bots.
- A **gate fight** at the end of each tier: best of five against the learned bot of the next tier.
- The **Demon ladder:** lava-heavy, web-heavy, bow-heavy and shield-heavy Demon variants, the end game.

### 3. With friends
- Queues in the hub: duel a friend in any arena, or both of you against bots.
- Party fights and the colosseum's free for all for several players at once.
- Ranked player-vs-player Elo, kept apart from the bot ladder.

### 4. Replays and review
- Watch any recorded fight in the practice world from any angle, with the coach lines pinned to the
  moments they're about, and step frame by frame.

### 5. Polish
- An in-game kit editor (make and save kits without editing JSON), and every setting in the menu with an
  explanation.
- A getting-started page in the hub and a full guide in the docs.
- Performance: no server stalls (every build and reset spread over ticks, chunk loading done ahead),
  measured with several players and eight bots at once.
- Works the same in singleplayer, on a LAN and on a dedicated server; settings and saved layouts carry
  over from 0.x without being lost.

### 6. Release checklist
- Every GameTest and client GameTest passes on a clean build.
- A fresh install and an upgraded 0.8 world both start cleanly, singleplayer and dedicated.
- Each mode played through in game against every tier at least once.
- Changelog, README, the guide and the screenshots up to date.

**1.0 is done when** the checklist is complete and a full evening of practice (stations, gate fights,
the colosseum with friends) runs with no errors, no stalls and no unfair bot.

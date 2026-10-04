# Changelog

The version is in the jar's name, the menu's title and the server log ("SparBot 0.3.0 initialised").

## 0.3.0

- UHC is now machine-learned too: the duel simulator knows shields (the 0.25 s block delay, 90 degree
  cover, axe disables), golden apples (32 ticks, Absorption I, Regeneration II), item switching,
  Protection armor and UHC's lack of natural regeneration. A bundled `uhc` model (version 2 network:
  also decides when to raise the shield and when to fight with the axe). `/sparbot train uhc <name>`.
- The `sword` model is retrained to strafe less and land more crits (training now penalises constant
  strafing and rewards crits), and learned bots obey the technique switches (strafe, block-hit).
- Leftover water and lava: bots see the source blocks around them, scoop up any they can reach with an
  empty bucket (theirs or yours) when it's safe, and block up lava next to them; they swim out of water
  pockets and step round whatever they're stuck on.
- Shields: bots stop swinging into a raised shield, only raise their own when it's worth it, and only
  count an axe hit as a disable once the shield really comes down.
- No swings through blocks or a web's outline (UHC hit rate up from 20-46% to 61-84%).
- Reach checked: new tests prove bots never hit beyond vanilla's 3 blocks.
- Block boost (UHC/SMP): a few blocks out, put a block ahead, sprint-jump onto it and launch off it.
- Crystal: bots give up on crystals they can't hit instead of staring at them.
- Training uses every core it's given (bigger populations on bigger machines).

## 0.2.0

- Kits tab in the menu (equip yourself with any kit); per-bot technique switches; reflexes while busy
  with utility; whole-fight lock-up tests and fixes; lava spam and bigger walls in UHC; crystal bots
  fight back with the sword; the sword revamp (reading, spacing, feints, whiff punishes, crit
  approaches); the duel simulator; the learned `sword` model and `/sparbot train`.

## 0.1.1

- First feedback round: UHC water/lava/web logic, predictive carts, strafe styles, PvPHQ kits.

## 0.1.0

- Milestones 1-7: mortal bots, kits, combat, playstyles, matches, Elo, NoDebuff, mace, spear,
  crystal, cart and UHC modes, recording, replay and the menu.

# Changelog

The version is in the jar's name, the menu's title and the server log ("SparBot 0.4.0 initialised").

## 0.7.1

- A UHC brain built on the Demon: `uhc_demon`, trained against the scripted Demon at Demon limits. Demon
  bots get it automatically (setting `autoModels`). In simulated UHC fights it beats the scripted Demon
  94% of the time (the `uhc` brain: 89%) and the `uhc` brain itself 77%, with a little more utility
  (5.4 webs, 2.3 lava pours and 7.5 water pours a fight) and much more shield play.
- UHC matches and the UHC meadow use PvPHQ's UHC kit (`pvphq_uhc`) for both sides instead of SparBot's
  own; the bots play it as well (the `uhc` brain wins 93% against the scripted pro in it, with 7 webs,
  2.5 lava pours and 28 blocks a fight).

## 0.7.0

- Practice world: SparBot's own flat desert (sand and sandstone over stone and deepslate, down to
  bedrock) with a hub and an arena per kind of fight: a walled UHC meadow (rolling grass, flowers, oaks,
  a pond, boulders), a quartz sword court, the open crystal desert (nothing in the way, diggable and
  blastable down to bedrock), a fenced cart field and a tuff mace and bow court. Pads in the hub take
  you to each arena. It is a dimension in every world (`/sparbot practice`, built on the first visit), or
  a whole world with the new **SparBot Practice** world type. The menu's new Practice tab (and
  `/sparbot practice fight <mode> [profile]`) starts a match against a bot in that mode's arena.
- Shield stuns work again: a fully blocked hit no longer starts the blocker's invulnerability, so the
  second click of a stun lands (setting `shieldStuns`, on). The bot does stuns and can be stunned.
- Shield counter: against a charged swordsman about to come into reach, UHC bots walk in behind a raised
  shield, let the swing land on it, then drop it and hit while they recharge. In simulated UHC fights a pro
  with it beats a pro without block-hitting 71% of the time (54% with block-hitting alone).
- UHC bots use the learned UHC brain by default (setting `autoModels`, on), held to their own tier's
  limits: in the simulator it beats the scripted bot of the same tier about 9 times in 10 with as much
  lava, web and water play. The learned brain now does the shield stun and the shield counter too.
- Fair kits: a bot sent to fight you wears the kit you last equipped (setting `matchPlayerKit`, on).
- PvPHQ cart kits, low tier (Flame bow) and high tier (crossbows and flint and steel), with match modes
  `cart_low` and `cart_high`. Unverified: the uncertain items are listed in each kit's notes. High tier
  bots set carts off the PvPHQ way: rail, cart, fire lit next to it, then a crossbow loaded in advance
  shot through the fire (a burning arrow sets a TNT minecart off at once).
- Crystal bots judge blasts through their armor: a practised bot never sets off a crystal or anchor that
  would pop or kill it, and steps out of the opponent's crystals and anchors.
- Golden heads are unchanged.

## 0.6.0

- Demon: a skill tier above pro, a top player who lives on utility, held to human limits (reactions
  155 ms on average, never under 130; 15 CPS; reach judged tightly but held back, hits from 2.3 blocks on
  average). Scripted Demon beats scripted pro in about 66% of simulated UHC fights. The bundled `uhc`
  brain is retrained at Demon level: 0.88 against the scripted Demon and 0.94 against the scripted pro in
  simulated fights, 0.74 against the 0.5.0 brain, 12 of 12 in game; about 14 arrow hits, 2.4 lava pours,
  4 webs and 20 blocks a fight, hits from 2.3 blocks on average.
- UHC blocks: fast clicking (a fresh right-click per tick or two, at the profile's click rate, instead of
  holding's one per 4 ticks), and the block line going back: under pressure with an apple to eat, the
  bot back-pedals and clicks a line of blocks (two high when quick) between itself and the chaser, then
  eats behind it. Walls only while the opponent is still out of reach.
- Golden heads in the UHC kits (PvPHQ's hotbar slot 4, and two in SparBot's): eaten in 0.8 s,
  Regeneration II for 10 s and Absorption. A golden apple that looks like a head, so it can't be placed.
- Kit enchantments: UHC and sword kits Protection III helmet and boots, Protection II chestplate and
  leggings, Sharpness III; cart, crystal and SMP kits fully maxed.
- Carts: inst-carts whenever the opponent is in cart range on the ground; short-draw shots released as
  soon as they're on the cart; enemy carts next to the bot are knocked out with one hit (or it gets
  clear).
- Anchors: set off with the hotbar totem, as players do. Fixed: a bot's right-click on a block only
  reached the block for items that place something, so nothing but glowstone could set off an anchor (a
  real client tries the block first with any item); charged anchors now really go off, and the bot steps
  back from its own anchor before setting it off.
- Menu: a You tab (Full heal, natural regeneration on/off) and Save layout / Reset per kit: your own
  hotbar arrangement comes back every time you equip a kit. `/sparbot layout save|reset <kit>`,
  `/sparbot heal`, and the `naturalRegeneration` setting.
- Models trained on 0.5.0 keep their melee; their tactic chooser (made for the old set of tactics) is
  dropped with a warning until retrained.

## 0.5.0

- Human-like reach: every bot judges its reach the way people do, holding back a little with a
  judgement that wobbles (a pro by -0.3 +- 0.15 blocks, a beginner +0.4 +- 0.5), and the learned melee
  only clicks inside that judged reach. No more constant 3-block hits: the learned sword bot's hits land
  from 2.5 blocks on average and 3% from beyond 2.9 (it was 16%); in game, UHC pros hit from 1.9-2.2.
  Fight stats show the mean hit distance and the share of far hits. The `sword` model is retrained.
- UHC utility revamp. Traced every utility play in the simulator and fixed why it lost fights:
  - lava and webs aimed at the air under a jumping opponent; they now land where the opponent comes down;
  - blocks, webs and lava only click when the highlighted block is the one meant, and lava is scooped
    where it actually landed (a wall in front used to catch the pour);
  - webs flickered (reached for, then dropped a tick later, throwing away the sword's charge); a web is
    now seen through once started;
  - utility only in windows that cost no hit: right after the bot's own hit, out of reach, or while the
    opponent eats or is stuck; melee rushes an opponent who can't hit back;
  - new plays: lava on an opponent eating an apple, the bow at mid range on a stuck or eating opponent,
    a web on the chaser to cover a heal; boosts only to catch someone holding back; walls only while the
    opponent is still out of reach.
  Against the same pro without them (1000 simulated fights each): lava 0.56, webs 0.58, walls 0.51,
  boosts 0.51, all utility together 0.88. Lava and webs together went from 0.35 to 0.60.
- New `uhc` brain, trained utility-first: the tactic chooser may bring lava, webs, the bow, walls and
  boosts forward but only hold them back slightly, and can't lift plain melee over them; training also
  rewards utility that lands. It uses more lava, webs and arrows than the scripted pro and beats it in
  about 79% of simulated fights and 9 of 12 in game. A test checks that no network can train the utility away.
- Respawn anchors in crystal PvP: anchor next to the opponent, glowstone, set off with the sword (power 5
  with fire outside the Nether), only when the opponent takes more of the blast. The SparBot crystal kit
  carries anchors and glowstone.
- Bot-vs-bot cart fights join the whole-fight lock-up tests.

## 0.4.0

- UHC brain: the bundled `uhc` model now chooses tactics as well as fighting in melee. A second network
  shifts the scripted brain's tactic scores (lava, webs, water, walls, healing, bow, boosts...), trained
  together with the melee network in full-kit simulated UHC fights. It beats the scripted pro in about
  93-96% of simulated fights and won 12 of 12 in game (UHC rules, every win a kill). It plays aggressively: it learned that lava, webs
  and walls rarely pay off against an opponent who carries water, and that pressure, the bow at range,
  healing and quick escapes do. `/sparbot train uhc <name>` trains the whole brain; `uhcmelee` the melee alone.
- New UHC simulator: blocks, water and lava that flow by vanilla's rules (lava turns to obsidian or
  cobblestone next to water), cobwebs, fire, fall damage, buckets, placing blocks and webs, the bow, and
  the real kits. Checked against the game with parity tests (fight length, tactic time, hits, utility,
  escape times agree).
- Webs: bots pour water onto the web they're actually caught in (it used to miss a web diagonally next
  to them and loop), cut themselves out with the sword when there's no water to hand, and keep a spare
  water bucket in the hotbar. Time stuck in webs roughly halved.
- Bow standoffs end: after a while a bot pushes in at any distance.
- A blocked hit no longer pushes a bot (a real client never feels it).
- Simulator accuracy: a click at nothing locks clicks for 10 ticks and only a real attack resets the
  charge (as on a server); sprinting continues while using an item.

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

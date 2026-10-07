# Changelog

The version is in the jar's name, the menu's title and the server log ("SparBot 0.4.0 initialised").

## 1.2.0

A new version of the Celestial Colosseum's story, its boss, its rewards and its halls. **SparBot now adds its own
items and an entity, so players need it on their client too** (before 1.2 it was server-side only).

- **Vaelor, the Unbroken is a real boss now**, not a bot: a new creature, four blocks tall in star-iron plate with a
  crowned great helm, a cape and a greatsword as long as he is tall, with his own model, texture and glowing eyes,
  heart and runes (violet, then gold, then red). He fights in three phases, and shows every blow before it lands:
  - *The Oath*: **Cleave** (a wide sweep; a shield takes it), **Overhead** (a blow down a line marked in red on the
    floor, which knocks a shield aside), **Lunge** (he crouches, then charges where you are) and **Guard** (hit him
    from the front and he ripostes; get behind him and he staggers).
  - *Starfall*: he calls **falling stars** down on circles marked round you, and **leaps** to where you stand; the
    landing sends a **shockwave** out along the ground that you have to jump.
  - *The Unbroken*: faster, his cleaves chain, and the **Star Lance**: a beam he aims slowly, then fires and drags
    after you. Hide behind the broken columns.
  - He roars between phases (he can't be hurt then) with a line for each, and when he falls he kneels, the light
    goes out of him and he's gone. His blows are his own damage types, the same on every difficulty.
- **His rewards are new items**, stronger than anything in vanilla:
  - **Oathkeeper**, his greatsword: 14 attack damage (a netherite sword does 8, an axe 10). Use it for
    **Starbreak**, a shockwave along the ground in front of you (10 damage, every twelve seconds).
  - **Starfall**, a bow: its arrows leave the string a fifth faster and do 3.5 base damage instead of 2.
  - **The Unbroken Plate** (helm, plate, greaves, sabatons): 24 armour and 16 toughness for the set (netherite: 20
    and 12) and more knockback resistance, with its own look when worn.
  - **The Heart of Aster**: use it to heal three hearts and get Regeneration II, Absorption II and Resistance; it
    rests for a minute and a half after.
- **The Deep: his prison is a giant cavern now.** Through the Hall of the Fallen's arch is **the Brink**, a ledge round
  a shaft, and the Oath Bridge out to the **Well** under the hanging Heart of the Star. Below: a cavern two hundred
  blocks across and eighty high, its dome set with stars, stalactites, amethyst spires and lava falls, and in it **the
  Deep Colosseum**: an arena seventy blocks across with six broken columns, his throne, the stands rising round it
  full of the skulls of the crowd that watched, a four-storey arcade lit from within, and a lava moat.
- **The Leap.** Drop down the Well (in survival) and you float eighty blocks down into the arena while **Vaelor's
  Theme** starts; your own things are kept safe and you get the challenger's kit. One challenger at a time. **A
  fight only ever starts from the Leap**, so walking out after a win can't start another (the 1.0.3 bug).
- **The way back up, for whoever wins.** The Triumph plays (a new track: his theme in the major, a minute long) and
  the dead in the stands roar. The **Gate of Triumph** opens across from his throne; braziers light ahead of you down
  the **Avenue** and over the moat to the **Hall of Triumph**, where your own things are given back and his are on
  seven pedestals for you to take. The **Gallery of Witness** climbs the cavern wall, with the story on its stones;
  the **Stair of Stars** goes in over the dome with glass in its floor to look down through; the **Laurel Door**
  opens onto the Brink; and on the platform the Heart breaks and **lifts you up through the field** into the
  colosseum, into fireworks, the crowd and the title *Champion of the Crown*. Everyone online hears who won, your
  name goes up on the champions' plaques, and anything you didn't take off the pedestals is kept for you.
- **Twenty-four great halls instead of a thousand small ones.** Each Gallery under the stands is now eight great
  halls (forty to a hundred blocks long), split at the gates and at four great piers, each one a single room built
  in layers: a patterned floor with a runner and a medallion, colonnades holding up balconies (two in the outer
  Galleries) with stairs up to them, a raked or vaulted ceiling with ribs and chandeliers, and its own furnishing and
  story: the Armoury of the Seven, the Archive of the Founders, the Hall of Champions, the Chapel of the Fallen Star,
  the Warden's Hall, the Cells, the Hall of Oaths, the Treasury, the Forge of Star-Iron, the Feast Hall, the Hanging
  Gardens, the Baths, the Observatory (with a great armillary round the star), the Library of Bouts, the Theatre,
  the Menagerie, the Barracks, the Training Grounds, the Map Room, the Infirmary, the Kitchens, the Crypt of the Six,
  the Council of the Seven and the Wagering Hall. The ring corridors are single grand promenades with each hall's
  name over its doors.
- The four secret vaults are in the piers between the halls now; the books point the way by hall and compass.
- Discoveries: walk twelve of the great halls, and all twenty-four (instead of 25 and 100 small ones).
- GameTests walk the Deep (down to the Brink, the Leap, the arena shut in, and the whole way back up with the gates
  open, no lava loose) and the halls (every floor and balcony and vault reachable), check his phases and his
  kneeling, that his blows land, and that his rewards beat vanilla.
- Worlds with an older colosseum rebuild it on the next visit (the Deep goes down to y -60).

## 1.0.3

- **The Heartwell: a boss fight under the field.** The Lower Door in the Cells is now a wide stair down, lit and
  bannered, with words for whoever goes down. At the bottom is the Hall of the Fallen, with plaques for those who
  went down and lost, the Roll of the Fallen, and the champions' plaque across from it. Through its great arch is
  the Heartwell: a domed arena forty-four blocks across, with eight pillars to fight round, the heart of the star
  hung over its seal, and Vaelor's throne. Step in and the way is barred behind you, a war horn sounds, **Vaelor's
  Theme** starts (an original track made for SparBot, 96 seconds, looping), and Vaelor the Unbroken rises with his
  name on the screen and a boss bar. You both get the same kit (netherite, sword, axe, shield, bow, pearls, golden
  apples, two totems; your own inventory is kept safe and given back), and he is the strongest bot there is. He is
  mortal, with nothing you don't have. He talks as the fight goes. Beat him and he thanks you: his blade
  *Oathkeeper*, the *Champion's Laurel*, thirty levels, your name on the plaques in the Hall of Champions and the Hall
  of the Fallen, and the news to everyone online. Lose and he sends you back up to train.
- **Discoveries with rewards.** The Hollow Crown has its own advancement tab: stepping under the stands, the six
  landmark halls, the Lower Door, the Hall of the Fallen, four secret vaults, 25 and 100 different halls walked,
  beating Vaelor, and finding every secret. Each one comes with a fanfare, experience and a relic of the story
  (Tell's Quill, Corvin's Lantern, Imre's Candle, the Lower Key, a Champion's Coin, the Fragments of Aster...), and
  is remembered for you.
- **Four secret vaults**, sealed behind cracked walls on landings of the Third Gallery's stair halls: crouch before
  the stone to open one. Tell's Hidden Study, the Masons' Vault (with their model of the bowl), the Seventh Seat
  and Vaelor's Armoury, each with a page of the story.
- **A simpler story with an ending.** Vaelor held the star's heart shut himself and swore to stay below until a
  champion could beat him; two hundred tried. The books are shorter and lead straight on: the Guide, the Archive,
  the Warden, the Cells (and the Chapel for a blessing), then down.
- **Every hall its own.** Twenty-eight kinds of hall instead of sixteen (new: observatories with an orrery,
  forges with a crucible, beast pens, looms, ledger rooms, a ward, trophy halls, apiaries, betting halls, engine
  rooms with a great clock, mushroom cellars, council chambers, salons, conservatories...), each in one of twelve
  woods and fourteen stones, two colours and six kinds of light, with its own floor pattern, wall treatment,
  ceiling, chandeliers, a head (altar, hearth, stage, forge, throne...) and a unique name and inscription. A few
  are long abandoned. Neighbours are never the same kind.
- **Stair halls instead of spiral stairs.** The spirals that blocked the corridors are gone: every Gallery has
  four stair halls at the diagonals, each with a lobby and a switchback stair to the top level. Halls open only
  onto corridors (no doors between halls), and levels no corridor reaches are solid.
- **Water stays put.** Water is set after everything else, every pool and fountain is walled in all round, and
  the floating islands have springs instead of waterfalls.
- Two steps down into each concourse at the aisles, so the seats below are a step away instead of two.
- A GameTest walks the colosseum's plans like a player: every hall and stair level reachable, the way down to
  the Heartwell, and no water that could leak.
- Worlds with an older colosseum rebuild it on the next visit.

## 1.0.2

- **The Hollow Crown: a mystery inside the Celestial Colosseum.** The colosseum has a story now, told in books
  on lecterns and inscriptions on the walls, and a trail to follow through it. A Visitor's Guide in every gate
  passage explains the arena and points to the Founders' Archive; the Archivist's Chronicle of the Falling Star
  leads to the Warden's Hall; the Warden's Log to the Chapel and the Treasury's Tithe Ledger; the Chaplain's
  Litany gives away the lower door in the Cells. Below it a stair and a long tunnel run under the field to the
  Heartwell, a secret vault beneath the compass rose holding the seal, eight warded frames, an empty throne and
  the Seven's confession. Books put back on their lecterns when the colosseum resets; signs are waxed.
- **Six landmark halls:** the Archive of the Founders (books to the ceiling, a reading table, a globe), the
  Warden's Hall, the Chapel of the Fallen Star (pews, an amethyst altar under a great window of glass), the
  Cells (barred cells, chains, scratchings, the lower door), the Treasury of Tithes (gold, scales, a barred
  vault) and the Hall of Champions (ten champions on pedestals with their records, and one struck off).
- **The Grand Stairs:** every gate passage opens, under the first two tiers, into a towering atrium hung with
  chandeliers on long chains. Beside it two flights climb behind balustrades, a bridge (the Bridge of the
  Seven) crosses the atrium, and the second flight comes out through the seats onto the first concourse.
- **Sixteen kinds of hall** instead of nine, each with its own furnishings (new: baths with lit pools, garden
  halls with azaleas, spore blossoms and a fountain, music halls with a stage, crypts, barracks, map rooms with
  the bowl laid out in the floor, kitchens), plus beams, sconces, windows beside every door, scrawled rumours on
  the walls, and a plaque over every door naming the hall beyond (sector, name, gallery and level).
- The gate passages name the rings they cross and welcome you at the outer end.

## 1.0.1

- **Inside the Celestial Colosseum's stands.** The space under the seats is no longer a dark undercroft (that
  mobs spawned in): it is storeys of rooms every eight blocks, three ring corridors running all the way round
  (behind the podium and under each concourse) lit by lanterns and glowing floor stones, with banners and
  benches, and forty-eight sectors of rooms between them. Each room is one of nine kinds: feast halls (a long
  candlelit table and benches), armouries (anvils, grindstones, smithing tables, weapon racks), libraries
  (bookshelves to the ceiling, lecterns, an enchanting table), shrines (an amethyst altar, crying obsidian,
  candles, crystals), training rooms (targets and straw dummies), treasuries (heaps of gold, decorated pots),
  alchemists' rooms (brewing stands, cauldrons), lounges (carpets, sofas, azaleas, a jukebox) and storerooms.
  Doors join every room to the corridors and to its neighbours.
- **Getting to your seat:** sixteen spiral staircases round lit newels climb from the ground floor, past every
  storey, to the two concourses; the aisles now run straight up the stands, the rail over each tier opens at
  them, and two steps lead up from each concourse to the tier above. The gate passages are vaulted halls with
  doors into the corridors they cross.
- **The Great Crystal:** a hexagonal crystal a hundred blocks from tip to tip floats over the Grand Bowl inside
  the halo, banded in purple, magenta and pink glass with amethyst edges, a column of light at its heart and
  beams from its tips; six smaller crystals float round it, and a gold ring and a glass ring orbit it.
- No hostile mobs in the practice world (named ones are left alone).
- Worlds with the 1.0.0 colosseum rebuild it in the background on the next visit (its pad opens when done).

## 1.0.0

- **Skill drills: every PvP skill trainable.** A training hall west of the hub (through the gold pad, the
  menu's new Drills tab, or `/sparbot drill`) with six glass-walled bays and 21 drills: full charge, edge of
  reach, crits, W-tap, S-tap and spacing, combos, jump reset, aim; block-hitting, shield stun, shield
  defence; health pots; out of webs, lava, bow shots, rod hits; crystal speed, anchor chain, re-totem;
  inst-cart; mace smash. Each has three stages that get harder (a dummy, a moving bot, a bot that fights
  back, or three tiers of fighting bot), each a timed round scored on that one skill from what the server
  sees you do: the charge, reach, sprint and fall of every hit, the bot's hits on you and whether you jumped
  or blocked, shields disabled and followed up, how much of each pot landed on you, seconds in a web, the
  bot on fire, arrows and hooks that hit, blasts, smashes and seconds to a new totem. Bronze, silver and gold
  medals, your best kept; your inventory is kept safe and comes back after. A drill's bot is held to the same
  human limits as any other, scripted moves included.
- **Fight reports and a coach.** After every match against a bot, your numbers (from the same measurements)
  and up to three coach lines on what to work on, each naming the drill that trains it ("62% of your hits
  were below full charge..."). The menu's You page shows your last fight and how your full-charge share moved
  over your last ten.
- **The Celestial Colosseum** replaces the Arcane Colosseum: six hundred blocks across, 560 blocks north of
  the hub. The Grand Bowl (a field round the compass rose, three tiers of seats over a hundred blocks high
  split by two concourses, a balcony imperial box with thrones, a vaulted undercroft lit from its ceiling, a
  crown wall with four storeys of glowing arches and flying buttresses, sixteen towers with purpur spires and
  a striped canopy), a plaza, a lit moat with four bridges, cherry gardens and a causeway under a great arch;
  four satellite stadiums of their own, Fire, Frost, Grove and Void; eight seventy-block knights with
  greatswords guarding the bridges; and in the sky a dragon coiling over the bowl, a phoenix, a sky whale
  with an island on its back, glowing jellyfish, the halo, a sky citadel and waterfall islands. It is built in
  the background (a twentieth of a second's work per tick at most; a few minutes, progress on your action
  bar, its pad opens when done), and a reset only goes over the chunks people were in.
- **Colosseum games** in the Grand Bowl: waves (two beginners, three intermediates, a pro and two advanced, a
  Demon and two pros; your kit and health back between waves) and king of the hill (hold the heart of the
  compass rose for sixty seconds), from the Practice tab or `/sparbot practice colosseum waves|hill`.
- **PvPHQ kits for every arena:** `sword_pvphq`, `crystal_pvphq` and `mace_pvphq` modes beside SparBot's own
  (cart and UHC already used PvPHQ's).
- Bots scoop up your water and lava at a human pace: a source lying around has to be noticed first (reaction
  time plus a moment: about 0.45 s for a Demon, 0.7 s for an advanced bot) and each scoop is followed by a
  pause (0.5 s for a Demon, about 1 s for an advanced bot). Lava at their own feet still goes at once.
- Crossbow carts: a cart whose blast would kill the bot from where it stands (a crossbow cart's does at four
  blocks, even through netherite) is still placed: the bot lights the fire, backs off along the line and
  shoots from where it survives. An enemy's cart nearer the bot than its opponent is knocked out, not used.
- The whole GameTest suite (134 tests) and the client GameTest pass.
- Worlds built by 0.8 are rebuilt on the next visit (the old colosseum's ground is cleared back to desert).

## 0.8.1

- The colosseum's field is repaved: concentric flagstone courses round a heraldic compass rose (four long
  points in calcite and tuff over four diagonal ones in smooth stone and andesite, each with a light and a
  shaded half and a blackstone edge, round a gilded boss ringed in amethyst), a braided knotwork band, and a
  tuff border with a gilded edge and twelve glowing stones. The hexagram is gone.
- More in the sky: a citadel on its own floating island beyond the north gate (curtain walls, a gateway,
  four corner towers and a tall keep with lit purple windows and purpur spires, cherry trees, hanging
  chains); two floating islands pouring waterfalls into the moat; great rune portals hovering over the north
  and south gates; and rock fragments drifting round the colosseum at different heights, some crowned with
  amethyst.

## 0.8.0

- **The Arcane Colosseum**: one grand stadium for free-for-all fights, medieval stone with an arcane glow.
  A field inlaid with a magic circle (amethyst rings, a purpur hexagram, glowing rune stones) behind a
  blackstone podium wall with crying-obsidian runes, soul-fire braziers and purple banners; two tiers of
  seats with purpur aisles and glowing steps, split by a lantern-lit promenade; royal boxes behind purple
  glass over the four gates; a three-storey outer wall whose arches are glowing purple windows; twelve
  towers with lantern rooms and purpur spires; four gates with raised portcullises, soul-fire pillars and
  bridges over a glowing moat; a cherry-blossom garden; and above it all a floating halo, crystal shards, an
  amethyst crystal over the middle and four floating cherry islands hung with chains.
- Reached from the purple gateway south of the hub (or the menu's Colosseum button, or
  `/sparbot practice colosseum`). Fight anyone, spawn bots as you like; it resets itself once every player
  has left (every block back as built, loose items, arrows, crystals and carts gone), a few columns per
  tick so the server never stalls.

## 0.7.2

- The practice world rebuilt (worlds built by 0.7.0 or 0.7.1 are rebuilt on the next visit):
  - Grandstands round every arena, outside it so fights and round resets never touch them: a stone
    colosseum rising behind the UHC meadow's wall, a blackstone and quartz grandstand with canopies round
    the sword court, a sandstone amphitheatre round the crystal desert (12 blocks out, beyond any crystal or
    anchor blast), deepslate stands round the cart field (14 blocks out, beyond a crossbow cart's blast,
    with grass in between) and tuff stands round the mace court. Aisles, battlements, pillars with lanterns
    and banners, and arcades on the outside walls.
  - Each pad now takes you to a viewing box at the top of the arena's south stand (carpet, rail, canopy),
    with the pad back to the hub behind you.
  - Floating names over every pad in the hub, over each arena and over the pads back to the hub.
  - A grander hub: a fountain, banner pillars either side of each pad, obelisks and palms.

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

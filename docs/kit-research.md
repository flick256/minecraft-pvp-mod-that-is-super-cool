# Kit research: MCPVP and related servers

Provenance reviewed: 2026-10-18. Public-source research last completed: 2026-10-03. The rule for this project is **never invent a kit layout**. A kit is
only marked `verified` when its exact contents come from the user or from a primary source.

## What "MCPVP" is today

The current MCPVP is **PvP Club** (`mcpvp.club`), a modern-combat practice server.
- Its tier-test page sends players to **MCTiers** for official tiers (LT5 to HT1 per gamemode).
  Source: https://mcpvp.club/tiertest
- A third-party guide (BoardMC, 2026-09-12) lists 14 kits:
  - Skill kits: Sword, Shield, Pot, Spear, Mace
  - Vanilla kits: Early Game, Late Game, End Game
  - Community kits: Netherite Pot, Diamond SMP, Creeper, Cart, Bow, SMP

  Source: https://www.boardmc.com/post/mcpvp-guide-kits-tiers-rankings

**No public source gives exact MCPVP kit contents.** Neither the site nor the guide lists items,
enchantments, counts or slot positions. The server has an in-game kit editor, so default layouts
are only visible in game. Every MCPVP kit is therefore **unverified** until the user supplies a
screenshot or list. SparBot ships none of them.

## Closest public data: MCTiers-style recreations

`HelixCraft/Minecraft-PVP-Kits` (GPL-3.0) provides `/give` commands for Sword, Axe, Mace, UHC,
Netherite OP, Pot, SMP and Crystal kits, stated to be "based on the default kit layouts from
MCTires [sic]". It is a third-party recreation and doesn't say which version or date it reflects.
Source: https://github.com/HelixCraft/Minecraft-PVP-Kits

SparBot includes exactly one kit derived from it, the Sword kit, with
`provenance.confidence = "low"` and `verified = false`:

| Slot | Item | Enchantments |
|---|---|---|
| head | diamond_helmet | protection 4, unbreaking 3 |
| chest | diamond_chestplate | protection 4, unbreaking 3 |
| legs | diamond_leggings | protection 3, unbreaking 3 |
| feet | diamond_boots | protection 3, unbreaking 3 |
| hotbar 0 | diamond_sword | unbreaking 3 |

## PvPHQ (play.pvphq.com), from the user's screenshots

On 2026-10-04 the user sent screenshots of PvPHQ's kit editor for five kits. They are bundled as
`pvphq_sword`, `pvphq_uhc`, `pvphq_smp`, `pvphq_mace` and `pvphq_crystal`, slot for slot, with
`provenance.source = "user-supplied"`, `confidence = "medium"` and `verified = false`, because:
- **Enchantments** aren't visible in an inventory screenshot (only the netherite axe in the SMP kit had
  its tooltip open), the original transcription omitted them. Later user requests supplied the UHC/sword enchantments and requested maxed enchantments for cart, crystal and SMP; the exact server enchantment sets remain unverified.
- **Potions** were read by colour (26.2 colours: Strength yellow, Fire Resistance orange, Swiftness light
  blue, Healing red); their levels are guesses.
- **A few items** couldn't be identified (a tall purple item in the mace and crystal kits, a long purple
  weapon and a grey-white splash potion in the crystal kit) and are left out.
- **Golden heads** use a custom consumable with a head appearance and 0.8-second eating time. The bundled regeneration/absorption values are assumptions documented in the kit notes, not verified PvPHQ behavior.
- **Respawn anchors** are supported: placement, glowstone charging and detonation outside the Nether.

On 2026-10-05 the user sent the two cart kits, bundled as `pvphq_cart_low` (rails, TNT minecarts, a Flame
bow) and `pvphq_cart_high` (rails, TNT minecarts, flint and steel and crossbows: a crossbow bolt shot
through fire sets a cart off, which a GameTest confirms). Same caveats: potions read by colour (only the
Strength II splash potion's tooltip was open), shulker box contents not shown (given empty), oak logs vs
planks, and in the high tier kit hotbar slot 8 (behind a tooltip), inventory slot 11 (a bow?) and slot 20
(a netherite pickaxe?). Each kit's `provenance.notes` lists them.

Each kit's `provenance.notes` lists its uncertain slots. Hover screenshots of the enchanted items (or
`/sparbot kit capture` in singleplayer after rebuilding a kit) would make them verified.

## Still needed from the user

For the PvPHQ kits: hover text of the enchanted items, and what the unidentified slots are (see above).
For each other kit you care about (Shield, Pot, Spear, Cart, Netherite Pot, ...), a screenshot of the
default layout plus the hover text of any enchanted item.

## 3.1 behavior audit

Both cart kits keep the existing screenshot-based slot layout. Their carried buffs are in the main
inventory, so the bot now temporarily swaps each needed buff into the hotbar, uses it, then restores
the displaced stack. This also supports inventory buffs in the other kits. Cart and UHC restocking
can bring spare rails and webs into empty hotbar slots. Storage blocks, including colored shulkers,
are never spent as emergency wall material.

No new exact PvPHQ item list was available during this review. Hidden slots, potion identities and
levels, enchantments and shulker contents remain unverified; changing them without evidence would
introduce another guess. A screenshot of the kit editor plus item tooltips is sufficient evidence;
no export or technical command is needed from the user. Sword and UHC kit contents were unchanged.

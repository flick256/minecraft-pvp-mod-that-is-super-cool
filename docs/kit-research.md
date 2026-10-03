# Kit research: MCPVP and related servers

Last researched: 2026-10-03. The rule for this project is **never invent a kit layout**. A kit is
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

## Still needed from the user

For each MCPVP kit you care about (Sword, Shield, Pot, Spear, Mace, Netherite Pot, ...), a screenshot
of the default layout (`/kit` editor or inventory) plus the hover text of any enchanted item.

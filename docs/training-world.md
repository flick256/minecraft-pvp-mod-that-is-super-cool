# Training world: design (built after the bot is feature-complete)

SparBot must always work in any normal world. A training world is an optional **pack** that adds
structured practice on top: arenas, drills and spawn markers, loaded through the same systems matches
use today.

## Pack format

```
sparbot-pack/
  pack.json          id, name, version, minecraftVersion, list of maps
  maps/<map>/
    map.json         arenas, drills and markers for this map (below)
    blocks.nbt       the map's blocks (vanilla structure format, like arena snapshots)
```

`map.json`:

```json
{
  "id": "dojo",
  "origin": [0, 64, 0],
  "arenas": [
    {"id": "dojo_sword", "from": [0,0,0], "to": [24,8,24], "spawnA": [4.5,1,12.5,-90], "spawnB": [20.5,1,12.5,90], "modes": ["sword_duel"]}
  ],
  "markers": [
    {"type": "bot_spawn", "pos": [12.5,1,30.5], "profile": "intermediate", "kit": "basic_sword"},
    {"type": "lobby", "pos": [0.5,1,0.5]}
  ],
  "drills": [
    {"id": "bridge_1", "type": "bridging", "start": [40,10,0], "finish": [40,10,60], "kit": "blocks_64", "timeLimitSeconds": 60},
    {"id": "clutch_1", "type": "clutch", "drop": [80,40,0], "height": 30, "kit": "water_bucket"},
    {"id": "aim_1", "type": "aim", "area": [[100,0,0],[120,10,20]], "targets": "moving_bots", "profile": "beginner"},
    {"id": "parkour_1", "type": "parkour", "checkpoints": [[140,1,0],[140,3,8],[143,5,14]]}
  ]
}
```

All coordinates are relative to `origin`, so a map can be pasted anywhere.

## Extension points (already in the code, or planned to plug in)

| Point | Today | Training world use |
|---|---|---|
| `ArenaRegistry` | Arenas from config with block snapshots and reset | Pack arenas register the same way, with their snapshot taken from `blocks.nbt` |
| `GameModeRegistry` / `GameMode` JSON | Rules, kit, rounds, timeouts | Drills become modes with their own win condition |
| `MatchState` (pure) | Countdown, rounds, best-of-N | Drill state machines follow the same pure, unit-testable pattern |
| `KitRegistry` | Slot-for-slot kits | Drill kits (blocks, water bucket, pearls) |
| `EloLadder` | Ratings per bot configuration and player | Per-drill personal bests and ratings |
| `Policy` (core) | Scripted brain | Drill-specific bot behaviours (moving aim targets, bridging rivals), and later ML policies |

## Drill types

| Drill | Scored on | Fails when |
|---|---|---|
| Bridging | Time from start to finish | Falling below the start height |
| Clutch | Surviving a fall (water bucket, cobweb, ladder) | Taking fall damage |
| Aim | Hit rate and damage against moving bot targets | — |
| Parkour | Time through ordered checkpoints | Missing a checkpoint |

Every drill resets its region from `blocks.nbt` between attempts, exactly like an arena.

## What will not change

- Bots are the same mortal players everywhere; a pack can't give them abilities.
- Without a pack installed, every command still works in any world. Arenas can be made by hand with
  `/sparbot arena create`.

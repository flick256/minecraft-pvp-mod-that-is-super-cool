# 4.0 validation and upgrade notes

This release keeps Minecraft 26.2, Java 25 and Fabric. The [feature guide](guide-4.0.md) covers the new menu flows and commands.

## Automated checks

- 232 core unit tests and three mod unit tests passed, including career persistence, interrupted promotions, invalid saves, legacy recordings, menu state compatibility and kit draft validation.
- All 146 required Minecraft server GameTests passed. Added cases exercise safe match cancellation, inventory and regeneration restoration, corrupt backup preservation, replay controls, event scoping, terrain construction, safe spawns and terrain snapshot restoration.
- The focused five-test recording suite also passed, including the event visibility command test. This checks both documented `on` and `off` syntax and the resulting menu review data.
- All seven match tests passed after fixing explosive cleanup, including a new check that reset discards crystals and TNT carts without detonating them, while leaving carts outside the arena alone.
- The real-client menu test passed through existing navigation and settings, a three-round career promotion, cancellation and retry, adaptation preferences, recording selection and playback controls, event jumps, a recommended drill, terrain rematch, and saving a Protection IV helmet as a new custom kit at a 320×240 GUI size. Compact editor widgets were checked for bounds and overlaps; screenshots are in the feature guide.
- The separate shutdown client test passed twice, closing integrated-server worlds with a live bot, waiting for each server to stop, and checking that the bot manager retained no bots.
- The isolated eight-bot performance check passed: mean rolling server tick time **8.53 ms**, peak rolling average **12.41 ms**, over 121 samples after 120 warmup ticks. The budget was 50 ms. This is a coarse measurement on the cloud test machine, not a hardware guarantee or a frame-rate measurement.

## Combat comparison

A 200-fight simulator holdout used seed 2026, alternating sides, flat terrain, the same PvPHQ UHC kit and a 5,000-tick limit. The baseline was source commit `f3632ae`.

| Build | Swight wins | Demon wins | Timeouts | Mean fight ticks |
|---|---:|---:|---:|---:|
| 3.2.1 baseline | 115 | 69 | 16 | 3,324 |
| 4.0 combat | 116 | 63 | 21 | 3,337 |

The small win-rate difference does not establish increased strength. Time spent in webs fell from 75.03 to 58.33 ticks per fight. The concrete changes reserve Swight's last two webs for useful openings and discard stale wall plans after an interruption. Reach, shield activation, reaction delays, inventory timing and finite supplies retain their existing limits. These are simulator results against another bot, not results against human players.

## Existing installations

- Replace the old SparBot jar rather than keeping both versions installed. Existing kit, profile and configuration files remain in use.
- Careers use a new, versioned `config/sparbot/career.json`. Missing saves start fresh. Invalid or unsupported saves are preserved and progression writes fail closed. Interrupted promotion series are cancelled without a result on load.
- Version 1 recordings remain readable. New recordings use version 2 with timestamped events. Replay mannequins pause at the final frame until explicitly stopped; they do not repeat attacks or place blocks.
- Inventory backups are staged before a match and restored only after all saved slots decode. A malformed backup remains available for recovery and is not silently replaced with empty slots.
- Terrain selection and rematch setup belong to the current server session. Changing terrain rebuilds only the selected practice court when it is clear, then snapshots that layout for round resets. Initial practice-world generation and terrain changes can cause a brief server stall.

Cloud validation uses Xvfb and software OpenGL. Offline Gradle checks use the existing external Loom metadata configuration with official cached artifacts and unchanged checksums. Lunar Client's launcher is unavailable here, so launcher recognition after exit still needs a real Lunar test.

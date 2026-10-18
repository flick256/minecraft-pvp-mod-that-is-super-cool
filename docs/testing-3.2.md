# 3.2 validation: menu redesign and Swight

The menu was exercised in a real Minecraft client with software rendering, including navigation,
spawning Swight, kit search and selection, equipping and saving a layout, technique toggles, healing,
settings changes, starting a UHC match, compact GUI sizes and keyboard focus. The regression also
checks that a server snapshot preserves a selected search range and an unfinished numeric edit.

![The redesigned menu](images/menu-overview-3.2.png)

![Choosing a Swight UHC fight](images/menu-practice-3.2.png)

## Automated checks

- 209 core tests and one connection cleanup test passed.
- All 133 required server Minecraft GameTests passed, including a live Swight-versus-Demon UHC fight.
- The client menu smoke test covers the actions described above and exits the game process cleanly.
- In the live UHC check, Swight used finite cobwebs, landed 29 ordinary hits at a mean distance of
  1.76 blocks, and had a longest blocking stretch of 13 ticks. Demon also landed hits; this check
  verifies behavior and limits, not a live-game win rate.

```sh
./gradlew build
./gradlew runClientGameTest -PmenuOnly
```

This cloud instance used Java 25, Xvfb and llvmpipe. Gradle ran offline with a supported Loom metadata
override outside the repository, reusing the previously verified official Minecraft metadata and
artifacts. Artifact URLs and checksums were unchanged. Optional online account-service lookups are
unavailable in the headless test environment.

## Independent Swight benchmarks

After development experiments, the unchanged candidate was tested on a separate seed set (`481`),
200 fights per scenario, alternating sides, with identical kits for both opponents and a 5,000-tick
limit. Swight's reaction, rotation, clicking and inventory speed limits match Demon. Its reach error
has a negative mean, nonzero variance and no positive allowance. All normal perception delays,
utility attention costs, vanilla bodies and finite inventory rules remain enabled.

| Kit | Arena | Swight wins | Demon wins | Timeouts |
|---|---|---:|---:|---:|
| PvPHQ UHC | Flat | 108 | 77 | 15 |
| PvPHQ UHC | Uneven | 100 | 86 | 14 |
| SparBot UHC | Flat | 127 | 69 | 4 |
| SparBot UHC | Uneven | 118 | 73 | 9 |
| **Total** | | **453** | **305** | **42** |

The result is approximately 60% of completed fights won, or 57% of all starts won with timeouts
counted separately. This is a simulator comparison against Demon, not a measurement against human
players. Swight is a first specialist release, not a claim of an established maximum human ceiling.

Reproduce each scenario by changing `kit` and `terrain`:

```sh
./gradlew :sparbot-core:uhcLab -Pfights=200 -Pa=swight -Pb=demon -Pkit=pvphq_uhc -Pterrain=false -PmaxTicks=5000 -Pseed=481
```

A separate 200-fight reach sample (seeds 77,000 onwards) recorded 13,131 Swight melee hits: mean
1.89 blocks, about 6% beyond 2.8 and 3% beyond 2.9, with 0% in the 3.0 bin. Sustained shield requests
are limited to 22 ticks followed by a six-tick release window; apples and potions are excluded from
that restriction. The unit regression forces sustained shield requests to verify those windows.

Full build, client, simulation, holdout and reach logs are retained locally in
`build/reports/3.2.0-validation/` (generated files excluded from Git).

# 3.1 validation (2026-10-18)

The full Gradle build passed with all 132 required Minecraft GameTests. This includes cart execution,
UHC fights, shield interactions and the new low/high PvPHQ cart buff preparation checks. A reproduced
32-tick cart stall was fixed by yielding to melee when no useful placement surface can be found.

Final unit tests: 203 core tests and one connection cleanup test passed. Final targeted Minecraft
run: all six buff/NoDebuff tests passed, including both full PvPHQ cart hotbars after buff preparation.
The unit checks cover pressure interrupting preparation, later restoration of the displaced item,
keeping active buffs in reserve, utility restocking, delayed attention and avoiding automatic melee
hits during precise placement.

Commands on a normally networked Java 25 environment:

```sh
./gradlew build
./gradlew test jar sourcesJar runGameTest '-PgametestFilter=sparbot-gametest:*buff*'
./gradlew :sparbot-core:uhcLab -Pfights=100 -Pa=pro -Pb=pro -Pkit=pvphq_uhc
```

This cloud instance reused official cached Minecraft version metadata through Loom's supported
`customMinecraftMetadata` setting in an external init script and ran Gradle offline. The metadata's
artifact URLs and checksums were unchanged. The project now sets Loom's unobfuscated mode explicitly
for Minecraft 26.2. The test selector requires a namespace and wildcard; an unqualified substring
selects no tests on this Minecraft version.

The same-seed 100-fight PvPHQ UHC comparison against 3.0.1 had mean durations of 3,176 vs 3,150 ticks,
with six vs five timeouts. Longest measured non-melee stall was 160 ticks in both runs, in retreat.
This checks for a broad regression; it does not measure realism against human recordings.

The jar contains the earlier shutdown fixes. Lunar launcher state was not retested in Lunar itself.
No server kit item layouts changed. Hidden slots, shulker contents and exact enchantments/potion
variants remain unverified as documented in [kit-research.md](kit-research.md).

Full build, final buff checks and simulation logs are retained locally under
`build/reports/3.1.0-validation/` (generated files, excluded from Git).

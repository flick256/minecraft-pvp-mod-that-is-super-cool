# SparBot 3.2.0

[Download sparbot-3.2.0.jar](sparbot-3.2.0.jar?raw=true)

For Minecraft Java 26.2, Java 25, Fabric Loader 0.19.3+ and Fabric API 0.161.0+26.2.
Replace the previous SparBot jar in your mods folder; keep Fabric API installed.

## A new practice menu

Persistent sidebar navigation, searchable kits/settings, a kit picker, remembered scroll positions,
and editor focus/selection that survive updates. Spawn and fight actions stay visible. Press B to open.

![New menu](menu-overview.png)

## Meet Swight

Choose **Overview > Challenge Swight**, select Swight in Practice, or run:

```text
/sparbot spawn Swight swight pvphq_uhc
```

Swight is a first UHC specialist release: approximate web/lava leading, shorter lava chains, and
follow-up pressure on trapped opponents or occupied bucket hands. Physical input and reaction limits
match Demon, with conservative reach judgement and release windows for sustained shielding.

In 800 held-out simulator fights, Swight won 453, Demon won 305, and 42 timed out. This is a simulator
comparison, not a measured win rate against real players. The mean reach in a separate 200-fight
sample was 1.89 blocks, with about 3% beyond 2.9.

![Choose a Swight fight](menu-practice.png)

Validation: 209 core tests plus one connection cleanup test passed; all 133 required server Minecraft
tests passed; the client menu regression passed and exited cleanly; the final focused live Swight
check passed. Source and full validation notes:

https://github.com/flick256/minecraft-pvp-mod-that-is-super-cool/tree/codex/ui-swight

Source commit: `eb65781e22f78da9edf985446f4518594d2fae2a`

SHA-256: `6908ae3d73c949f0910a264a54355f45f2760d9e437504a30d664150b1a8acb7`

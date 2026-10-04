# Testing M3 in game

## Install

1. Install Fabric Loader 0.19.5 (or newer) for Minecraft **26.2**.
2. Get the mod jar:
   - **From GitHub:** open the latest "build" run in the repo's Actions tab and download the `sparbot`
     artifact, or
   - **Build it yourself:** run `./gradlew build` with Java 25. The jar is `build/libs/sparbot-0.1.0.jar`.
3. Put that jar and Fabric API `0.161.0+26.2` in your `mods` folder.

Use a creative or flat test world with cheats on. **You** can be in creative while you set things up, but bots
only target survival players. Switch to survival with `/gamemode survival` before fighting one.

## Quick checks (about 10 minutes)

| What | Commands | What should happen |
|---|---|---|
| Sword duel | `/sparbot spawn Bob pro` | Bob walks at you, times his hits, crits, W-taps and strafes |
| Skill difference | `/sparbot spawn Noob beginner` | Slower, misses, spam-clicks, sometimes walks off edges |
| Full kit | `/sparbot spawn Kit pro sparbot_combat` | Bob has a sword, axe, shield, bow, crossbow, pearls, golden apples and a spare totem |
| Axe vs shield | Fight Kit while holding a shield up (right click) | Kit switches to the axe; your shield gets disabled (cooldown on your hotbar) |
| Block-hitting | Stand in melee with Kit | Kit raises the shield between its own swings |
| Bow | Back off 10+ blocks | Kit draws fully and shoots, leading you if you move |
| Guard | Draw a bow at Kit from range | Kit raises the shield and walks in |
| Pearl | Get 20+ blocks away (`/tp`) | Kit throws a pearl and lands near you |
| Golden apple | Hit Kit down to half health, then back off | Kit eats a golden apple (watch the absorption hearts) |
| Totem | `/sparbot kill` doesn't trigger totems; instead `/damage Kit 100` with a totem in its offhand | Totem pops, then Kit moves the spare totem into its offhand |
| Watch it think | `/sparbot info Kit` | Current tactic, all tactic scores, reaction delay, mistake state |
| Stats | `/sparbot stats Kit` | Hits, crits, ranged hits, totem pops, gapples, pearls, K/D |
| Your own kit | Set up your inventory, then `/sparbot kit capture mykit` | Saved; `/sparbot kit set Bob mykit` gives Bob exactly your items |
| Your own layout | Rearrange a kit in your inventory, then `/sparbot layout capture mine sparbot_combat` | `/sparbot layout set Kit mine` puts Kit's items in your slots |

## Reporting bugs

`/sparbot info <bot>` at the moment something looks wrong is the most useful thing to send, plus the
`[SparBot]` lines from the log. Turning on `logDecisions` in `config/sparbot.json` logs every tactic change.

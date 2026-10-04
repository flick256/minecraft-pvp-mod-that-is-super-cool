package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Full-kit UHC fights between two scripted pros in a 32 x 32 walled arena, the same as the simulator's
 * UHC arena, logging the same numbers the simulator's UhcLab prints (utility used, time spent in webs,
 * water and fire, what was left lying around). Comparing the two is how the simulator is checked
 * against the game.
 */
public class UhcParityGameTests {
	private static final int HALF = 16;
	private static final int SIZE = 2 * HALF + 2;
	private static final int FIGHT_TICKS = 3000;

	/** Inventory counts a fighter's utility is read from, tick to tick. */
	private static final class Watch {
		final Bot bot;
		int lavaPours;
		int waterPours;
		int scoops;
		int blocks;
		int webs;
		int arrows;
		int ticksWeb;
		int ticksWater;
		int ticksBurning;
		int ticksLava;
		int blocked;
		final java.util.Map<String, Integer> tactics = new java.util.TreeMap<>();
		int[] last;

		Watch(Bot bot) {
			this.bot = bot;
		}

		void tick() {
			BotPlayer p = bot.body();
			if (p == null || !p.isAlive()) {
				return;
			}
			Inventory inv = p.getInventory();
			int[] now = {count(inv, Items.LAVA_BUCKET), count(inv, Items.WATER_BUCKET), count(inv, Items.BUCKET), count(inv, Items.COBBLESTONE),
				count(inv, Items.COBWEB), count(inv, Items.ARROW)};
			if (last != null) {
				if (now[2] > last[2]) {
					lavaPours += Math.max(0, last[0] - now[0]);
					waterPours += Math.max(0, last[1] - now[1]);
				} else if (now[2] < last[2]) {
					scoops += last[2] - now[2];
				}
				blocks += Math.max(0, last[3] - now[3]);
				webs += Math.max(0, last[4] - now[4]);
				arrows += Math.max(0, last[5] - now[5]);
			}
			last = now;
			boolean web = p.level().getBlockStates(p.getBoundingBox().deflate(1.0E-3)).anyMatch(s -> s.is(Blocks.COBWEB));
			ticksWeb += web ? 1 : 0;
			ticksWater += p.isInWater() ? 1 : 0;
			ticksBurning += p.isOnFire() ? 1 : 0;
			ticksLava += p.isInLava() ? 1 : 0;
			tactics.merge(bot.trace().tactic(), 1, Integer::sum);
		}

		private static int count(Inventory inv, net.minecraft.world.item.Item item) {
			int n = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(item)) {
					n += s.getCount();
				}
			}
			return n;
		}

		String line() {
			return String.format("lavaPours=%d waterPours=%d scoops=%d blocks=%d webs=%d arrowsShot=%d arrowHits=%d hits=%d swings=%d crits=%d gapples=%d web=%d water=%d burning=%d lava=%d dealt=%.1f taken=%.1f tactics=%s",
				lavaPours, waterPours, scoops, blocks, webs, arrows, bot.stats().rangedHits(), bot.stats().hits(), bot.stats().swings(), bot.stats().crits(),
				bot.stats().gapplesEaten(), ticksWeb, ticksWater, ticksBurning, ticksLava, bot.stats().damageDealt(), bot.stats().damageTaken(), tactics);
		}
	}

	/** A stone floor on bedrock, the inside 32 x 32, glass walls 4 high around it. */
	private static void arena(GameTestHelper helper) {
		TestSupport.platform(helper, SIZE, SIZE);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, -1, z), Blocks.BEDROCK);
				if (x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1) {
					for (int y = 1; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
	}

	private static void fight(GameTestHelper helper, int round) {
		fight(helper, round, null);
	}

	/** As {@link #fight(GameTestHelper, int)}, with bot A fighting with the given model (null: scripted). */
	private static void fight(GameTestHelper helper, int round, String modelA) {
		arena(helper);
		double gap = 8 + round % 8;
		double cx = HALF + 1;
		Bot a = TestSupport.spawnBot(helper, modelA == null ? "ParityA" : "BrainA", cx - gap / 2, 1, cx, -90, "pro", "sparbot_uhc");
		if (modelA != null) {
			a.setModel(modelA, SparBot.models().get(modelA).orElseThrow());
		}
		Bot b = TestSupport.spawnBot(helper, "ParityB", cx + gap / 2, 1, cx, 90, "pro", "sparbot_uhc");
		a.setAssignedTarget(TestSupport.body(b).getUUID());
		b.setAssignedTarget(TestSupport.body(a).getUUID());
		// UHC rules, as in the uhc_duel mode and the simulator: no natural regeneration.
		io.github.flick256.sparbot.match.MatchRules.withoutRegeneration(TestSupport.body(a).getUUID(), true);
		io.github.flick256.sparbot.match.MatchRules.withoutRegeneration(TestSupport.body(b).getUUID(), true);
		Watch wa = new Watch(a);
		Watch wb = new Watch(b);
		boolean[] over = {false};
		helper.onEachTick(() -> {
			wa.tick();
			wb.tick();
			BotPlayer pa = a.body();
			BotPlayer pb = b.body();
			if (round == 3 && helper.getTick() % 4 == 0 && helper.getTick() <= 800 && pa != null && pb != null) {
				net.minecraft.world.phys.Vec3 o = helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO);
				SparBot.LOGGER.info(String.format("TRACE %4d | A %-8s hp%5.1f (%6.2f %5.2f %6.2f) %s | B %-8s hp%5.1f (%6.2f %5.2f %6.2f) %s d=%.1f", helper.getTick(),
					a.trace().tactic(), pa.getHealth() + pa.getAbsorptionAmount(), pa.getX() - o.x - cx, pa.getY() - o.y - 1, pa.getZ() - o.z - cx,
					pa.getMainHandItem().getItem().toString().replace("minecraft:", ""), b.trace().tactic(), pb.getHealth() + pb.getAbsorptionAmount(),
					pb.getX() - o.x - cx, pb.getY() - o.y - 1, pb.getZ() - o.z - cx, pb.getMainHandItem().getItem().toString().replace("minecraft:", ""),
					pa.distanceTo(pb)));
			}
			if (!over[0] && (pa == null || pb == null || !pa.isAlive() || !pb.isAlive() || helper.getTick() >= FIGHT_TICKS)) {
				over[0] = true;
				float ha = pa == null || !pa.isAlive() ? 0 : pa.getHealth() + pa.getAbsorptionAmount();
				float hb = pb == null || !pb.isAlive() ? 0 : pb.getHealth() + pb.getAbsorptionAmount();
				int water = 0;
				int lava = 0;
				for (int x = 1; x < SIZE - 1; x++) {
					for (int z = 1; z < SIZE - 1; z++) {
						for (int y = 1; y <= 6; y++) {
							var fluid = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(x, y, z)));
							water += fluid.is(FluidTags.WATER) ? 1 : 0;
							lava += fluid.is(FluidTags.LAVA) ? 1 : 0;
						}
					}
				}
				SparBot.LOGGER.info((modelA == null ? "UHC parity" : "UHC brain") + " round {}: ticks={} winner={} healthA={} healthB={} leftoverWater={} leftoverLava={} | A {} | B {}", round,
					helper.getTick(), ha > hb ? "A" : hb > ha ? "B" : "draw", ha, hb, water, lava, wa.line(), wb.line());
				if (pa != null) {
					io.github.flick256.sparbot.match.MatchRules.withoutRegeneration(pa.getUUID(), false);
				}
				if (pb != null) {
					io.github.flick256.sparbot.match.MatchRules.withoutRegeneration(pb.getUUID(), false);
				}
				TestSupport.remove(a, b);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound0(GameTestHelper helper) {
		fight(helper, 0);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound1(GameTestHelper helper) {
		fight(helper, 1);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound2(GameTestHelper helper) {
		fight(helper, 2);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound3(GameTestHelper helper) {
		fight(helper, 3);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound4(GameTestHelper helper) {
		fight(helper, 4);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound5(GameTestHelper helper) {
		fight(helper, 5);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound6(GameTestHelper helper) {
		fight(helper, 6);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void parityRound7(GameTestHelper helper) {
		fight(helper, 7);
	}

	/** A pro shooting a standing dummy 15 blocks away: shots and hits until it dies, to compare with the simulator. */
	@GameTest(maxTicks = 820, padding = 60)
	public void arrowParity(GameTestHelper helper) {
		arena(helper);
		double cx = HALF + 1;
		Bot shooter = TestSupport.certain(TestSupport.spawnBot(helper, "Archer", cx - 7.5, 1, cx, -90, "pro", "sparbot_uhc"));
		Bot dummy = TestSupport.dummy(helper, "Target", cx + 7.5, 1, cx, 90, "sparbot_uhc");
		shooter.setAssignedTarget(TestSupport.body(dummy).getUUID());
		Watch w = new Watch(shooter);
		boolean[] over = {false};
		helper.onEachTick(() -> {
			w.tick();
			BotPlayer t = dummy.body();
			if (!over[0] && (t == null || !t.isAlive() || helper.getTick() >= 800)) {
				over[0] = true;
				SparBot.LOGGER.info("Arrow parity: ticks={} dummyDead={} arrowsShot={} arrowHits={} tactics={}", helper.getTick(), t == null || !t.isAlive(), w.arrows,
					shooter.stats().rangedHits(), w.tactics);
				TestSupport.remove(shooter, dummy);
				helper.succeed();
			}
		});
	}

	/** As {@link #arrowParity}, with the dummy strafing sideways on a fixed path (1.5 blocks either way, every 3 s). */
	private static void movingArrowParity(GameTestHelper helper, int phase) {
		arena(helper);
		double cx = HALF + 1;
		Bot shooter = TestSupport.certain(TestSupport.spawnBot(helper, "Archer", cx - 7.5, 1, cx, -90, "pro", "sparbot_uhc"));
		Bot dummy = TestSupport.dummy(helper, "Target", cx + 7.5, 1, cx, 90, "sparbot_uhc");
		shooter.setAssignedTarget(TestSupport.body(dummy).getUUID());
		Watch w = new Watch(shooter);
		boolean[] over = {false};
		net.minecraft.world.phys.Vec3 home = helper.absoluteVec(new net.minecraft.world.phys.Vec3(cx + 7.5, 1, cx));
		java.util.Map<Integer, double[]> closest = new java.util.HashMap<>();
		helper.onEachTick(() -> {
			w.tick();
			BotPlayer t = dummy.body();
			if (t != null && t.isAlive()) {
				double z = home.z + 1.5 * Math.sin(2 * Math.PI * (helper.getTick() + 7 * phase) / 60.0);
				t.setPos(home.x, t.getY(), z);
				t.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
				// Where each arrow passes the dummy: the closest it gets to the middle of its body.
				for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(
					net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, t.getBoundingBox().inflate(30))) {
					double dx = arrow.getX() - t.getX();
					double dy = arrow.getY() - (t.getY() + 0.9);
					double dz = arrow.getZ() - t.getZ();
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					double[] best = closest.get(arrow.getId());
					if (best == null || d < best[0]) {
						closest.put(arrow.getId(), new double[] {d, dx, dy, dz, helper.getTick()});
					}
				}
			}
			if (!over[0] && (t == null || !t.isAlive() || helper.getTick() >= 800)) {
				over[0] = true;
				SparBot.LOGGER.info("Moving arrow parity: ticks={} dummyDead={} arrowsShot={} arrowHits={} tactics={}", helper.getTick(), t == null || !t.isAlive(),
					w.arrows, shooter.stats().rangedHits(), w.tactics);
				java.util.Set<Integer> alive = new java.util.HashSet<>();
				for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(
					net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, new net.minecraft.world.phys.AABB(home, home).inflate(40))) {
					alive.add(arrow.getId());
				}
				for (java.util.Map.Entry<Integer, double[]> e : closest.entrySet()) {
					double[] c = e.getValue();
					SparBot.LOGGER.info("Arrow pass: {} d={} dx={} dy={} dz={} tick={}", alive.contains(e.getKey()) ? "MISS" : "HIT", String.format("%.2f", c[0]),
						String.format("%.2f", c[1]), String.format("%.2f", c[2]), String.format("%.2f", c[3]), (int) c[4]);
				}
				TestSupport.remove(shooter, dummy);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity0(GameTestHelper helper) {
		movingArrowParity(helper, 0);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity1(GameTestHelper helper) {
		movingArrowParity(helper, 1);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity2(GameTestHelper helper) {
		movingArrowParity(helper, 2);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity3(GameTestHelper helper) {
		movingArrowParity(helper, 3);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity4(GameTestHelper helper) {
		movingArrowParity(helper, 4);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity5(GameTestHelper helper) {
		movingArrowParity(helper, 5);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity6(GameTestHelper helper) {
		movingArrowParity(helper, 6);
	}

	@GameTest(maxTicks = 820, padding = 60)
	public void movingArrowParity7(GameTestHelper helper) {
		movingArrowParity(helper, 7);
	}

	/**
	 * How long a pro takes to get out of a cobweb, by where the web is relative to its body: 0 at its feet,
	 * 1 diagonally next to it, 2 a two-high column next to it, 3 at its head next to it. An opponent stands
	 * 8 blocks away. Compared with the simulator's UhcLab escapes.
	 */
	private static void webEscape(GameTestHelper helper, int layout) {
		arena(helper);
		double cx = HALF + 1;
		// The body overlaps blocks (cx-1, cx) in x and z: x and z 0.25 into the block cx.
		Bot bot = TestSupport.spawnBot(helper, "Webbed", cx + 0.25, 1, cx + 0.25, -90, "pro", "sparbot_uhc");
		Bot dummy = TestSupport.dummy(helper, "Watcher", cx + 8.5, 1, cx + 0.5, 90, "sparbot_uhc");
		bot.setAssignedTarget(TestSupport.body(dummy).getUUID());
		int c = (int) cx;
		switch (layout) {
			case 0 -> helper.setBlock(new BlockPos(c, 1, c), Blocks.COBWEB);
			case 1 -> helper.setBlock(new BlockPos(c - 1, 1, c - 1), Blocks.COBWEB);
			case 2 -> {
				helper.setBlock(new BlockPos(c - 1, 1, c), Blocks.COBWEB);
				helper.setBlock(new BlockPos(c - 1, 2, c), Blocks.COBWEB);
			}
			default -> helper.setBlock(new BlockPos(c - 1, 2, c - 1), Blocks.COBWEB);
		}
		int[] freeAt = {-1};
		helper.onEachTick(() -> {
			BotPlayer p = bot.body();
			if (p == null) {
				return;
			}
			boolean web = p.level().getBlockStates(p.getBoundingBox().deflate(1.0E-3)).anyMatch(st -> st.is(Blocks.COBWEB));
			if (!web && freeAt[0] < 0 && helper.getTick() > 2) {
				freeAt[0] = (int) helper.getTick();
			}
			if (freeAt[0] >= 0 || helper.getTick() >= 400) {
				SparBot.LOGGER.info("Web escape layout {}: free after {} ticks", layout, freeAt[0]);
				TestSupport.remove(bot, dummy);
				helper.succeed();
			}
		});
	}

	/** How long a pro takes to put itself out after a moment in lava. */
	private static void fireEscape(GameTestHelper helper, int round) {
		arena(helper);
		double cx = HALF + 1;
		Bot bot = TestSupport.spawnBot(helper, "Burning", cx + 0.5, 1, cx + 0.5, -90, "pro", "sparbot_uhc");
		Bot dummy = TestSupport.dummy(helper, "Watcher", cx + 8.5, 1, cx + 0.5, 90, "sparbot_uhc");
		bot.setAssignedTarget(TestSupport.body(dummy).getUUID());
		int c = (int) cx;
		helper.runAfterDelay(10, () -> helper.setBlock(new BlockPos(c, 1, c), Blocks.LAVA));
		helper.runAfterDelay(14, () -> helper.setBlock(new BlockPos(c, 1, c), Blocks.AIR));
		int[] outAt = {-1};
		helper.onEachTick(() -> {
			BotPlayer p = bot.body();
			if (p == null || helper.getTick() < 16) {
				return;
			}
			if (!p.isOnFire() && outAt[0] < 0) {
				outAt[0] = (int) helper.getTick() - 14;
			}
			if (outAt[0] >= 0 || helper.getTick() >= 400) {
				SparBot.LOGGER.info("Fire escape {}: out after {} ticks, health {}", round, outAt[0], p.getHealth());
				TestSupport.remove(bot, dummy);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape0x0(GameTestHelper helper) {
		webEscape(helper, 0);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape0x1(GameTestHelper helper) {
		webEscape(helper, 0);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape0x2(GameTestHelper helper) {
		webEscape(helper, 0);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape1x0(GameTestHelper helper) {
		webEscape(helper, 1);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape1x1(GameTestHelper helper) {
		webEscape(helper, 1);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape1x2(GameTestHelper helper) {
		webEscape(helper, 1);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape2x0(GameTestHelper helper) {
		webEscape(helper, 2);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape2x1(GameTestHelper helper) {
		webEscape(helper, 2);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape2x2(GameTestHelper helper) {
		webEscape(helper, 2);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape3x0(GameTestHelper helper) {
		webEscape(helper, 3);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape3x1(GameTestHelper helper) {
		webEscape(helper, 3);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void webEscape3x2(GameTestHelper helper) {
		webEscape(helper, 3);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void fireEscape0(GameTestHelper helper) {
		fireEscape(helper, 0);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void fireEscape1(GameTestHelper helper) {
		fireEscape(helper, 1);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void fireEscape2(GameTestHelper helper) {
		fireEscape(helper, 2);
	}

	@GameTest(maxTicks = 420, padding = 60)
	public void fireEscape3(GameTestHelper helper) {
		fireEscape(helper, 3);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound0(GameTestHelper helper) {
		fight(helper, 0, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound1(GameTestHelper helper) {
		fight(helper, 1, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound2(GameTestHelper helper) {
		fight(helper, 2, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound3(GameTestHelper helper) {
		fight(helper, 3, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound4(GameTestHelper helper) {
		fight(helper, 4, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound5(GameTestHelper helper) {
		fight(helper, 5, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound6(GameTestHelper helper) {
		fight(helper, 6, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound7(GameTestHelper helper) {
		fight(helper, 7, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound8(GameTestHelper helper) {
		fight(helper, 8, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound9(GameTestHelper helper) {
		fight(helper, 9, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound10(GameTestHelper helper) {
		fight(helper, 10, "uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 60)
	public void brainRound11(GameTestHelper helper) {
		fight(helper, 11, "uhc");
	}
}

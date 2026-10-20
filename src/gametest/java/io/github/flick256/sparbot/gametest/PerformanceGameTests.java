package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** A coarse server-side tick budget check with four simultaneous bot fights. */
public final class PerformanceGameTests {
	// Minecraft averages over a rolling history; wait longer than that window after bot setup.
	private static final int WARMUP_TICKS = 120;
	private static final int MEASURE_TICKS = 120;
	private static final long TICK_BUDGET_NANOS = 50_000_000L;

	@GameTest(maxTicks = WARMUP_TICKS + MEASURE_TICKS + 20, padding = 40)
	public void eightBotsStayWithinServerTickBudget(GameTestHelper helper) {
		FightGameTests.ring(helper);
		// Keep the four fights in separate stone-floored courts so target selection and movement
		// cannot turn this into one eight-player melee.
		for (int y = 1; y <= 4; y++) {
			for (int p = 1; p < 15; p++) {
				helper.setBlock(new BlockPos(8, y, p), Blocks.GLASS);
				helper.setBlock(new BlockPos(p, y, 8), Blocks.GLASS);
			}
		}
		List<Bot> bots = new ArrayList<>(8);
		pair(helper, bots, 2.5, 3.5, 5.5, 5.5, 0);
		pair(helper, bots, 10.5, 3.5, 13.5, 5.5, 1);
		pair(helper, bots, 2.5, 10.5, 5.5, 13.5, 2);
		pair(helper, bots, 10.5, 10.5, 13.5, 13.5, 3);

		AtomicLong total = new AtomicLong();
		AtomicLong samples = new AtomicLong();
		AtomicLong peak = new AtomicLong();
		int[] elapsed = {0};
		helper.onEachTick(() -> {
			if (++elapsed[0] <= WARMUP_TICKS) {
				return;
			}
			long tickNanos = helper.getLevel().getServer().getAverageTickTimeNanos();
			total.addAndGet(tickNanos);
			samples.incrementAndGet();
			peak.accumulateAndGet(tickNanos, Math::max);
		});
		helper.runAfterDelay(WARMUP_TICKS + MEASURE_TICKS, () -> {
			long mean = total.get() / Math.max(1, samples.get());
			SparBot.LOGGER.info("Eight-bot server tick check: mean {} ms, peak rolling average {} ms over {} samples",
				mean / 1_000_000.0, peak.get() / 1_000_000.0, samples.get());
			long alive = bots.stream().filter(bot -> bot.body() != null && bot.body().isAlive()).count();
			TestSupport.remove(bots.toArray(Bot[]::new));
			helper.assertTrue(samples.get() >= MEASURE_TICKS - 2, "collected a full tick-time window");
			helper.assertTrue(alive >= 6, "at least three of the four fights remained active; alive bots=" + alive);
			helper.assertTrue(mean <= TICK_BUDGET_NANOS,
				"eight bots averaged " + mean / 1_000_000.0 + " ms per server tick (budget 50 ms)");
			helper.succeed();
		});
	}

	private static void pair(GameTestHelper helper, List<Bot> bots, double ax, double az, double bx, double bz, int court) {
		Bot a = TestSupport.spawnBot(helper, "PerfA" + court, ax, 1, az, -45, "demon", "pvphq_uhc");
		Bot b = TestSupport.spawnBot(helper, "PerfB" + court, bx, 1, bz, 135, "swight", "pvphq_uhc");
		a.setAssignedTarget(TestSupport.body(b).getUUID());
		b.setAssignedTarget(TestSupport.body(a).getUUID());
		bots.add(a);
		bots.add(b);
	}
}

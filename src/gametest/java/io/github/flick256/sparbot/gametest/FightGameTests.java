package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.bot.Bot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Whole fights between two bots with full kits, watched by {@link FightDiagnostics}: no bot may lock up
 * (stand frozen next to its opponent) at any point of the fight.
 */
public class FightGameTests {
	private static final int SIZE = 16;
	private static final int FIGHT_TICKS = 900;

	/**
	 * A 16x16 stone floor on bedrock (crystals blow holes in the stone, and a single layer would drop the
	 * fighters out of the world) with a 4-high glass ring, opened through the test's barrier walls.
	 */
	static void ring(GameTestHelper helper) {
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

	private static void fight(GameTestHelper helper, String profile, String kit) {
		ring(helper);
		Bot a = TestSupport.spawnBot(helper, "FightA", 4.5, 1, 7.5, -90, profile, kit);
		Bot b = TestSupport.spawnBot(helper, "FightB", 11.5, 1, 7.5, 90, profile, kit);
		a.setAssignedTarget(TestSupport.body(b).getUUID());
		b.setAssignedTarget(TestSupport.body(a).getUUID());
		FightDiagnostics da = new FightDiagnostics(a, b);
		FightDiagnostics db = new FightDiagnostics(b, a);
		helper.onEachTick(() -> {
			da.tick(helper);
			db.tick(helper);
		});
		helper.runAfterDelay(FIGHT_TICKS, () -> {
			da.log();
			db.log();
			int worst = Math.max(da.longestStall(), db.longestStall());
			TestSupport.remove(a, b);
			helper.assertTrue(worst <= FightDiagnostics.STALL_LIMIT, "a bot locked up for " + worst + " ticks:\n" + da.report() + "\n" + db.report());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void uhcProsNeverLockUp(GameTestHelper helper) {
		fight(helper, "pro", "sparbot_uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void uhcIntermediatesNeverLockUp(GameTestHelper helper) {
		fight(helper, "intermediate", "pvphq_uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void crystalProsNeverLockUp(GameTestHelper helper) {
		fight(helper, "pro", "sparbot_crystal");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void cartProsNeverLockUp(GameTestHelper helper) {
		fight(helper, "pro", "sparbot_cart");
	}
}

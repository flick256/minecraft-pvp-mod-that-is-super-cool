package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.BotTestAccess;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.match.MatchState;
import io.github.flick256.sparbot.match.Arena;
import io.github.flick256.sparbot.match.Match;
import io.github.flick256.sparbot.match.MatchManager;
import io.github.flick256.sparbot.match.MatchRules;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Milestone 6f: UHC rules and items (lava, water, cobwebs). */
public class UhcGameTests {
	private static final GameMode UHC_RULES = new GameMode("test_uhc", "UHC rules", "", "basic_sword", 1, 60, 1, "health", false);

	private static void hurtAndFeed(BotPlayer body) {
		body.setHealth(10);
		body.getFoodData().setFoodLevel(20);
		body.getFoodData().setSaturation(5);
	}

	@GameTest(maxTicks = 800)
	public void uhcRulesStopRegenerationForTheFightersOnly(GameTestHelper helper) throws Exception {
		Arena arena = MatchGameTests.arena(helper, "uhcarena");
		Bot a = TestSupport.spawnBot(helper, "UhcA", 2.5, 1, 3.5, -90, "beginner", "basic_sword");
		Bot b = TestSupport.spawnBot(helper, "UhcB", 5.5, 1, 4.5, 90, "beginner", "basic_sword");
		Bot bystander = TestSupport.dummy(helper, "Bystander", 1.5, 1, 6.5, 0, "basic_sword");
		AtomicReference<Match> done = new AtomicReference<>();
		SparBot.matches().start(helper.getLevel().getServer(), UHC_RULES, arena, MatchManager.fighterFor(a), MatchManager.fighterFor(b), done::set);
		BotPlayer[] bodies = new BotPlayer[3];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SparBot.matches().match(arena.id()).map(m -> m.state().phase() == MatchState.Phase.FIGHTING).orElse(false),
				"round started"))
			.thenExecute(() -> {
				BotTestAccess.pauseBrain(a, true);
				BotTestAccess.pauseBrain(b, true);
				bodies[0] = TestSupport.body(a);
				bodies[1] = TestSupport.body(b);
				bodies[2] = TestSupport.body(bystander);
				for (BotPlayer body : bodies) {
					hurtAndFeed(body);
				}
			})
			.thenIdle(60)
			.thenExecute(() -> {
				// Full hunger and saturation heal 1 health every 10 ticks, unless natural regeneration is off.
				helper.assertValueEqual(bodies[0].getHealth(), 10.0F, "fighter A's health (no natural regeneration)");
				helper.assertValueEqual(bodies[1].getHealth(), 10.0F, "fighter B's health (no natural regeneration)");
				helper.assertTrue(bodies[2].getHealth() > 10.0F, "a player outside the match still regenerates, has " + bodies[2].getHealth());
				TestSupport.remove(a, b, bystander);
			})
			.thenWaitUntil(() -> helper.assertTrue(done.get() != null, "the match ended"))
			.thenExecute(() -> helper.assertTrue(MatchRules.regenerates(bodies[0]) && MatchRules.regenerates(bodies[1]),
				"the rule is lifted when the match ends"))
			.thenSucceed();
	}

	@GameTest(maxTicks = 500, padding = 16)
	public void botPoursLavaOnItsOpponentAndScoopsItUp(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Lavaman", 1.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(6, ItemStack.EMPTY); // no webs: lava only
		body.getInventory().setItem(2, ItemStack.EMPTY); // no bow
		Bot target = TestSupport.dummy(helper, "Toast", 4.5, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		boolean[] burned = {false};
		helper.onEachTick(() -> {
			if (target.body() != null && target.body().isOnFire()) {
				burned[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(burned[0], "the opponent caught fire");
			helper.assertValueEqual(body.getInventory().countItem(Items.LAVA_BUCKET), 2, "lava buckets (scooped back up)");
			SparBot.LOGGER.info("Lava test: {}", bot.stats().summary());
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botPutsItselfOutWithWater(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Soggy", 3.5, 1, 3.5, 0, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		boolean[] wasOut = {false};
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> body.igniteForSeconds(10))
			.thenWaitUntil(() -> {
				helper.assertFalse(body.isOnFire(), "still burning");
				wasOut[0] = true;
			})
			.thenWaitUntil(() -> helper.assertValueEqual(body.getInventory().countItem(Items.WATER_BUCKET), 2, "water buckets (picked back up)"))
			.thenExecute(() -> {
				helper.assertTrue(wasOut[0], "the fire went out");
				TestSupport.remove(bot);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void botWebsItsOpponent(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Spider", 1.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(4, ItemStack.EMPTY); // no lava: webs only
		body.getInventory().setItem(10, ItemStack.EMPTY);
		Bot target = TestSupport.dummy(helper, "Fly", 4.5, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			BlockPos feet = TestSupport.body(target).blockPosition();
			helper.assertTrue(helper.getLevel().getBlockState(feet).is(Blocks.COBWEB), "a cobweb where the opponent stands");
			helper.assertTrue(body.getInventory().countItem(Items.COBWEB) < 8, "a cobweb was used up");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botWashesItselfOutOfAWeb(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Unstuck", 3.5, 1, 3.5, 0, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.COBWEB);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).isAir() || !helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.COBWEB),
				"the web is still there"))
			.thenWaitUntil(() -> helper.assertValueEqual(body.getInventory().countItem(Items.WATER_BUCKET), 2, "water buckets (picked back up)"))
			.thenExecute(() -> TestSupport.remove(bot))
			.thenSucceed();
	}

	@GameTest(maxTicks = 300, padding = 16)
	public void botPoursLavaOntoAWebbedOpponent(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Torch", 1.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(6, ItemStack.EMPTY); // no webs of its own
		Bot target = TestSupport.dummy(helper, "Stuck", 4.5, 1, 3.5, 90, "basic_sword");
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.COBWEB);
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(target.body() != null && target.body().isOnFire(), "the webbed opponent burns");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botStunsAShieldAndHitsAtOnce(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Stunner", 2.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		for (int slot : new int[] {2, 3, 4, 5, 6, 7}) {
			body.getInventory().setItem(slot, ItemStack.EMPTY); // sword and axe only
		}
		Bot target = TestSupport.dummy(helper, "Turtle", 4.5, 1, 3.5, 90, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		bot.setAssignedTarget(t.getUUID());
		long[] disabledAt = {-1};
		float[] healthAtDisable = {0};
		helper.onEachTick(() -> {
			if (!t.isUsingItem() && disabledAt[0] < 0 && !t.getCooldowns().isOnCooldown(t.getOffhandItem())) {
				t.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND); // keeps the shield raised until it is disabled
			}
			if (disabledAt[0] < 0 && t.getCooldowns().isOnCooldown(t.getOffhandItem())) {
				disabledAt[0] = helper.getTick();
				healthAtDisable[0] = t.getHealth();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(disabledAt[0] >= 0, "the axe disabled the shield");
			helper.assertTrue(t.getHealth() < healthAtDisable[0], "then a hit landed");
			helper.assertTrue(helper.getTick() - disabledAt[0] <= 10, "right after the stun, " + (helper.getTick() - disabledAt[0]) + " ticks later");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botWallsOffToHeal(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Mason", 2.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Rusher", 6.5, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int blocksBefore = body.getInventory().countItem(Items.COBBLESTONE);
		int gapplesBefore = body.getInventory().countItem(Items.GOLDEN_APPLE);
		// Hurt mid-fight, once the bot has seen its opponent.
		helper.runAfterDelay(20, () -> body.setHealth(8));
		helper.succeedWhen(() -> {
			helper.assertTrue(blocksBefore - body.getInventory().countItem(Items.COBBLESTONE) >= 4, "a wall of at least four blocks, used "
				+ (blocksBefore - body.getInventory().countItem(Items.COBBLESTONE)) + ", now " + bot.trace().tactic() + " " + bot.trace().note());
			helper.assertTrue(body.getInventory().countItem(Items.GOLDEN_APPLE) < gapplesBefore, "then a golden apple eaten behind it");
			boolean wall = false;
			for (int x = 1; x <= 6 && !wall; x++) {
				for (int z = 1; z <= 6 && !wall; z++) {
					wall = helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.COBBLESTONE) && helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.COBBLESTONE);
				}
			}
			helper.assertTrue(wall, "a two-high wall stands between them");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botScoopsUpLeftoverWater(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Tidy", 1.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		for (int slot : new int[] {2, 4, 6}) {
			body.getInventory().setItem(slot, ItemStack.EMPTY); // no bow, lava or webs
		}
		body.getInventory().setItem(5, new ItemStack(Items.BUCKET)); // its water is already down
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
		Bot target = TestSupport.dummy(helper, "Far", 6.5, 1, 6.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int waterBefore = body.getInventory().countItem(Items.WATER_BUCKET);
		helper.succeedWhen(() -> {
			helper.assertTrue(!helper.getBlockState(new BlockPos(3, 1, 3)).getFluidState().isSource(), "the water was scooped up");
			helper.assertValueEqual(body.getInventory().countItem(Items.WATER_BUCKET), waterBefore + 1, "water buckets");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botBlocksUpLavaNextToIt(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Mason2", 1.5, 1, 3.5, -90, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		for (int slot : new int[] {2, 4, 5, 6, 10, 11}) {
			body.getInventory().setItem(slot, ItemStack.EMPTY); // no bow, no buckets at all, no webs: only blocks
		}
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.LAVA);
		Bot target = TestSupport.dummy(helper, "Far2", 6.5, 1, 6.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(!helper.getBlockState(new BlockPos(3, 1, 3)).getFluidState().isSource(), "the lava was blocked up");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300)
	public void botSwimsOutOfAWaterPocket(GameTestHelper helper) {
		TestSupport.arena(helper);
		// A raised floor with a one-block water pocket in it.
		for (int x = 1; x < 7; x++) {
			for (int z = 1; z < 7; z++) {
				helper.setBlock(new BlockPos(x, 1, z), x == 2 && z == 3 ? Blocks.WATER : Blocks.STONE);
			}
		}
		Bot bot = TestSupport.spawnBot(helper, "Swimmer", 2.5, 1, 3.5, -90, "pro", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Dry", 5.5, 2, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(body.getY() >= helper.absoluteVec(new net.minecraft.world.phys.Vec3(0, 2, 0)).y - 0.01 && !body.isInWater(),
				"climbed out of the water onto the floor");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 300, padding = 32)
	public void botBlockBoostsBackIntoTheFight(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 20);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Booster", 3.5, 1, 2.5, 0, "pro", "sparbot_uhc"));
		BotPlayer body = TestSupport.body(bot);
		for (int slot : new int[] {2, 4, 5, 6}) {
			body.getInventory().setItem(slot, ItemStack.EMPTY); // no bow, buckets or webs: blocks only
		}
		Bot target = TestSupport.dummy(helper, "Gap", 3.5, 1, 11.5, 180, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int blocksBefore = body.getInventory().countItem(Items.COBBLESTONE);
		double ground = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0, 1, 0)).y;
		boolean[] stoodOnStep = {false};
		helper.onEachTick(() -> {
			if (body.onGround() && body.getY() > ground + 0.9) {
				stoodOnStep[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(body.getInventory().countItem(Items.COBBLESTONE) < blocksBefore, "placed a step");
			helper.assertTrue(stoodOnStep[0], "jumped onto it");
			TestSupport.remove(bot, target);
		});
	}
}

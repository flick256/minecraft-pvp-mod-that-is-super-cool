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
}

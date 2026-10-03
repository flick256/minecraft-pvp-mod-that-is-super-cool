package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/** Milestone 6e: cart PvP. The rail and the cart are placed with real clicks and the cart is set off by a real Flame arrow. */
public class CartGameTests {
	@GameTest(maxTicks = 500, padding = 24)
	public void botRailsCartsAndShootsIt(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Carter", 1.5, 1, 3.5, -90, "pro", "sparbot_cart"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Victim", 5.0, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int railsBefore = body.getInventory().countItem(Items.RAIL);
		int cartsBefore = body.getInventory().countItem(Items.TNT_MINECART);
		int arrowsBefore = body.getInventory().countItem(Items.ARROW);
		helper.succeedWhen(() -> {
			helper.assertTrue(body.getInventory().countItem(Items.RAIL) < railsBefore, "a rail was placed");
			helper.assertTrue(body.getInventory().countItem(Items.TNT_MINECART) < cartsBefore, "a TNT minecart was placed");
			helper.assertTrue(body.getInventory().countItem(Items.ARROW) < arrowsBefore, "an arrow was shot");
			helper.assertTrue(bot.stats().blastHits() > 0, "the cart's blast hurt the opponent");
			SparBot.LOGGER.info("Cart test: {}", bot.stats().summary());
			TestSupport.remove(bot, target);
		});
	}
}

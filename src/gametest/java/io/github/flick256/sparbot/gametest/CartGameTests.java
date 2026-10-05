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
		// In netherite, so the (maxed) sword doesn't finish it before the cart goes off.
		Bot target = TestSupport.dummy(helper, "Victim", 5.0, 1, 3.5, 90, "sparbot_cart");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int railsBefore = body.getInventory().countItem(Items.RAIL);
		int cartsBefore = body.getInventory().countItem(Items.TNT_MINECART);
		// What happened, tick by tick (the bow has Infinity, so arrows aren't used up).
		boolean[] seen = new boolean[3];
		FightDiagnostics watch = new FightDiagnostics(bot, target);
		helper.onEachTick(() -> {
			watch.tick(helper);
			seen[0] |= body.getInventory().countItem(Items.RAIL) < railsBefore;
			seen[1] |= body.getInventory().countItem(Items.TNT_MINECART) < cartsBefore;
			seen[2] |= !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, body.getBoundingBox().inflate(12),
				a -> a.getOwner() == body).isEmpty();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(seen[0], "a rail was placed\n" + watch.report());
			helper.assertTrue(seen[1], "a TNT minecart was placed\n" + watch.report());
			helper.assertTrue(seen[2], "an arrow was shot\n" + watch.report());
			helper.assertTrue(bot.stats().blastHits() > 0, "the cart's blast hurt the opponent\n" + watch.report());
			SparBot.LOGGER.info("Cart test: {}", bot.stats().summary());
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 200, padding = 24)
	public void botKnocksOutACartDroppedNextToIt(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Defuser", 1.5, 1, 3.5, -90, "pro", "sparbot_cart"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Carter", 6.0, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		body.getInventory().setItem(2, net.minecraft.world.item.ItemStack.EMPTY); // no rails: only defending here
		// The opponent's cart, on a rail right by the bot (a Flame arrow would be on its way).
		net.minecraft.core.BlockPos railPos = new net.minecraft.core.BlockPos(2, 1, 5);
		helper.runAfterDelay(20, () -> {
			helper.setBlock(railPos, net.minecraft.world.level.block.Blocks.RAIL);
			net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1.0625, 5.5));
			net.minecraft.world.entity.vehicle.minecart.MinecartTNT cart = new net.minecraft.world.entity.vehicle.minecart.MinecartTNT(
				net.minecraft.world.entity.EntityTypes.TNT_MINECART, helper.getLevel());
			cart.setPos(at.x, at.y, at.z);
			helper.getLevel().addFreshEntity(cart);
		});
		int cartsBefore = body.getInventory().countItem(Items.TNT_MINECART);
		helper.runAfterDelay(25, () -> helper.succeedWhen(() -> {
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(helper.absolutePos(railPos)).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.vehicle.minecart.MinecartTNT.class, box).isEmpty(),
				"the cart is gone, now " + bot.trace().tactic() + " " + bot.trace().note());
			// Knocked out, it drops as an item (which the bot may well have picked up); set off, it would be gone.
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
				e -> e.getItem().is(Items.TNT_MINECART)).isEmpty() || body.getInventory().countItem(Items.TNT_MINECART) > cartsBefore,
				"knocked out (it dropped as an item), not set off");
			helper.assertValueEqual(body.getHealth(), body.getMaxHealth(), "the bot's health");
			TestSupport.remove(bot, target);
		}));
	}
}

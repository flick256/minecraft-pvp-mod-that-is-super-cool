package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/** Swight must execute its UHC plan through normal Minecraft bodies and finite kit items. */
public class SwightGameTests {
	@GameTest(maxTicks = 1220, padding = 40)
	public void swightTradesWithDemonUsesUtilityAndDoesNotLockUp(GameTestHelper helper) {
		FightGameTests.ring(helper);
		Bot swight = TestSupport.spawnBot(helper, "Swight", 4.5, 1, 7.5, -90, "swight", "pvphq_uhc");
		Bot demon = TestSupport.spawnBot(helper, "Demon", 11.5, 1, 7.5, 90, "demon", "pvphq_uhc");
		swight.setAssignedTarget(TestSupport.body(demon).getUUID());
		demon.setAssignedTarget(TestSupport.body(swight).getUUID());
		FightDiagnostics diagnostic = new FightDiagnostics(swight, demon);
		int webs = TestSupport.body(swight).getInventory().countItem(Items.COBWEB);
		AtomicBoolean usedUtility = new AtomicBoolean();
		AtomicInteger shieldRun = new AtomicInteger(), longestShield = new AtomicInteger();
		helper.onEachTick(() -> {
			diagnostic.tick(helper);
			var body = swight.body();
			if (body == null || !body.isAlive()) { return; }
			if (body.getInventory().countItem(Items.COBWEB) < webs) { usedUtility.set(true); }
			int run = body.isBlocking() ? shieldRun.incrementAndGet() : 0;
			if (!body.isBlocking()) { shieldRun.set(0); }
			longestShield.accumulateAndGet(run, Math::max);
		});
		helper.runAfterDelay(1200, () -> {
			diagnostic.log();
			SparBot.LOGGER.info("Swight live check: {} vs {}, utility={}, longest shield={} ticks", swight.stats().summary(), demon.stats().summary(),
				usedUtility.get(), longestShield.get());
			TestSupport.remove(swight, demon);
			helper.assertTrue(swight.stats().hits() >= 3 && demon.stats().hits() >= 3, "both sides traded ordinary hits");
			helper.assertTrue(usedUtility.get(), "Swight used a finite cobweb from its kit");
			helper.assertTrue(diagnostic.longestStall() <= FightDiagnostics.STALL_LIMIT, "Swight did not freeze: " + diagnostic.report());
			helper.assertTrue(longestShield.get() <= 26, "sustained shielding has release windows, longest " + longestShield.get());
			helper.succeed();
		});
	}
	@GameTest(maxTicks = 160)
	public void swightHitsTheExposedBodyBelowAHeadHeightWeb(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot swight = TestSupport.spawnBot(helper, "WebAngle", 2.5, 1, 3.5, -90, "swight", "pvphq_uhc");
		var body = TestSupport.body(swight);
		for (int slot = 0; slot < 36; slot++) { body.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY); }
		body.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Items.DIAMOND_SWORD));
		body.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Items.WATER_BUCKET));
		body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
		Bot target = TestSupport.dummy(helper, "Webbed", 5.5, 1, 3.5, 90, "basic_sword");
		var web = new net.minecraft.core.BlockPos(5, 2, 3);
		helper.setBlock(web, net.minecraft.world.level.block.Blocks.COBWEB);
		swight.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(swight.stats().hits() > 0, "Swight found an exposed part of the real hitbox through normal aim and clicks");
			helper.assertTrue(helper.getBlockState(web).is(net.minecraft.world.level.block.Blocks.COBWEB), "the obstructing web was not removed to pass the test");
			TestSupport.remove(swight, target);
		});
	}

}

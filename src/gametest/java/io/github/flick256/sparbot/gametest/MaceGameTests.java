package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Milestone 6b: mace smashes and wind charge launches. */
public class MaceGameTests {
	private static void unarmor(BotPlayer body) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			body.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	@GameTest(maxTicks = 200)
	public void maceSmashAddsVanillaFallDamage(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot attacker = TestSupport.dummy(helper, "Smasher", 2.5, 1, 3.5, -90, "basic_sword");
		Bot victim = TestSupport.dummy(helper, "Anvil", 4.5, 1, 3.5, 90, "basic_sword");
		BotPlayer a = TestSupport.body(attacker);
		BotPlayer v = TestSupport.body(victim);
		unarmor(v);
		a.getInventory().setItem(0, new ItemStack(Items.MACE));
		float[] damage = new float[2];
		// A big smash must not be capped by 20 health (absorption cannot help: 26.2 caps it by the
		// MAX_ABSORPTION attribute), so give the victim 100 max health.
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		v.setHealth(100);
		helper.startSequence()
			.thenIdle(40)
			.thenExecute(() -> {
				a.attack(v);
				damage[0] = 100 - v.getHealth();
				v.setHealth(100);
				v.invulnerableTime = 0;
			})
			.thenIdle(40)
			.thenExecute(() -> {
				a.fallDistance = 5.0; // as if falling from 5 blocks
				a.attack(v);
				damage[1] = 100 - v.getHealth();
				// MaceItem: 4 per block for 3 blocks + 2 per block for the next 2 = 16 bonus.
				helper.assertTrue(Math.abs(damage[1] - damage[0] - 16.0F) < 0.01F, "smash bonus " + (damage[1] - damage[0]) + " (expected 16)");
				helper.assertTrue(a.fallDistance == 0, "a smash resets the attacker's fall distance");
				TestSupport.remove(attacker, victim);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 600, padding = 16)
	public void botLaunchesWithAWindChargeAndSmashes(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 8);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Macer", 2.5, 1, 3.5, -90, "pro", "sparbot_mace"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Bonk", 5.5, 1, 3.5, 90, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		unarmor(t);
		bot.setAssignedTarget(t.getUUID());
		double startY = body.getY();
		int windBefore = body.getInventory().countItem(Items.WIND_CHARGE);
		double[] peak = {startY};
		float[] biggestHit = {0};
		AtomicReference<Float> lastHealth = new AtomicReference<>(t.getHealth());
		helper.onEachTick(() -> {
			if (bot.body() == null || target.body() == null) {
				return;
			}
			peak[0] = Math.max(peak[0], bot.body().getY());
			float h = target.body().getHealth();
			biggestHit[0] = Math.max(biggestHit[0], lastHealth.get() - h);
			lastHealth.set(h);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(body.getInventory().countItem(Items.WIND_CHARGE) < windBefore, "a wind charge was used");
			helper.assertTrue(peak[0] - startY > 2.5, "launched into the air, peak " + (peak[0] - startY));
			// An unarmored target: a plain mace hit is ~6, a smash adds 4 per fallen block.
			helper.assertTrue(biggestHit[0] >= 11, "landed a smash, biggest hit " + biggestHit[0]);
			SparBot.LOGGER.info("Mace test: peak +{}, biggest hit {}", peak[0] - startY, biggestHit[0]);
			TestSupport.remove(bot, target);
		});
	}
}

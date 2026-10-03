package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/** Milestone 6a: NoDebuff (splash healing, refills, buffs). */
public class NoDebuffGameTests {
	private static int healingPots(BotPlayer body) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = body.getInventory().getItem(i);
			if (s.is(Items.SPLASH_POTION)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean hotbarHasPot(BotPlayer body) {
		for (int i = 0; i < 9; i++) {
			if (body.getInventory().getItem(i).is(Items.SPLASH_POTION)) {
				return true;
			}
		}
		return false;
	}

	@GameTest(maxTicks = 200)
	public void lowBotPotsDownAndHeals(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Potter", 3.5, 1, 3.5, 0, "pro", "sparbot_nodebuff");
		BotPlayer body = TestSupport.body(bot);
		int potsBefore = healingPots(body);
		body.setHealth(7);
		helper.succeedWhen(() -> {
			helper.assertTrue(healingPots(body) < potsBefore, "a healing potion was used up");
			helper.assertTrue(body.getHealth() >= 13, "and healed (Instant Health II at the feet is +8), health " + body.getHealth());
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 300)
	public void botRefillsItsHotbarWithPotsWhenSafe(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Refill", 3.5, 1, 3.5, 0, "pro", "sparbot_nodebuff");
		BotPlayer body = TestSupport.body(bot);
		for (int i = 4; i <= 7; i++) {
			body.getInventory().setItem(i, ItemStack.EMPTY);
		}
		int total = healingPots(body);
		helper.succeedWhen(() -> {
			helper.assertTrue(hotbarHasPot(body), "pots moved from the inventory to the hotbar");
			helper.assertValueEqual(healingPots(body), total, "moving pots never creates or destroys any");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 300)
	public void botDrinksSpeedAndFireResistance(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Drinker", 3.5, 1, 3.5, 0, "pro", "sparbot_nodebuff");
		BotPlayer body = TestSupport.body(bot);
		helper.succeedWhen(() -> {
			helper.assertTrue(body.hasEffect(MobEffects.SPEED), "drank Speed II");
			helper.assertTrue(body.hasEffect(MobEffects.FIRE_RESISTANCE), "drank Fire Resistance");
			helper.assertTrue(body.getInventory().getItem(2).isEmpty() || !body.getInventory().getItem(2).is(Items.POTION)
				|| body.getInventory().getItem(2).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion().isEmpty(),
				"the drunk potion is gone (glass bottle or empty)");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 2400)
	public void noDebuffDuelUsesPotsOnBothSides(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot a = TestSupport.spawnBot(helper, "NdA", 2.5, 1, 3.5, -90, "pro", "sparbot_nodebuff");
		Bot b = TestSupport.spawnBot(helper, "NdB", 5.5, 1, 4.5, 90, "advanced", "sparbot_nodebuff");
		a.setAssignedTarget(TestSupport.body(b).getUUID());
		b.setAssignedTarget(TestSupport.body(a).getUUID());
		int potsA = healingPots(TestSupport.body(a));
		int potsB = healingPots(TestSupport.body(b));
		AtomicBoolean someoneDied = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (a.body() != null && a.body().isDeadOrDying() || b.body() != null && b.body().isDeadOrDying()) {
				someoneDied.set(true);
			}
		});
		helper.succeedWhen(() -> {
			boolean aPotted = someoneDied.get() || healingPots(a.body()) < potsA;
			boolean bPotted = someoneDied.get() || healingPots(b.body()) < potsB;
			helper.assertTrue(aPotted && bPotted, "both sides potted (or the fight ended)");
			helper.assertTrue(a.stats().hits() + b.stats().hits() > 5, "they fought");
			SparBot.LOGGER.info("NoDebuff duel: {} [{}] vs {} [{}]", a.name(), a.stats().summary(), b.name(), b.stats().summary());
			TestSupport.remove(a, b);
		});
	}
}

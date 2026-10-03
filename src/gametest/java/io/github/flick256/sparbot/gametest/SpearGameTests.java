package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Milestone 6c: spear jabs and charges. */
public class SpearGameTests {
	private static void unarmor(BotPlayer body) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			body.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	private static void move(GameTestHelper helper, BotPlayer body, double x) {
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1, 3.5));
		body.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, Set.of(), 90, 0, true);
	}

	/** Exactly what a vanilla client sends for a left click with a spear (MultiPlayerGameMode#piercingAttack). */
	private static void stab(BotPlayer player) {
		player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STAB, BlockPos.ZERO, Direction.DOWN));
	}

	/**
	 * The facts SpearTactic is built on, checked against the running game: a jab needs a full charge,
	 * hits from 2 to 4.5 blocks, and deals the spear's attack damage (netherite: 1 + 4).
	 */
	@GameTest(maxTicks = 300)
	public void spearJabReachesFromTwoToFourAndAHalfBlocks(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot attacker = TestSupport.dummy(helper, "Jabber", 1.5, 1, 3.5, -90, "basic_sword");
		Bot victim = TestSupport.dummy(helper, "Pincushion", 4.5, 1, 3.5, 90, "basic_sword");
		BotPlayer a = TestSupport.body(attacker);
		BotPlayer v = TestSupport.body(victim);
		unarmor(v);
		a.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
		a.getInventory().setSelectedSlot(0);
		float[] damage = new float[4];
		helper.startSequence()
			.thenIdle(40)
			.thenExecute(() -> {
				a.resetAttackStrengthTicker();
				stab(a);
				damage[0] = 20 - v.getHealth();
			})
			.thenIdle(40)
			.thenExecute(() -> {
				// Centre 3 blocks away: inside the jab's reach.
				stab(a);
				damage[1] = 20 - v.getHealth();
				v.setHealth(20);
				v.invulnerableTime = 0;
				move(helper, v, 2.7);
				v.setDeltaMovement(Vec3.ZERO);
			})
			.thenIdle(40)
			.thenExecute(() -> {
				// Centre 1.2 blocks away: inside the 2-block dead zone.
				stab(a);
				damage[2] = 20 - v.getHealth();
				move(helper, v, 6.8);
				v.setDeltaMovement(Vec3.ZERO);
			})
			.thenIdle(40)
			.thenExecute(() -> {
				// Centre 5.3 blocks away: beyond 4.5.
				stab(a);
				damage[3] = 20 - v.getHealth();
				helper.assertTrue(damage[0] == 0, "no jab without a full charge, took " + damage[0]);
				helper.assertTrue(Math.abs(damage[1] - 5.0F) < 0.01F, "a full netherite spear jab deals 5, took " + damage[1]);
				helper.assertTrue(damage[2] == 0, "nothing inside 2 blocks is hit, took " + damage[2]);
				helper.assertTrue(damage[3] == 0, "nothing beyond 4.5 blocks is hit, took " + damage[3]);
				TestSupport.remove(attacker, victim);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void botJabsLikeAPlayer(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Lancer", 1.5, 1, 3.5, -90, "pro", "sparbot_spear"));
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR)); // unenchanted: a jab is exactly 5
		body.getInventory().setItem(1, ItemStack.EMPTY);
		Bot target = TestSupport.dummy(helper, "Hay", 5.0, 1, 3.5, 90, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		unarmor(t);
		bot.setAssignedTarget(t.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(bot.stats().hits() >= 2, "jabbed twice, hits " + bot.stats().hits());
			double perHit = bot.stats().damageDealt() / bot.stats().hits();
			helper.assertTrue(Math.abs(perHit - 5.0) < 0.01, "every jab dealt the vanilla 5, average " + perHit);
			helper.assertTrue(body.getMainHandItem().has(DataComponents.PIERCING_WEAPON), "fought with the spear");
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 600, padding = 32)
	public void botSprintsInWithASpearCharge(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 24);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Charger", 3.5, 1, 2.5, 0, "pro", "sparbot_spear"));
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SPEAR));
		Bot target = TestSupport.dummy(helper, "Bull", 3.5, 1, 15.5, 180, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		unarmor(t);
		bot.setAssignedTarget(t.getUUID());
		float[] chargeHit = {0};
		float[] lastHealth = {t.getHealth()};
		helper.onEachTick(() -> {
			if (bot.body() == null || target.body() == null) {
				return;
			}
			BotPlayer b = bot.body();
			float h = target.body().getHealth();
			if (h < lastHealth[0] && b.isUsingItem() && b.getUseItem().has(DataComponents.KINETIC_WEAPON)) {
				chargeHit[0] = Math.max(chargeHit[0], lastHealth[0] - h);
			}
			lastHealth[0] = h;
		});
		helper.succeedWhen(() -> {
			// KineticWeapon: 1 + floor(closing speed x 1.2). The 4.6 blocks/s damage threshold alone
			// gives at least 1 + 5 = 6, more than a plain jab's 5.
			helper.assertTrue(chargeHit[0] >= 6, "a charge hit landed, " + chargeHit[0]);
			helper.assertTrue(bot.stats().hits() > 0, "the charge hit counts in the bot's stats");
			SparBot.LOGGER.info("Spear charge test: charge hit {}", chargeHit[0]);
			TestSupport.remove(bot, target);
		});
	}
}

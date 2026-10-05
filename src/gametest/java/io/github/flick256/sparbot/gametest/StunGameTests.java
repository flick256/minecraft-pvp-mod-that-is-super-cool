package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shield stuns between two players (bot bodies, paused, driven with the packets a client sends): an axe
 * hit on a raised shield disables it, and the second click right after counts as a hit of its own, with
 * its knockback (the shieldStuns setting, on by default).
 */
public class StunGameTests {
	private static void stun(GameTestHelper helper, int gapTicks) {
		TestSupport.arena(helper);
		Bot a = TestSupport.dummy(helper, "Axer", 2.5, 1, 3.5, -90, "basic_sword");
		Bot b = TestSupport.dummy(helper, "Blocker", 4.5, 1, 3.5, 90, "basic_sword");
		BotPlayer attacker = TestSupport.body(a);
		BotPlayer blocker = TestSupport.body(b);
		attacker.getInventory().setItem(0, new ItemStack(Items.DIAMOND_AXE));
		attacker.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
		attacker.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(0));
		blocker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		blocker.getInventory().setItem(0, ItemStack.EMPTY);
		helper.runAfterDelay(20, () -> blocker.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 1, blocker.getYRot(),
			blocker.getXRot())));
		float[] before = new float[1];
		helper.runAfterDelay(45, () -> {
			before[0] = blocker.getHealth();
			boolean wasBlocking = blocker.isBlocking();
			attacker.connection.handleAttack(new ServerboundAttackPacket(blocker.getId()));
			float afterAxe = blocker.getHealth();
			boolean disabled = blocker.getCooldowns().isOnCooldown(blocker.getOffhandItem());
			helper.assertTrue(wasBlocking, "the shield was up");
			helper.assertTrue(disabled, "the axe disabled the shield");
			helper.assertValueEqual(afterAxe, before[0], "a blocked hit does no damage");
			Runnable followUp = () -> {
				attacker.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
				attacker.connection.handleAttack(new ServerboundAttackPacket(blocker.getId()));
				helper.assertTrue(blocker.hurtTime > 0 && blocker.getHealth() < before[0], "the follow-up landed as a hit of its own (knockback, damage)");
				SparBot.LOGGER.info("Stun test (follow-up after {} ticks): blocking {}, health {} -> {} after the axe (shield disabled {}), {} after the sword;"
					+ " invulnerableTime {}, attacker charge {}", gapTicks, wasBlocking, before[0], afterAxe, disabled, blocker.getHealth(),
					blocker.invulnerableTime, attacker.getAttackStrengthScale(0.5F));
			};
			if (gapTicks == 0) {
				followUp.run();
			} else {
				helper.runAfterDelay(gapTicks, followUp);
			}
		});
		helper.runAfterDelay(60, () -> {
			TestSupport.remove(a, b);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void stunSameTick(GameTestHelper helper) {
		stun(helper, 0);
	}

	@GameTest(maxTicks = 80)
	public void stunNextTick(GameTestHelper helper) {
		stun(helper, 1);
	}

	@GameTest(maxTicks = 80)
	public void stunFourTicks(GameTestHelper helper) {
		stun(helper, 4);
	}
}

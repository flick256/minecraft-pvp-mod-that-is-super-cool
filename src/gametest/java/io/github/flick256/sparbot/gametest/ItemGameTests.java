package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.BotTestAccess;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Layout;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.kit.KitCapture;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Milestone 2: items, eating, kit capture, layouts and inventory input. */
public class ItemGameTests {
	private static Kit.Provenance original() {
		return new Kit.Provenance("sparbot-original", null, "test", "26.2", "high", true, null, null, List.of());
	}

	private static Kit kit(Map<String, Kit.KitItem> slots, Kit.KitItem offhand) {
		return new Kit("test_kit", "Test", "test", original(), new Kit.Armor(null, new Kit.KitItem("minecraft:diamond_chestplate", null, null, null), null, null),
			offhand, slots, List.of());
	}

	@GameTest(maxTicks = 200)
	public void lowHealthBotEatsAGoldenApple(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Eater", 3.5, 1, 3.5, 0, "pro", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(4, new ItemStack(Items.GOLDEN_APPLE, 3));
		body.setHealth(6);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(body.getInventory().getItem(4).getCount(), 2, "golden apples left");
			helper.assertTrue(body.hasEffect(MobEffects.REGENERATION) && body.hasEffect(MobEffects.ABSORPTION), "gapple effects applied");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 200)
	public void lowHealthBotEatsAGoldenHeadFromTheUhcKit(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "HeadEater", 3.5, 1, 3.5, 0, "pro", "pvphq_uhc");
		BotPlayer body = TestSupport.body(bot);
		ItemStack head = body.getInventory().getItem(3);
		helper.assertTrue(io.github.flick256.sparbot.bot.ItemClassifier.isGoldenHead(head), "hotbar slot 4 holds golden heads, was " + head);
		helper.assertValueEqual(head.getHoverName().getString(), "Golden Head", "its name");
		helper.assertValueEqual(head.get(net.minecraft.core.component.DataComponents.CONSUMABLE).consumeSeconds(), 0.8F, "eaten in 0.8 s");
		for (int slot = 0; slot < 36; slot++) {
			if (slot != 3 && body.getInventory().getItem(slot).is(Items.GOLDEN_APPLE)) {
				body.getInventory().setItem(slot, ItemStack.EMPTY); // only the heads to eat
			}
		}
		body.setHealth(4);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(body.getInventory().getItem(3).getCount(), 1, "golden heads left");
			helper.assertTrue(body.hasEffect(MobEffects.REGENERATION) && body.getEffect(MobEffects.REGENERATION).getAmplifier() == 1,
				"Regeneration II applied");
			helper.assertTrue(body.getEffect(MobEffects.REGENERATION).getDuration() > 100, "for 10 s (a golden apple's lasts 5)");
			helper.assertTrue(body.hasEffect(MobEffects.ABSORPTION), "and Absorption");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 200)
	public void hungryBotEatsFood(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Hungry", 3.5, 1, 3.5, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		body.getInventory().setItem(6, new ItemStack(Items.COOKED_BEEF, 5));
		body.getFoodData().setFoodLevel(6);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(body.getInventory().getItem(6).getCount(), 4, "steak left");
			helper.assertTrue(body.getFoodData().getFoodLevel() > 6, "food bar refilled");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 40)
	public void capturedKitReproducesEveryStackExactly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 2, 1, 2);
		try {
			ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
			sword.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
			sword.setDamageValue(321);
			sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
			human.getInventory().setItem(0, sword);
			ItemStack pot = new ItemStack(Items.SPLASH_POTION, 1);
			pot.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HEALING));
			human.getInventory().setItem(1, pot);
			ItemStack arrows = new ItemStack(Items.TIPPED_ARROW, 12);
			arrows.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
			human.getInventory().setItem(17, arrows);
			ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 32);
			rockets.set(DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(3, List.of()));
			human.getInventory().setItem(35, rockets);
			human.getInventory().setItem(8, new ItemStack(Items.GOLDEN_APPLE, 64));
			human.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
			boots.setDamageValue(50);
			human.setItemSlot(EquipmentSlot.FEET, boots);

			Kit captured = KitCapture.captureKit(human, "captured_test", "test");
			helper.assertTrue(captured.provenance().verified() && "user-supplied".equals(captured.provenance().source()), "captured kits are first-hand");
			helper.assertTrue(KitApplier.resolveErrors(captured, level.registryAccess()).isEmpty(),
				"captured kit resolves: " + KitApplier.resolveErrors(captured, level.registryAccess()));

			Bot bot = TestSupport.spawnBot(helper, "Copy", 5, 1, 5, 0, "intermediate", "basic_sword");
			BotPlayer body = TestSupport.body(bot);
			KitApplier.apply(body, captured);
			for (int i = 0; i < 36; i++) {
				ItemStack expected = human.getInventory().getItem(i);
				ItemStack actual = body.getInventory().getItem(i);
				helper.assertTrue(ItemStack.matches(expected, actual), "slot " + i + ": expected " + expected + " " + expected.getComponentsPatch()
					+ " but was " + actual + " " + actual.getComponentsPatch());
			}
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.HEAD}) {
				helper.assertTrue(ItemStack.matches(human.getItemBySlot(slot), body.getItemBySlot(slot)), slot.getName() + " matches");
			}
			TestSupport.remove(bot);
		} finally {
			TestSupport.removeRealPlayer(human);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void layoutRearrangesTheBotsKit(GameTestHelper helper) {
		Kit kit = kit(Map.of(
			"0", new Kit.KitItem("minecraft:diamond_sword", null, null, null),
			"1", new Kit.KitItem("minecraft:golden_apple", 6, null, null),
			"9", new Kit.KitItem("minecraft:totem_of_undying", null, null, null)), new Kit.KitItem("minecraft:shield", null, null, null));
		Layout layout = new Layout("mine", "test_kit", Map.of("2", "minecraft:diamond_sword", "8", "minecraft:golden_apple"), "minecraft:totem_of_undying");
		Bot bot = TestSupport.spawnBot(helper, "Layout", 4, 1, 4, 0, "intermediate", "basic_sword");
		bot.setKit(kit);
		bot.setLayout(layout);
		BotPlayer body = TestSupport.body(bot);
		KitApplier.apply(body, bot.effectiveKit());
		helper.assertTrue(body.getInventory().getItem(2).is(Items.DIAMOND_SWORD), "sword moved to slot 2");
		helper.assertTrue(body.getInventory().getItem(8).is(Items.GOLDEN_APPLE) && body.getInventory().getItem(8).getCount() == 6, "gapples in slot 8");
		helper.assertTrue(body.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "totem in offhand");
		helper.assertTrue(body.getInventory().getItem(9).is(Items.SHIELD), "shield took the totem's old slot");
		TestSupport.remove(bot);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void inventoryClickAndSwapKeyMoveItemsLikeAClient(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Mover", 4, 1, 4, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		BotTestAccess.pauseBrain(bot, true);
		body.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));
		body.getInventory().setItem(3, new ItemStack(Items.SHIELD));
		// Hover slot 20 in the open inventory and press the offhand key (button 40).
		BotTestAccess.press(bot, Inputs.inInventory(new InventoryClick(20, 40)));
		helper.assertTrue(body.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "totem moved to offhand");
		helper.assertTrue(body.getInventory().getItem(20).isEmpty(), "slot 20 emptied");
		// Close the inventory, select slot 3 and press F: shield and totem trade hands.
		BotTestAccess.press(bot, Inputs.IDLE.withHotbarSlot(3));
		BotTestAccess.press(bot, Inputs.IDLE.withSwapOffhand(true));
		helper.assertTrue(body.getOffhandItem().is(Items.SHIELD), "F put the shield in the offhand");
		helper.assertTrue(body.getInventory().getItem(3).is(Items.TOTEM_OF_UNDYING), "and the totem in hand");
		TestSupport.remove(bot);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void clicksAreIgnoredWhileTheShieldIsUp(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot blocker = TestSupport.spawnBot(helper, "Blocker", 2.5, 1, 3.5, -90, "intermediate", "basic_sword");
		Bot dummy = TestSupport.spawnBot(helper, "Dummy", 4.5, 1, 3.5, 90, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(blocker);
		BotTestAccess.pauseBrain(blocker, true);
		BotTestAccess.pauseBrain(dummy, true);
		body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		helper.startSequence()
			.thenExecuteFor(25, () -> BotTestAccess.press(blocker, Inputs.IDLE.withUse(true)))
			.thenExecute(() -> {
				helper.assertTrue(body.isBlocking(), "shield raised (sword in main hand cannot be used, so the offhand shield is)");
				int swingsBefore = blocker.stats().swings();
				BotTestAccess.press(blocker, Inputs.IDLE.withUse(true).withAttack(true));
				helper.assertValueEqual(blocker.stats().swings(), swingsBefore, "a click while using an item is swallowed");
				helper.assertTrue(TestSupport.body(dummy).getHealth() == TestSupport.body(dummy).getMaxHealth(), "no damage dealt");
				BotTestAccess.press(blocker, Inputs.IDLE);
				helper.assertFalse(body.isUsingItem(), "releasing right click lowers the shield");
				TestSupport.remove(blocker, dummy);
			})
			.thenSucceed();
	}
}

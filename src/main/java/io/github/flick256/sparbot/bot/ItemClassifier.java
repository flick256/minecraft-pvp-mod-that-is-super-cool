package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/** Maps real 26.2 item stacks to the brain's {@link ItemKind}s and {@link ItemInfo}s. */
public final class ItemClassifier {
	/** A bare hand deals 1 damage (Player base attack damage attribute). */
	private static final double HAND_DAMAGE = 1.0;

	private ItemClassifier() {
	}

	public static ItemKind classify(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemKind.EMPTY;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return ItemKind.SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return ItemKind.AXE;
		}
		if (stack.is(ItemTags.SPEARS) || stack.has(DataComponents.PIERCING_WEAPON)) {
			return ItemKind.SPEAR;
		}
		if (stack.is(Items.MACE)) {
			return ItemKind.MACE;
		}
		if (stack.is(Items.TRIDENT)) {
			return ItemKind.TRIDENT;
		}
		if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
			return ItemKind.SHIELD;
		}
		if (stack.is(Items.TNT_MINECART)) {
			return ItemKind.TNT_MINECART;
		}
		if (stack.is(Items.BOW)) {
			return ItemKind.BOW;
		}
		if (stack.is(Items.CROSSBOW)) {
			return ItemKind.CROSSBOW;
		}
		if (stack.is(ItemTags.ARROWS)) {
			return ItemKind.ARROW;
		}
		if (stack.is(Items.FISHING_ROD)) {
			return ItemKind.FISHING_ROD;
		}
		if (stack.is(Items.ENDER_PEARL)) {
			return ItemKind.ENDER_PEARL;
		}
		if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return ItemKind.ENCHANTED_GOLDEN_APPLE;
		}
		if (stack.is(Items.GOLDEN_APPLE)) {
			return ItemKind.GOLDEN_APPLE;
		}
		if (stack.has(DataComponents.DEATH_PROTECTION)) {
			return ItemKind.TOTEM;
		}
		if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
			return ItemKind.SPLASH_POTION;
		}
		if (stack.is(Items.POTION)) {
			return ItemKind.DRINK_POTION;
		}
		if (stack.is(Items.WIND_CHARGE)) {
			return ItemKind.WIND_CHARGE;
		}
		if (stack.is(Items.COBWEB)) {
			return ItemKind.COBWEB;
		}
		if (stack.is(Items.BUCKET)) {
			return ItemKind.BUCKET;
		}
		if (stack.is(Items.WATER_BUCKET)) {
			return ItemKind.WATER_BUCKET;
		}
		if (stack.is(Items.LAVA_BUCKET)) {
			return ItemKind.LAVA_BUCKET;
		}
		if (stack.is(Items.END_CRYSTAL)) {
			return ItemKind.END_CRYSTAL;
		}
		if (stack.is(Items.FIREWORK_ROCKET)) {
			return ItemKind.FIREWORK;
		}
		if (stack.has(DataComponents.FOOD)) {
			return ItemKind.FOOD;
		}
		if (stack.has(DataComponents.EQUIPPABLE) && stack.get(DataComponents.EQUIPPABLE).slot().isArmor()) {
			return ItemKind.ARMOR;
		}
		if (stack.getItem() instanceof BlockItem) {
			return ItemKind.BLOCK;
		}
		return ItemKind.OTHER;
	}

	public static ItemInfo info(Player owner, ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemInfo.EMPTY;
		}
		double damage = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, HAND_DAMAGE, EquipmentSlot.MAINHAND);
		double durability = stack.isDamageableItem() ? 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage() : 1.0;
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		String potionId = potion != null && potion.potion().isPresent() ? potion.potion().get().getRegisteredName() : null;
		return new ItemInfo(classify(stack), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), damage, durability,
			owner.getCooldowns().isOnCooldown(stack), stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack), potionId, enchantments(stack));
	}

	private static Set<String> enchantments(ItemStack stack) {
		ItemEnchantments enchantments = stack.getEnchantments();
		if (enchantments.isEmpty()) {
			return Set.of();
		}
		Set<String> ids = new HashSet<>();
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			ids.add(enchantment.getRegisteredName());
		}
		return ids;
	}

	public static InventoryState inventory(ServerPlayer player, @Nullable Entity target) {
		Inventory inventory = player.getInventory();
		ItemInfo[] slots = new ItemInfo[InventoryState.SIZE];
		for (int i = 0; i < InventoryState.SIZE; i++) {
			slots[i] = info(player, inventory.getItem(i));
		}
		ItemInfo[] armor = {
			info(player, player.getItemBySlot(EquipmentSlot.HEAD)),
			info(player, player.getItemBySlot(EquipmentSlot.CHEST)),
			info(player, player.getItemBySlot(EquipmentSlot.LEGS)),
			info(player, player.getItemBySlot(EquipmentSlot.FEET))
		};
		boolean using = player.isUsingItem();
		FishingHook hook = player.fishing;
		return new InventoryState(
			slots,
			info(player, player.getOffhandItem()),
			armor,
			inventory.getSelectedSlot(),
			using,
			using && player.getUsedItemHand() == InteractionHand.OFF_HAND,
			using ? player.getTicksUsingItem() : 0,
			using ? classify(player.getUseItem()) : ItemKind.EMPTY,
			player.isBlocking(),
			hook != null && !hook.isRemoved(),
			hook != null && target != null && hook.getHookedIn() == target);
	}
}

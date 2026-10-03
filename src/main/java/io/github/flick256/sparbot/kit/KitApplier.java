package io.github.flick256.sparbot.kit;

import io.github.flick256.sparbot.core.kit.Kit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Turns a core {@link Kit} into real 26.2 item stacks. Applying a kit is a full reset (inventory,
 * effects, health, hunger) and is only done when a bot spawns, respawns or starts a round, never
 * mid-fight, so consumables stay finite.
 */
public final class KitApplier {
	private KitApplier() {
	}

	/** Problems resolving the kit against the registries (unknown ids, armor in the wrong slot). */
	public static List<String> resolveErrors(Kit kit, HolderLookup.Provider registries) {
		List<String> errors = new ArrayList<>();
		if (kit.armor() != null) {
			checkArmor("armor.head", kit.armor().head(), EquipmentSlot.HEAD, registries, errors);
			checkArmor("armor.chest", kit.armor().chest(), EquipmentSlot.CHEST, registries, errors);
			checkArmor("armor.legs", kit.armor().legs(), EquipmentSlot.LEGS, registries, errors);
			checkArmor("armor.feet", kit.armor().feet(), EquipmentSlot.FEET, registries, errors);
		}
		if (kit.offhand() != null) {
			resolve("offhand", kit.offhand(), registries, errors);
		}
		if (kit.slots() != null) {
			kit.slots().forEach((slot, item) -> resolve("slots." + slot, item, registries, errors));
		}
		if (kit.effects() != null) {
			for (Kit.KitEffect effect : kit.effects()) {
				if (BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.id())).isEmpty()) {
					errors.add("unknown effect " + effect.id());
				}
			}
		}
		return errors;
	}

	/** Clears the player and equips the kit. The kit must already have passed {@link #resolveErrors}. */
	public static void apply(ServerPlayer player, Kit kit) {
		HolderLookup.Provider registries = player.level().registryAccess();
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		player.removeAllEffects();

		if (kit.armor() != null) {
			player.setItemSlot(EquipmentSlot.HEAD, build(kit.armor().head(), registries));
			player.setItemSlot(EquipmentSlot.CHEST, build(kit.armor().chest(), registries));
			player.setItemSlot(EquipmentSlot.LEGS, build(kit.armor().legs(), registries));
			player.setItemSlot(EquipmentSlot.FEET, build(kit.armor().feet(), registries));
		}
		player.setItemSlot(EquipmentSlot.OFFHAND, build(kit.offhand(), registries));
		if (kit.slots() != null) {
			for (Map.Entry<String, Kit.KitItem> entry : kit.slots().entrySet()) {
				inventory.setItem(Integer.parseInt(entry.getKey()), build(entry.getValue(), registries));
			}
		}
		inventory.setSelectedSlot(0);
		if (kit.effects() != null) {
			for (Kit.KitEffect effect : kit.effects()) {
				Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.id())).orElseThrow();
				int duration = effect.durationTicks() == -1 ? MobEffectInstance.INFINITE_DURATION : effect.durationTicks();
				player.addEffect(new MobEffectInstance(holder, duration, effect.amplifier()));
			}
		}

		// Start of a round: full health and hunger, nothing left over from the last life.
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.setAbsorptionAmount(0);
		player.clearFire();
		player.resetFallDistance();
		player.inventoryMenu.broadcastChanges();
	}

	private static ItemStack build(Kit.KitItem spec, HolderLookup.Provider registries) {
		if (spec == null) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.get(Identifier.parse(spec.id())).orElseThrow().value();
		ItemStack stack = new ItemStack(item, spec.countOrDefault());
		if (spec.enchantments() != null) {
			HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
			spec.enchantments().forEach((id, level) ->
				stack.enchant(enchantments.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id))), level));
		}
		if (spec.potion() != null) {
			Holder<Potion> potion = BuiltInRegistries.POTION.get(Identifier.parse(spec.potion())).orElseThrow();
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
		}
		return stack;
	}

	private static void resolve(String where, Kit.KitItem spec, HolderLookup.Provider registries, List<String> errors) {
		Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(Identifier.parse(spec.id()));
		if (item.isEmpty()) {
			errors.add(where + ": unknown item " + spec.id());
			return;
		}
		int maxStack = item.get().value().getDefaultMaxStackSize();
		if (spec.countOrDefault() > maxStack) {
			errors.add(where + ": " + spec.id() + " stacks to " + maxStack + ", kit asks for " + spec.countOrDefault());
		}
		if (spec.enchantments() != null) {
			HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
			for (String id : spec.enchantments().keySet()) {
				if (enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id))).isEmpty()) {
					errors.add(where + ": unknown enchantment " + id);
				}
			}
		}
		if (spec.potion() != null && BuiltInRegistries.POTION.get(Identifier.parse(spec.potion())).isEmpty()) {
			errors.add(where + ": unknown potion " + spec.potion());
		}
	}

	private static void checkArmor(String where, Kit.KitItem spec, EquipmentSlot slot, HolderLookup.Provider registries, List<String> errors) {
		if (spec == null) {
			return;
		}
		int before = errors.size();
		resolve(where, spec, registries, errors);
		if (errors.size() != before) {
			return;
		}
		Item item = BuiltInRegistries.ITEM.get(Identifier.parse(spec.id())).orElseThrow().value();
		Equippable equippable = new ItemStack(item).get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != slot) {
			errors.add(where + ": " + spec.id() + " cannot be worn in the " + slot.getName() + " slot");
		}
	}
}

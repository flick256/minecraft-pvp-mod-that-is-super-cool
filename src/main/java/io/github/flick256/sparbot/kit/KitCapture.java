package io.github.flick256.sparbot.kit;

import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Layout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Turns a real player's inventory into a kit or a layout, slot for slot. Captured kits are
 * first-hand evidence, so they are marked "user-supplied", verified, high confidence.
 */
public final class KitCapture {
	private KitCapture() {
	}

	public static Kit captureKit(ServerPlayer player, String id, String mode) {
		Map<String, Kit.KitItem> slots = new LinkedHashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				slots.put(Integer.toString(i), describe(player, stack));
			}
		}
		Kit.Armor armor = new Kit.Armor(describe(player, player.getItemBySlot(EquipmentSlot.HEAD)), describe(player, player.getItemBySlot(EquipmentSlot.CHEST)),
			describe(player, player.getItemBySlot(EquipmentSlot.LEGS)), describe(player, player.getItemBySlot(EquipmentSlot.FEET)));
		List<Kit.KitEffect> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add(new Kit.KitEffect(effect.getEffect().getRegisteredName(), effect.getAmplifier(),
				effect.isInfiniteDuration() ? -1 : effect.getDuration()));
		}
		Kit.Provenance provenance = new Kit.Provenance("user-supplied", null, mode, SharedConstants.getCurrentVersion().name(), "high", true,
			"captured in game from " + player.getPlainTextName() + "'s inventory on " + LocalDate.now(), null, List.of());
		return new Kit(id, id, mode, provenance, armor, describe(player, player.getOffhandItem()), slots, effects);
	}

	/** Records where the player keeps each of the kit's item types. */
	public static Layout captureLayout(ServerPlayer player, String id, Kit kit) {
		Set<String> kitItems = new java.util.HashSet<>();
		if (kit.slots() != null) {
			kit.slots().values().forEach(item -> kitItems.add(Layout.key(item)));
		}
		if (kit.offhand() != null) {
			kitItems.add(Layout.key(kit.offhand()));
		}
		Map<String, String> slots = new LinkedHashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			String key = layoutKey(stack);
			if (!stack.isEmpty() && kitItems.contains(key)) {
				slots.put(Integer.toString(i), key);
			}
		}
		ItemStack offhand = player.getOffhandItem();
		String offhandKey = offhand.isEmpty() ? null : layoutKey(offhand);
		return new Layout(id, kit.id(), slots, offhandKey != null && kitItems.contains(offhandKey) ? offhandKey : null);
	}

	/** A stack as a layout names it ({@link Layout#key}): golden heads and potions told apart from their look-alikes. */
	static String layoutKey(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		String potion = contents != null && contents.potion().isPresent() ? contents.potion().get().getRegisteredName() : null;
		return Layout.key(id, io.github.flick256.sparbot.bot.ItemClassifier.isGoldenHead(stack), potion);
	}

	/** One stack as a kit item: id, count, enchantments, base potion, and every other component. */
	static Kit.KitItem describe(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Map<String, Integer> enchantments = null;
		ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
		if (enchants != null && !enchants.isEmpty()) {
			enchantments = new LinkedHashMap<>();
			for (Holder<Enchantment> enchantment : enchants.keySet()) {
				enchantments.put(enchantment.getRegisteredName(), enchants.getLevel(enchantment));
			}
		}
		String potion = null;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		boolean simplePotion = contents != null && contents.potion().isPresent() && contents.customEffects().isEmpty()
			&& contents.customColor().isEmpty() && contents.customName().isEmpty();
		if (simplePotion) {
			potion = contents.potion().get().getRegisteredName();
		}
		Integer count = stack.getCount() == 1 ? null : stack.getCount();
		return new Kit.KitItem(id, count, enchantments, potion, otherComponents(player, stack, simplePotion));
	}

	/** Serializes every changed component except those already captured as fields, in /give syntax. */
	private static String otherComponents(ServerPlayer player, ItemStack stack, boolean potionAsField) {
		RegistryOps<Tag> ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		DataComponentPatch patch = stack.getComponentsPatch();
		List<String> parts = new ArrayList<>();
		for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
			DataComponentType<?> type = entry.getKey();
			if (type == DataComponents.ENCHANTMENTS || type.isTransient() || potionAsField && type == DataComponents.POTION_CONTENTS) {
				continue;
			}
			String key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString();
			if (entry.getValue().isEmpty()) {
				parts.add("!" + key);
			} else {
				encode(type, entry.getValue().get(), ops).ifPresent(tag -> parts.add(key + "=" + tag));
			}
		}
		return parts.isEmpty() ? null : parts.stream().collect(Collectors.joining(","));
	}

	@SuppressWarnings("unchecked")
	private static <T> Optional<String> encode(DataComponentType<T> type, Object value, RegistryOps<Tag> ops) {
		if (type.codec() == null) {
			return Optional.empty();
		}
		return type.codec().encodeStart(ops, (T) value).result().map(Tag::toString);
	}
}

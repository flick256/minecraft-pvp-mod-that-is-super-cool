package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.kit.Kit;
import java.util.Map;

/**
 * One inventory stack in the simulator, with the numbers vanilla's combat code reads from it.
 *
 * @param attackDamage the attack damage attribute it gives in the main hand (1 for the hand)
 * @param attackSpeed the attack speed attribute it gives (4 for the hand)
 * @param magic extra melee damage from Sharpness (1 + 0.5 per level above the first), scaled by charge
 * @param power extra arrow damage from Power (1 + 0.5 per level above the first)
 */
record SimStack(ItemKind kind, String id, int count, double attackDamage, double attackSpeed, double magic, double power) {
	static final double HAND_DAMAGE = 1.0;
	static final double HAND_SPEED = 4.0;

	SimStack withCount(int newCount) {
		return newCount <= 0 ? null : new SimStack(kind, id, newCount, attackDamage, attackSpeed, magic, power);
	}

	/** The same slot with a different item in it (a bucket filled or emptied). */
	static SimStack of(ItemKind kind, String id) {
		return new SimStack(kind, id, 1, HAND_DAMAGE, HAND_SPEED, 0, 0);
	}

	ItemInfo info() {
		return new ItemInfo(kind, id, count, attackDamage, 1.0, false, false, null);
	}

	/** A kit item as the simulator knows it: the item kinds UHC kits use (others become {@link ItemKind#OTHER}). */
	static SimStack fromKit(Kit.KitItem item) {
		String id = item.id().startsWith("minecraft:") ? item.id() : "minecraft:" + item.id();
		String name = id.substring("minecraft:".length());
		Map<String, Integer> ench = item.enchantments() == null ? Map.of() : item.enchantments();
		int count = item.countOrDefault();
		double magic = levelBonus(ench.getOrDefault("minecraft:sharpness", 0));
		double power = levelBonus(ench.getOrDefault("minecraft:power", 0));
		if (name.endsWith("_sword")) {
			return new SimStack(ItemKind.SWORD, id, count, swordDamage(name), 1.6, magic, 0);
		}
		if (name.endsWith("_axe")) {
			return new SimStack(ItemKind.AXE, id, count, axeDamage(name), axeSpeed(name), magic, 0);
		}
		ItemKind kind = switch (name) {
			case "bow" -> ItemKind.BOW;
			case "crossbow" -> ItemKind.CROSSBOW;
			case "arrow" -> ItemKind.ARROW;
			case "water_bucket" -> ItemKind.WATER_BUCKET;
			case "lava_bucket" -> ItemKind.LAVA_BUCKET;
			case "bucket" -> ItemKind.BUCKET;
			case "cobweb" -> ItemKind.COBWEB;
			case "golden_apple" -> item.components() != null && item.components().contains("golden_head") ? ItemKind.GOLDEN_HEAD : ItemKind.GOLDEN_APPLE;
			case "enchanted_golden_apple" -> ItemKind.ENCHANTED_GOLDEN_APPLE;
			case "shield" -> ItemKind.SHIELD;
			case "ender_pearl" -> ItemKind.ENDER_PEARL;
			case "cooked_beef", "cooked_porkchop", "bread", "steak", "golden_carrot", "baked_potato", "cooked_chicken" -> ItemKind.FOOD;
			case "cobblestone", "stone", "dirt", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
				"cobbled_deepslate", "sandstone", "end_stone", "netherrack", "andesite", "diorite", "granite" -> ItemKind.BLOCK;
			default -> ItemKind.OTHER;
		};
		return new SimStack(kind, id, count, HAND_DAMAGE, HAND_SPEED, 0, power);
	}

	/** Sharpness and Power in 26.2: 1.0 at level I, +0.5 a level after that. */
	private static double levelBonus(int level) {
		return level <= 0 ? 0 : 1.0 + 0.5 * (level - 1);
	}

	private static double swordDamage(String name) {
		return switch (name) {
			case "netherite_sword" -> 8;
			case "diamond_sword" -> 7;
			case "iron_sword" -> 6;
			case "stone_sword", "copper_sword" -> 5;
			default -> 4;
		};
	}

	private static double axeDamage(String name) {
		return switch (name) {
			case "netherite_axe" -> 10;
			case "wooden_axe", "golden_axe" -> 7;
			default -> 9;
		};
	}

	private static double axeSpeed(String name) {
		return switch (name) {
			case "netherite_axe", "diamond_axe", "golden_axe" -> 1.0;
			case "iron_axe" -> 0.9;
			default -> 0.8;
		};
	}
}

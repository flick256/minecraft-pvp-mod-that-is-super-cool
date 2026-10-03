package io.github.flick256.sparbot.core.item;

/** What an item is good for in a fight. The mod classifies real item stacks into these. */
public enum ItemKind {
	EMPTY,
	SWORD,
	AXE,
	MACE,
	SPEAR,
	TRIDENT,
	SHIELD,
	BOW,
	CROSSBOW,
	ARROW,
	FISHING_ROD,
	ENDER_PEARL,
	GOLDEN_APPLE,
	ENCHANTED_GOLDEN_APPLE,
	FOOD,
	TOTEM,
	SPLASH_POTION,
	DRINK_POTION,
	WIND_CHARGE,
	COBWEB,
	WATER_BUCKET,
	LAVA_BUCKET,
	BLOCK,
	END_CRYSTAL,
	FIREWORK,
	ARMOR,
	OTHER;

	public boolean isMeleeWeapon() {
		return this == SWORD || this == AXE || this == MACE || this == TRIDENT;
	}

	public boolean isFood() {
		return this == GOLDEN_APPLE || this == ENCHANTED_GOLDEN_APPLE || this == FOOD;
	}
}

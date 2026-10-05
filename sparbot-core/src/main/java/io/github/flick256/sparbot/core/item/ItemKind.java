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
	TNT_MINECART,
	CROSSBOW,
	ARROW,
	FISHING_ROD,
	ENDER_PEARL,
	GOLDEN_APPLE,
	ENCHANTED_GOLDEN_APPLE,
	/** UHC's golden head (a SparBot kit item: a golden apple that looks like a head, eaten in 0.8 s, Regeneration II for 10 s). */
	GOLDEN_HEAD,
	FOOD,
	TOTEM,
	SPLASH_POTION,
	DRINK_POTION,
	WIND_CHARGE,
	COBWEB,
	WATER_BUCKET,
	BUCKET,
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
		return this == GOLDEN_APPLE || this == ENCHANTED_GOLDEN_APPLE || this == GOLDEN_HEAD || this == FOOD;
	}
}

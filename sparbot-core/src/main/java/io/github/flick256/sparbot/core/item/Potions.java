package io.github.flick256.sparbot.core.item;

import java.util.Map;

/** What vanilla potions do, by potion id (e.g. minecraft:strong_healing), as far as a fighter cares. */
public final class Potions {
	/** Potion name fragment to the effect it gives. Strong / long variants share the effect. */
	private static final Map<String, String> EFFECTS = Map.of(
		"healing", "minecraft:instant_health",
		"swiftness", "minecraft:speed",
		"fire_resistance", "minecraft:fire_resistance",
		"strength", "minecraft:strength",
		"regeneration", "minecraft:regeneration",
		"turtle_master", "minecraft:resistance");
	/** Buffs a duelist keeps up. */
	private static final java.util.Set<String> BUFFS = java.util.Set.of("minecraft:speed", "minecraft:fire_resistance", "minecraft:strength");

	private Potions() {
	}

	/** The effect a potion gives, or null for potions a fighter does not use (harming, poison...). */
	public static String effectOf(String potionId) {
		if (potionId == null) {
			return null;
		}
		String name = potionId.substring(potionId.indexOf(':') + 1).replace("strong_", "").replace("long_", "");
		return EFFECTS.get(name);
	}

	public static boolean isHealingSplash(ItemInfo item) {
		return item.is(ItemKind.SPLASH_POTION) && "minecraft:instant_health".equals(effectOf(item.potion()));
	}

	/** A speed / fire resistance / strength potion (drink or splash). */
	public static boolean isBuff(ItemInfo item) {
		return (item.is(ItemKind.DRINK_POTION) || item.is(ItemKind.SPLASH_POTION)) && BUFFS.contains(effectOf(item.potion()));
	}
}

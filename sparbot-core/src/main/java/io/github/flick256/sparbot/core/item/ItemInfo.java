package io.github.flick256.sparbot.core.item;

/**
 * One inventory stack as the bot knows it (a player can see all of this by looking at their own
 * inventory and hotbar).
 *
 * @param attackDamage main-hand attack damage the item gives (base attribute value included)
 * @param durability remaining durability in [0, 1]; 1 for items without durability
 * @param onCooldown the item is on a use cooldown (e.g. ender pearl, disabled shield)
 * @param charged a loaded crossbow
 * @param potion potion id for potions, else null
 */
public record ItemInfo(ItemKind kind, String id, int count, double attackDamage, double durability, boolean onCooldown, boolean charged, String potion) {
	public static final ItemInfo EMPTY = new ItemInfo(ItemKind.EMPTY, "minecraft:air", 0, 1.0, 1.0, false, false, null);

	public boolean isEmpty() {
		return kind == ItemKind.EMPTY || count <= 0;
	}

	public boolean is(ItemKind k) {
		return !isEmpty() && kind == k;
	}
}

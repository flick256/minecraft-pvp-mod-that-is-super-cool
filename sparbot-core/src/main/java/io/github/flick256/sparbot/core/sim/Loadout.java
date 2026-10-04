package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import java.util.Map;

/**
 * What a simulated fighter carries. Either the melee numbers alone (the hotbar is sword in slot 0, then
 * the axe and golden apples if carried, a shield in the offhand), or a whole kit from the mod's kit files,
 * slot for slot, buckets, blocks, cobwebs, bow and arrows included.
 *
 * @param attackDamage the sword's attack damage attribute (1 for the hand plus the weapon's bonus)
 * @param enchantBonus extra sword damage from enchantments (Sharpness), scaled by charge
 * @param attackSpeed the sword's attack speed attribute: a full charge takes 20 / speed ticks
 * @param armor total armor points
 * @param toughness total armor toughness
 * @param axeDamage the axe's attack damage, 0 for no axe
 * @param gapples golden apples carried
 * @param shield a shield in the offhand
 * @param protection total Protection levels on the armor (each level blocks 4% of what armor lets through)
 * @param kit the whole kit, or null for the melee numbers alone
 */
public record Loadout(double attackDamage, double enchantBonus, double attackSpeed, double armor, double toughness, double axeDamage, int gapples,
	boolean shield, int protection, Kit kit) {
	/** Diamond sword and full diamond armor: SparBot's basic_sword kit. */
	public static final Loadout DIAMOND_SWORD = new Loadout(7.0, 0.0, 1.6, 20.0, 8.0);
	/**
	 * SparBot's sparbot_uhc kit as far as melee goes: diamond sword and axe, six golden apples, a shield
	 * and Protection II diamond armor (UHC: no natural regeneration, so golden apples are the only healing).
	 */
	public static final Loadout UHC = new Loadout(7.0, 0.0, 1.6, 20.0, 8.0, 9.0, 6, true, 8);
	/** Diamond axe: attack speed 1.0. */
	static final double AXE_SPEED = 1.0;

	/** A sword-only loadout. */
	public Loadout(double attackDamage, double enchantBonus, double attackSpeed, double armor, double toughness) {
		this(attackDamage, enchantBonus, attackSpeed, armor, toughness, 0, 0, false, 0, null);
	}

	/** Melee numbers without a kit. */
	public Loadout(double attackDamage, double enchantBonus, double attackSpeed, double armor, double toughness, double axeDamage, int gapples,
		boolean shield, int protection) {
		this(attackDamage, enchantBonus, attackSpeed, armor, toughness, axeDamage, gapples, shield, protection, null);
	}

	/** A bundled kit, everything in it (armor points and Protection read from its armor). */
	public static Loadout ofKit(String kitId) {
		Kit kit = Kits.loadBundled().get(kitId);
		if (kit == null) {
			throw new IllegalArgumentException("no bundled kit " + kitId);
		}
		double armor = 0;
		double toughness = 0;
		int protection = 0;
		Kit.Armor a = kit.armor();
		for (Kit.KitItem piece : a == null ? new Kit.KitItem[0] : new Kit.KitItem[] {a.head(), a.chest(), a.legs(), a.feet()}) {
			if (piece == null) {
				continue;
			}
			String name = piece.id().replace("minecraft:", "");
			armor += armorPoints(name);
			toughness += name.startsWith("diamond_") ? 2 : name.startsWith("netherite_") ? 3 : 0;
			Map<String, Integer> ench = piece.enchantments() == null ? Map.of() : piece.enchantments();
			protection += ench.getOrDefault("minecraft:protection", 0);
		}
		boolean shield = kit.offhand() != null && kit.offhand().id().endsWith("shield");
		return new Loadout(1, 0, 4, armor, toughness, 0, 0, shield, protection, kit);
	}

	private static double armorPoints(String name) {
		String material = name.substring(0, name.indexOf('_'));
		String piece = name.substring(name.indexOf('_') + 1);
		int[] points = switch (material) {
			case "diamond", "netherite" -> new int[] {3, 8, 6, 3};
			case "iron" -> new int[] {2, 6, 5, 2};
			case "chainmail" -> new int[] {2, 5, 4, 1};
			case "golden" -> new int[] {2, 5, 3, 1};
			default -> new int[] {1, 3, 2, 1};
		};
		return switch (piece) {
			case "helmet" -> points[0];
			case "chestplate" -> points[1];
			case "leggings" -> points[2];
			default -> points[3];
		};
	}

	/** Ticks for a full charge with the sword. */
	public double chargeTicks() {
		return 20.0 / attackSpeed;
	}

	public boolean hasAxe() {
		return axeDamage > 0;
	}

	public boolean hasKit() {
		return kit != null;
	}
}

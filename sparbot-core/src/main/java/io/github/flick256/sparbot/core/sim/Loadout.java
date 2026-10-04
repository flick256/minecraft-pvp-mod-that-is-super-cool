package io.github.flick256.sparbot.core.sim;

/**
 * What a simulated fighter carries, reduced to the numbers vanilla's combat code uses. The hotbar is
 * sword in slot 0, then the axe and golden apples if carried; a shield goes in the offhand.
 *
 * @param attackDamage the sword's attack damage attribute (1 for the hand plus the weapon's bonus)
 * @param enchantBonus extra sword damage from enchantments (Sharpness: 0.5 x level + 0.5), scaled by charge
 * @param attackSpeed the sword's attack speed attribute: a full charge takes 20 / speed ticks
 * @param armor total armor points
 * @param toughness total armor toughness
 * @param axeDamage the axe's attack damage, 0 for no axe
 * @param gapples golden apples carried
 * @param shield a shield in the offhand
 * @param protection total Protection levels on the armor (each level blocks 4% of what armor lets through)
 */
public record Loadout(double attackDamage, double enchantBonus, double attackSpeed, double armor, double toughness, double axeDamage, int gapples,
	boolean shield, int protection) {
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
		this(attackDamage, enchantBonus, attackSpeed, armor, toughness, 0, 0, false, 0);
	}

	/** Ticks for a full charge with the sword. */
	public double chargeTicks() {
		return 20.0 / attackSpeed;
	}

	public boolean hasAxe() {
		return axeDamage > 0;
	}
}

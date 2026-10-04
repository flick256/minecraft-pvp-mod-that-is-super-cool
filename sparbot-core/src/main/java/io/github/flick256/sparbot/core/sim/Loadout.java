package io.github.flick256.sparbot.core.sim;

/**
 * What a simulated fighter carries, reduced to the numbers vanilla's combat code uses.
 *
 * @param attackDamage the weapon's attack damage attribute (1 for the hand plus the weapon's bonus)
 * @param enchantBonus extra damage from enchantments (Sharpness: 0.5 x level + 0.5), scaled by charge
 * @param attackSpeed the weapon's attack speed attribute: a full charge takes 20 / speed ticks
 * @param armor total armor points
 * @param toughness total armor toughness
 */
public record Loadout(double attackDamage, double enchantBonus, double attackSpeed, double armor, double toughness) {
	/** Diamond sword and full diamond armor: SparBot's basic_sword kit. */
	public static final Loadout DIAMOND_SWORD = new Loadout(7.0, 0.0, 1.6, 20.0, 8.0);

	/** Ticks for a full charge. */
	public double chargeTicks() {
		return 20.0 / attackSpeed;
	}
}

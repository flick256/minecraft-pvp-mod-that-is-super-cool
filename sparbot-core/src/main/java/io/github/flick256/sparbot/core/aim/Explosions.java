package io.github.flick256.sparbot.core.aim;

import io.github.flick256.sparbot.core.math.Vec3;

/**
 * Explosion damage as 26.2 computes it (ExplosionDamageCalculator#getEntityDamageAmount), before
 * armor, enchantments and difficulty: with {@code d} the distance from the centre to the entity's
 * feet divided by twice the power, and {@code e} the share of the entity the blast can see,
 * {@code p = (1 - d) * e} and damage is {@code (p * p + p) / 2 * 7 * 2 * power + 1}. Nothing beyond
 * twice the power is hurt.
 */
public final class Explosions {
	/** EndCrystal#hurtServer explodes with power 6. */
	public static final double CRYSTAL_POWER = 6.0;

	private Explosions() {
	}

	/**
	 * Damage before armor to an entity standing at {@code feet}, assuming the blast sees all of it
	 * (blocks in between only lower it, so this is what a careful player plans for).
	 */
	public static double rawDamage(Vec3 center, Vec3 feet, double power) {
		double doubleRadius = power * 2.0;
		double dist = feet.distanceTo(center) / doubleRadius;
		if (dist > 1.0) {
			return 0;
		}
		double p = 1.0 - dist;
		return (p * p + p) / 2.0 * 7.0 * doubleRadius + 1.0;
	}
}

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
	/** A charged respawn anchor used outside the Nether (RespawnAnchorBlock#explode), centred on the block. */
	public static final double ANCHOR_POWER = 5.0;

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

	/**
	 * What a blast of {@code raw} damage takes off a player wearing {@code armor} (head, chest, legs, feet),
	 * on Normal difficulty: armor points and toughness first (CombatRules#getDamageAfterAbsorb), then
	 * enchantment protection (Protection counted as level IV, 1 per level; Blast Protection IV, 2 per
	 * level; capped at 20, 4% each). The enchantment level isn't seen, only whether it is there.
	 */
	public static double afterArmor(double raw, io.github.flick256.sparbot.core.item.ItemInfo[] armor) {
		double points = 0;
		double toughness = 0;
		int protection = 0;
		for (int slot = 0; slot < armor.length && slot < 4; slot++) {
			io.github.flick256.sparbot.core.item.ItemInfo piece = armor[slot];
			if (piece == null || piece.id() == null) {
				continue;
			}
			String id = piece.id();
			int[] values = id.contains("netherite_") || id.contains("diamond_") ? new int[] {3, 8, 6, 3}
				: id.contains("iron_") ? new int[] {2, 6, 5, 2}
				: id.contains("chainmail_") ? new int[] {2, 5, 4, 1}
				: id.contains("golden_") ? new int[] {2, 5, 3, 1}
				: id.contains("leather_") ? new int[] {1, 3, 2, 1}
				: id.contains("turtle_") ? new int[] {2, 0, 0, 0} : new int[4];
			points += values[slot];
			toughness += id.contains("netherite_") ? 3 : id.contains("diamond_") ? 2 : 0;
			if (piece.enchanted("minecraft:blast_protection")) {
				protection += 8;
			} else if (piece.enchanted("minecraft:protection")) {
				protection += 4;
			}
		}
		double f = 2.0 + toughness / 4.0;
		double g = Math.max(points * 0.2, Math.min(points - raw / f, 20.0));
		double damage = raw * (1.0 - g / 25.0);
		return damage * (1.0 - Math.min(20, protection) / 25.0);
	}
}

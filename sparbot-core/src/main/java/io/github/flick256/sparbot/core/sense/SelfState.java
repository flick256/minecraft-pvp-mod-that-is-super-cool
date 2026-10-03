package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.math.Vec3;

/**
 * What the bot knows about its own body this tick, i.e. what a player sees on their own HUD and feels
 * through their client.
 *
 * @param attackStrength vanilla attack charge in [0, 1] (Player#getAttackStrengthScale(0.5))
 * @param attackReach the reach a vanilla client would use for the held item, in blocks
 * @param hurtTime vanilla hurt animation counter; jumps to its max the tick damage is taken
 * @param dropDepth for each of 8 compass directions (index = round(worldYaw / 45) mod 8, yaw 0 = south),
 *     how many blocks the ground drops one block away; {@link #VOID_DROP} means no ground found
 */
public record SelfState(
	Vec3 position,
	Vec3 eyePosition,
	Vec3 velocity,
	float yaw,
	float pitch,
	float health,
	float maxHealth,
	float absorption,
	int foodLevel,
	boolean onGround,
	boolean sprinting,
	boolean inWater,
	boolean horizontalCollision,
	double fallDistance,
	float attackStrength,
	double attackReach,
	int hurtTime,
	boolean holdingMeleeWeapon,
	int[] dropDepth
) {
	public static final int VOID_DROP = 64;
	public static final int DIRECTIONS = 8;

	public float healthFraction() {
		return maxHealth <= 0 ? 0 : (health + absorption) / maxHealth;
	}
}

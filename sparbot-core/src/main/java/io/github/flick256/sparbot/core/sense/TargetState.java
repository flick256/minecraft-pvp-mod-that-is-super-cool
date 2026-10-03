package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.math.Vec3;

/**
 * The opponent as the bot perceives it. When the target is not visible this holds the last seen
 * values (with {@code visible == false}); the bot never receives positions it could not see.
 *
 * @param halfWidth half of the target's hitbox width
 * @param height target hitbox height
 * @param ticksSinceSeen 0 when visible this tick
 */
public record TargetState(
	int entityId,
	String name,
	Vec3 position,
	Vec3 velocity,
	float yaw,
	float health,
	float maxHealth,
	boolean onGround,
	int hurtTime,
	boolean blocking,
	boolean visible,
	int ticksSinceSeen,
	double halfWidth,
	double height
) {
	/** Closest point of the target's hitbox to {@code from}. */
	public Vec3 closestHitboxPoint(Vec3 from) {
		double x = clamp(from.x(), position.x() - halfWidth, position.x() + halfWidth);
		double y = clamp(from.y(), position.y(), position.y() + height);
		double z = clamp(from.z(), position.z() - halfWidth, position.z() + halfWidth);
		return new Vec3(x, y, z);
	}

	/** Distance from {@code from} to the hitbox, the same metric vanilla uses for reach checks. */
	public double hitboxDistance(Vec3 from) {
		return from.distanceTo(closestHitboxPoint(from));
	}

	public Vec3 chest() {
		return position.add(new Vec3(0, height * 0.7, 0));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}

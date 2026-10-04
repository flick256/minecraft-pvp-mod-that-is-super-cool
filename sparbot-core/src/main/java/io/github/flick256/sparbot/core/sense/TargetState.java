package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;

/**
 * The opponent as the bot perceives it. When the target is not visible this holds the last seen
 * values (with {@code visible == false}); the bot never receives positions it could not see.
 *
 * @param halfWidth half of the target's hitbox width
 * @param height target hitbox height
 * @param ticksSinceSeen 0 when visible this tick
 * @param mainHand what the opponent visibly holds
 * @param offhand what the opponent visibly holds in the offhand
 * @param usingKind the item the opponent is visibly using (eating, drawing a bow, blocking), or EMPTY
 * @param armorPoints armor value of the opponent's visible armor
 * @param ticksSinceSwing ticks since the opponent's arm last swung (a click, hit or miss), {@link #NO_SWING} if unknown
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
	double height,
	ItemKind mainHand,
	ItemKind offhand,
	ItemKind usingKind,
	double armorPoints,
	boolean onFire,
	boolean inWeb,
	boolean inWater,
	int ticksSinceSwing
) {
	/** Unknown swing timing reads as long ago. */
	public static final int NO_SWING = 100;

	/** A target whose swing timing isn't known. */
	public TargetState(int entityId, String name, Vec3 position, Vec3 velocity, float yaw, float health, float maxHealth, boolean onGround,
		int hurtTime, boolean blocking, boolean visible, int ticksSinceSeen, double halfWidth, double height, ItemKind mainHand,
		ItemKind offhand, ItemKind usingKind, double armorPoints, boolean onFire, boolean inWeb, boolean inWater) {
		this(entityId, name, position, velocity, yaw, health, maxHealth, onGround, hurtTime, blocking, visible, ticksSinceSeen, halfWidth, height,
			mainHand, offhand, usingKind, armorPoints, onFire, inWeb, inWater, NO_SWING);
	}

	/** A target whose water state isn't known (not in water). */
	public TargetState(int entityId, String name, Vec3 position, Vec3 velocity, float yaw, float health, float maxHealth, boolean onGround,
		int hurtTime, boolean blocking, boolean visible, int ticksSinceSeen, double halfWidth, double height, ItemKind mainHand,
		ItemKind offhand, ItemKind usingKind, double armorPoints, boolean onFire, boolean inWeb) {
		this(entityId, name, position, velocity, yaw, health, maxHealth, onGround, hurtTime, blocking, visible, ticksSinceSeen, halfWidth, height,
			mainHand, offhand, usingKind, armorPoints, onFire, inWeb, false);
	}

	/** A target that isn't burning. */
	public TargetState(int entityId, String name, Vec3 position, Vec3 velocity, float yaw, float health, float maxHealth, boolean onGround,
		int hurtTime, boolean blocking, boolean visible, int ticksSinceSeen, double halfWidth, double height, ItemKind mainHand,
		ItemKind offhand, ItemKind usingKind, double armorPoints) {
		this(entityId, name, position, velocity, yaw, health, maxHealth, onGround, hurtTime, blocking, visible, ticksSinceSeen, halfWidth, height,
			mainHand, offhand, usingKind, armorPoints, false, false, false);
	}

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

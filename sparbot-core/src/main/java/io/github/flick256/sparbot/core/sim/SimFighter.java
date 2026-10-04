package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.List;

/**
 * One player in the duel simulator: vanilla 26.2 movement on flat ground and vanilla melee, re-done
 * without Minecraft so thousands of fights a second can run. The numbers and the order of operations
 * follow the decompiled game code they are named after:
 * <ul>
 *   <li>keys to movement: LocalPlayer#modifyInput (x 0.98, then stretched to the unit square)</li>
 *   <li>LivingEntity#travelInAir: accelerate by the movement speed (0.1, x 1.3 sprinting) on the
 *       ground or the flying speed (0.02, 0.026 sprinting) in the air, move, then gravity 0.08 and drag
 *       (0.98 vertical; 0.6 x 0.91 on the ground, 0.91 in the air)</li>
 *   <li>LivingEntity#jumpFromGround: 0.42 up, plus 0.2 along the facing when sprinting</li>
 *   <li>Player#attack: charge (20 / attack speed ticks), damage x (0.2 + 0.8 charge^2), crits x 1.5
 *       (charge above 0.9, falling, not on the ground, not sprinting), sprint knockback (charge above
 *       0.9 while sprinting: +0.5 along the attacker's facing, then the attacker slows to 0.6 and stops
 *       sprinting)</li>
 *   <li>LivingEntity#hurtServer: 20 ticks of invulnerability, of which the last 10 let a bigger hit
 *       through for the difference (without knockback), default knockback 0.4 away from the attacker</li>
 *   <li>LivingEntity#knockback: velocity / 2 minus the push; upwards min(0.4, vy / 2 + power) on the ground</li>
 *   <li>CombatRules#getDamageAfterAbsorb for armor</li>
 *   <li>Knockback reaches a player one tick later (the server sends it, the client applies it)</li>
 * </ul>
 */
public final class SimFighter {
	static final double EYE_HEIGHT = 1.62;
	static final double HALF_WIDTH = 0.3;
	static final double HEIGHT = 1.8;
	static final double REACH = 3.0;
	private static final double WALK_SPEED = 0.1;
	private static final double SPRINT_FACTOR = 1.3;
	private static final double AIR_SPEED = 0.02;
	private static final double AIR_SPEED_SPRINTING = 0.026;
	private static final double GRAVITY = 0.08;
	private static final double GROUND_FRICTION = 0.6 * 0.91;
	private static final double AIR_FRICTION = 0.91;
	private static final double VERTICAL_DRAG = 0.98;
	private static final double JUMP_POWER = 0.42;
	private static final double SPRINT_JUMP_BOOST = 0.2;
	private static final double DEFAULT_KNOCKBACK = 0.4;
	private static final double SPRINT_KNOCKBACK = 0.5;
	private static final int JUMP_DELAY = 10;
	static final float MAX_HEALTH = 20.0F;
	private static final ItemInfo SWORD = new ItemInfo(ItemKind.SWORD, "minecraft:diamond_sword", 1, 7.0, 1.0, false, false, null);
	private static final int[] FLAT = new int[SelfState.DIRECTIONS];

	final Loadout loadout;
	double x;
	double y;
	double z;
	double vx;
	double vy;
	double vz;
	float yaw;
	float pitch;
	boolean onGround = true;
	boolean sprinting;
	boolean horizontalCollision;
	double fallDistance;
	int attackTicker = 100;
	float health = MAX_HEALTH;
	int invulnerableTime;
	int hurtTime;
	float lastHurt;
	int noJumpDelay;
	/** Knockback the "client" receives at the start of its next tick. */
	Vec3 pendingMotion;
	/** Where it was at the end of the last tick (what an observer derives velocity from). */
	double lastX;
	double lastY;
	double lastZ;
	/** Ticks since this fighter last swung (an opponent can see the swing animation). */
	int ticksSinceSwing = 100;
	Inputs keys = Inputs.IDLE;

	// Statistics.
	int swings;
	int hits;
	int crits;
	int sprintHits;
	float damageDealt;

	SimFighter(Loadout loadout, double x, double z, float yaw) {
		this.loadout = loadout;
		this.x = x;
		this.y = 0;
		this.z = z;
		this.yaw = yaw;
		this.lastX = x;
		this.lastZ = z;
	}

	public double x() {
		return x;
	}

	public double z() {
		return z;
	}

	public float health() {
		return health;
	}

	public boolean sprinting() {
		return sprinting;
	}

	/** Player#getAttackStrengthScale(0.5). */
	float attackStrength() {
		return (float) Math.max(0, Math.min(1, (attackTicker + 0.5) / loadout.chargeTicks()));
	}

	Vec3 position() {
		return new Vec3(x, y, z);
	}

	Vec3 eye() {
		return new Vec3(x, y + EYE_HEIGHT, z);
	}

	SelfState selfState() {
		InventoryState inv = inventory();
		return new SelfState(position(), eye(), new Vec3(vx, vy, vz), yaw, pitch, health, MAX_HEALTH, 0, 20, onGround, sprinting, false,
			horizontalCollision, fallDistance, attackStrength(), REACH, hurtTime, true, FLAT, inv, List.of());
	}

	/** This fighter as its opponent sees it. */
	TargetState asTarget(int id) {
		Vec3 velocity = new Vec3(x - lastX, y - lastY, z - lastZ);
		return new TargetState(id, "Sim" + id, position(), velocity, yaw, health, MAX_HEALTH, onGround, hurtTime, false, true, 0, HALF_WIDTH, HEIGHT,
			ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, loadout.armor());
	}

	private static InventoryState inventory() {
		ItemInfo[] slots = new ItemInfo[InventoryState.SIZE];
		java.util.Arrays.fill(slots, ItemInfo.EMPTY);
		slots[0] = SWORD;
		ItemInfo[] armor = {ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY};
		return new InventoryState(slots, ItemInfo.EMPTY, armor, 0, false, false, 0, ItemKind.EMPTY, false);
	}

	/** Start of tick: knockback sent last tick arrives. */
	void receiveKnockback() {
		if (pendingMotion != null) {
			vx = pendingMotion.x();
			vy = pendingMotion.y();
			vz = pendingMotion.z();
			pendingMotion = null;
		}
	}

	/** Mouse movement, as the client applies it before anything else. */
	void look(Inputs in) {
		keys = in;
		yaw = Angles.wrapDegrees(yaw + in.yawDelta());
		pitch = Math.max(-90.0F, Math.min(90.0F, pitch + in.pitchDelta()));
	}

	/** A left click: hits {@code other} if the crosshair is on it within reach, and resets the charge either way. */
	void click(SimFighter other, Rng rng) {
		swings++;
		ticksSinceSwing = 0;
		Vec3 eye = eye();
		Vec3 dir = Angles.lookVector(yaw, pitch);
		double entry = rayEntry(eye, dir, new Vec3(other.x - HALF_WIDTH, other.y, other.z - HALF_WIDTH),
			new Vec3(other.x + HALF_WIDTH, other.y + HEIGHT, other.z + HALF_WIDTH), REACH);
		if (entry >= 0) {
			attack(other, rng);
		}
		attackTicker = 0;
	}

	private void attack(SimFighter other, Rng rng) {
		float strength = attackStrength();
		double base = loadout.attackDamage() * (0.2 + strength * strength * 0.8);
		double magic = strength * loadout.enchantBonus();
		boolean full = strength > 0.9F;
		boolean knockbackAttack = sprinting && full;
		boolean crit = full && fallDistance > 0 && !onGround && !sprinting;
		if (crit) {
			base *= 1.5;
		}
		float damage = (float) (base + magic);
		Vec3 oldMovement = new Vec3(other.vx, other.vy, other.vz);
		int landed = other.hurt(damage, this);
		boolean fullHit = landed == FULL;
		if (landed != MISSED) {
			hits++;
			if (crit) {
				crits++;
			}
		}
		if (fullHit && knockbackAttack) {
			sprintHits++;
			// Player#causeExtraKnockback: along the attacker's facing; the attacker slows down and stops sprinting.
			double rad = Math.toRadians(yaw);
			other.knockback(SPRINT_KNOCKBACK, Math.sin(rad), -Math.cos(rad), oldMovement);
			vx *= 0.6;
			vz *= 0.6;
			sprinting = false;
		}
	}

	private static final int MISSED = 0;
	private static final int PARTIAL = 1;
	private static final int FULL = 2;

	/** LivingEntity#hurtServer: {@link #FULL} (with knockback), {@link #PARTIAL} (the difference, while invulnerable) or {@link #MISSED}. */
	private int hurt(float damage, SimFighter attacker) {
		boolean full;
		float dealt;
		if (invulnerableTime > 10) {
			if (damage <= lastHurt) {
				return MISSED;
			}
			dealt = afterArmor(damage) - afterArmor(lastHurt);
			lastHurt = damage;
			full = false;
		} else {
			lastHurt = damage;
			invulnerableTime = 20;
			hurtTime = 10;
			dealt = afterArmor(damage);
			full = true;
		}
		health -= dealt;
		attacker.damageDealt += dealt;
		if (full) {
			// Default knockback: away from the attacker.
			knockback(DEFAULT_KNOCKBACK, attacker.x - x, attacker.z - z, new Vec3(vx, vy, vz));
		}
		return full ? FULL : PARTIAL;
	}

	/** CombatRules#getDamageAfterAbsorb. */
	private float afterArmor(float damage) {
		double toughness = 2.0 + loadout.toughness() / 4.0;
		double armor = Math.max(loadout.armor() * 0.2, Math.min(20.0, loadout.armor() - damage / toughness));
		return (float) (damage * (1.0 - armor / 25.0));
	}

	/**
	 * LivingEntity#knockback, computed on the velocity the server knows; the result reaches the fighter
	 * at the start of its next tick. Several pushes in one tick build on each other.
	 */
	private void knockback(double power, double dx, double dz, Vec3 base) {
		Vec3 now = pendingMotion != null ? pendingMotion : base;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-5) {
			dx = 0.01;
			dz = 0;
			len = 0.01;
		}
		double px = dx / len * power;
		double pz = dz / len * power;
		pendingMotion = new Vec3(now.x() / 2.0 - px, onGround ? Math.min(0.4, now.y() / 2.0 + power) : now.y(), now.z() / 2.0 - pz);
	}

	/** One tick of the body: sprint state, jumping, LivingEntity#travelInAir, timers. */
	void move(double arenaHalfSize) {
		Inputs in = keys;
		boolean forwardKey = in.forward() > 0;
		// ClientEmulator#updateSprint (LocalPlayer#aiStep): the sprint key starts a sprint while moving forward.
		if (!sprinting && forwardKey && in.sprint()) {
			sprinting = true;
		} else if (sprinting && (!forwardKey || horizontalCollision)) {
			sprinting = false;
		}

		// LocalPlayer#modifyInput.
		double left = in.strafe();
		double forward = in.forward();
		double length = Math.sqrt(left * left + forward * forward);
		if (length > 0) {
			left = left / length * 0.98;
			forward = forward / length * 0.98;
			double scaled = Math.sqrt(left * left + forward * forward);
			double dirX = Math.abs(left / scaled);
			double dirY = Math.abs(forward / scaled);
			double tan = dirY > dirX ? dirX / dirY : dirY / dirX;
			double modified = Math.min(scaled * Math.sqrt(1.0 + tan * tan), 1.0);
			left = left / scaled * modified;
			forward = forward / scaled * modified;
		}

		// LivingEntity#aiStep: jumping.
		if (noJumpDelay > 0) {
			noJumpDelay--;
		}
		if (in.jump()) {
			if (onGround && noJumpDelay == 0) {
				vy = Math.max(JUMP_POWER, vy);
				if (sprinting) {
					double rad = Math.toRadians(yaw);
					vx += -Math.sin(rad) * SPRINT_JUMP_BOOST;
					vz += Math.cos(rad) * SPRINT_JUMP_BOOST;
				}
				noJumpDelay = JUMP_DELAY;
			}
		} else {
			noJumpDelay = 0;
		}

		// LivingEntity#travelInAir.
		boolean grounded = onGround;
		double speed = grounded ? WALK_SPEED * (sprinting ? SPRINT_FACTOR : 1.0) : sprinting ? AIR_SPEED_SPRINTING : AIR_SPEED;
		double inLength = left * left + forward * forward;
		if (inLength > 1.0E-7) {
			double norm = inLength > 1.0 ? Math.sqrt(inLength) : 1.0;
			double ix = left / norm * speed;
			double iz = forward / norm * speed;
			double rad = Math.toRadians(yaw);
			double sin = Math.sin(rad);
			double cos = Math.cos(rad);
			vx += ix * cos - iz * sin;
			vz += iz * cos + ix * sin;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		// Entity#move on flat ground inside a walled square.
		double nx = x + vx;
		double nz = z + vz;
		double limit = arenaHalfSize - HALF_WIDTH;
		horizontalCollision = false;
		if (Math.abs(nx) > limit) {
			nx = Math.copySign(limit, nx);
			vx = 0;
			horizontalCollision = true;
		}
		if (Math.abs(nz) > limit) {
			nz = Math.copySign(limit, nz);
			vz = 0;
			horizontalCollision = true;
		}
		x = nx;
		z = nz;
		double ny = y + vy;
		if (ny <= 0) {
			ny = 0;
			vy = 0;
			onGround = true;
			fallDistance = 0;
		} else {
			if (vy < 0) {
				fallDistance -= vy;
			}
			onGround = false;
		}
		y = ny;
		vy -= GRAVITY;
		double friction = grounded ? GROUND_FRICTION : AIR_FRICTION;
		vx *= friction;
		vz *= friction;
		vy *= VERTICAL_DRAG;
		if (onGround) {
			vy = 0;
		}

		attackTicker++;
		ticksSinceSwing++;
		if (invulnerableTime > 0) {
			invulnerableTime--;
		}
		if (hurtTime > 0) {
			hurtTime--;
		}
	}

	boolean dead() {
		return health <= 0;
	}

	/** Distance along the ray at which it enters the box, or -1 if it misses within {@code maxDistance}. */
	static double rayEntry(Vec3 origin, Vec3 dir, Vec3 min, Vec3 max, double maxDistance) {
		double tMin = 0;
		double tMax = maxDistance;
		double[] o = {origin.x(), origin.y(), origin.z()};
		double[] d = {dir.x(), dir.y(), dir.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1e-9) {
				if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
					return -1;
				}
			} else {
				double t1 = (lo[axis] - o[axis]) / d[axis];
				double t2 = (hi[axis] - o[axis]) / d[axis];
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
				if (tMin > tMax) {
					return -1;
				}
			}
		}
		return tMin;
	}
}

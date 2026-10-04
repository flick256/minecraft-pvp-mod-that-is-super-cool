package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;

/**
 * An arrow in flight (AbstractArrow#tick): moves by its velocity, stops at the first solid block or
 * hits a body on the way, then loses 1% of its speed (40% in water) and falls by 0.05 a tick. A hit
 * does ceil(speed x damage) (2, plus Power), a fully drawn shot's critical arrow a random bit more
 * (AbstractArrow#onHitEntity); it knocks back along its flight (LivingEntity#dealDefaultKnockback).
 */
final class SimArrow {
	private static final double GRAVITY = 0.05;
	private static final double DRAG = 0.99;
	private static final double WATER_DRAG = 0.6;
	private static final int MAX_LIFE = 200;

	final SimFighter owner;
	double x;
	double y;
	double z;
	private double vx;
	private double vy;
	private double vz;
	private final boolean crit;
	private final double baseDamage;
	private int life;
	boolean done;
	/** Closest pass to the target's middle so far: distance, dx, dy, dz (diagnostics). */
	double[] closest;
	/** It hit its target (diagnostics). */
	boolean hit;

	SimArrow(SimFighter owner, double x, double y, double z, Vec3 velocity, boolean crit, double baseDamage) {
		this.owner = owner;
		this.x = x;
		this.y = y;
		this.z = z;
		this.vx = velocity.x();
		this.vy = velocity.y();
		this.vz = velocity.z();
		this.crit = crit;
		this.baseDamage = baseDamage;
	}

	void tick(SimWorld world, SimFighter target, Rng rng) {
		if (done) {
			return;
		}
		if (++life > MAX_LIFE) {
			done = true;
			return;
		}
		boolean inWater = world.type((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) == SimWorld.WATER;
		if (inWater) {
			vx *= WATER_DRAG;
			vy *= WATER_DRAG;
			vz *= WATER_DRAG;
		}
		Vec3 from = new Vec3(x, y, z);
		Vec3 v = new Vec3(vx, vy, vz);
		double cdx = x - target.x;
		double cdy = y - (target.y + 0.9);
		double cdz = z - target.z;
		double cd = Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz);
		if (closest == null || cd < closest[0]) {
			closest = new double[] {cd, cdx, cdy, cdz};
		}
		double len = v.length();
		SimWorld.Hit block = len < 1.0E-7 ? null : world.clip(from, v.scale(1.0 / len), len, SimWorld.Clip.COLLIDER);
		double reach = block == null ? len : block.distance();
		// ProjectileUtil#getEntityHitResult with the margin of ProjectileUtil#computeMargin.
		double margin = Math.max(0, Math.min(0.3, (life - 2) / 20.0));
		Vec3 min = target.boxMin().subtract(new Vec3(margin, margin, margin));
		Vec3 max = target.boxMax().add(new Vec3(margin, margin, margin));
		double entry = len < 1.0E-7 ? -1 : SimFighter.rayEntry(from, v.scale(1.0 / len), min, max, reach);
		if (entry >= 0 && !target.dead()) {
			double speed = len;
			int damage = (int) Math.ceil(speed * baseDamage);
			if (crit) {
				damage += rng.nextInt(0, damage / 2 + 1);
			}
			int landed = target.hurt(damage, SimFighter.Damage.ARROW, owner, false, target.x - vx, target.z - vz);
			if (landed != SimFighter.MISSED && landed != SimFighter.BLOCKED) {
				owner.arrowHits++;
			}
			hit = true;
			done = true;
			return;
		}
		if (block != null) {
			done = true; // stuck in the block
			return;
		}
		x += vx;
		y += vy;
		z += vz;
		if (!inWater) {
			vx *= DRAG;
			vy *= DRAG;
			vz *= DRAG;
		}
		vy -= GRAVITY;
	}
}

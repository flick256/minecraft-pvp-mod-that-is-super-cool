package io.github.flick256.sparbot.core.aim;

import java.util.OptionalDouble;

/**
 * Projectile flight, simulated tick by tick in the exact order vanilla 26.2 updates each projectile,
 * so the bot can work out the pitch a human would learn by practice. Results are aims, not
 * guarantees: vanilla still adds its own random spread when the projectile is launched.
 */
public final class Ballistics {
	/** How a projectile updates each tick. */
	public enum Order {
		/** ThrowableProjectile#tick (pearls, splash potions): gravity, drag, move. */
		GRAVITY_DRAG_MOVE,
		/** AbstractArrow#tick: move, drag, gravity. */
		MOVE_DRAG_GRAVITY,
		/** FishingHook#tick: gravity, move, drag. */
		GRAVITY_MOVE_DRAG
	}

	/** How the launcher turns the player's pitch into an initial (horizontal, vertical) velocity. */
	public interface Launch {
		double[] velocity(float pitchDegrees, double speed);
	}

	/** Projectile#shootFromRotation: velocity = look vector * speed. */
	public static final Launch LOOK_VECTOR = (pitch, speed) -> {
		double rad = Math.toRadians(pitch);
		return new double[] {Math.cos(rad) * speed, -Math.sin(rad) * speed};
	};

	/**
	 * FishingHook(Player, ...): direction (horizontal 1, vertical -tan(pitch) clamped to [-5, 5]),
	 * scaled by 0.6 / |direction| + ~0.5 (vanilla adds a small triangle-distributed random term).
	 */
	public static final Launch FISHING_HOOK = (pitch, speed) -> {
		double vertical = Math.max(-5.0, Math.min(5.0, -Math.tan(Math.toRadians(pitch))));
		double length = Math.sqrt(1 + vertical * vertical);
		double scale = 0.6 / length + 0.5;
		return new double[] {scale, vertical * scale};
	};

	public record Projectile(String name, double speed, double gravity, double drag, Order order, Launch launch) {
	}

	public static final Projectile ARROW_FULL_BOW = new Projectile("arrow (full bow)", 3.0, 0.05, 0.99, Order.MOVE_DRAG_GRAVITY, LOOK_VECTOR);
	public static final Projectile ARROW_CROSSBOW = new Projectile("arrow (crossbow)", 3.15, 0.05, 0.99, Order.MOVE_DRAG_GRAVITY, LOOK_VECTOR);
	public static final Projectile ENDER_PEARL = new Projectile("ender pearl", 1.5, 0.03, 0.99, Order.GRAVITY_DRAG_MOVE, LOOK_VECTOR);
	public static final Projectile HOOK = new Projectile("fishing hook", 1.0, 0.03, 0.92, Order.GRAVITY_MOVE_DRAG, FISHING_HOOK);

	private static final int MAX_TICKS = 200;

	private Ballistics() {
	}

	/** Result of one simulated flight to a horizontal distance. */
	public record Flight(double heightAtTarget, int ticks) {
	}

	/**
	 * Height (relative to the launch point) the projectile has when it has travelled
	 * {@code horizontalDistance}, or {@code NEGATIVE_INFINITY} if it never gets that far.
	 */
	public static Flight simulate(Projectile p, float pitch, double horizontalDistance) {
		double[] v = p.launch().velocity(pitch, p.speed());
		double vh = v[0];
		double vy = v[1];
		double x = 0;
		double y = 0;
		for (int tick = 1; tick <= MAX_TICKS; tick++) {
			double prevX = x;
			double prevY = y;
			switch (p.order()) {
				case GRAVITY_DRAG_MOVE -> {
					vy -= p.gravity();
					vh *= p.drag();
					vy *= p.drag();
					x += vh;
					y += vy;
				}
				case MOVE_DRAG_GRAVITY -> {
					x += vh;
					y += vy;
					vh *= p.drag();
					vy *= p.drag();
					vy -= p.gravity();
				}
				case GRAVITY_MOVE_DRAG -> {
					vy -= p.gravity();
					x += vh;
					y += vy;
					vh *= p.drag();
					vy *= p.drag();
				}
			}
			if (x >= horizontalDistance) {
				double t = (horizontalDistance - prevX) / (x - prevX);
				return new Flight(prevY + (y - prevY) * t, tick);
			}
			if (y < -256) {
				break;
			}
		}
		return new Flight(Double.NEGATIVE_INFINITY, MAX_TICKS);
	}

	/**
	 * Low-arc pitch (Minecraft convention: negative looks up) that lands the projectile
	 * {@code dy} blocks above the launch point at {@code horizontalDistance}, or empty when out of range.
	 */
	public static OptionalDouble solvePitch(Projectile p, double horizontalDistance, double dy) {
		// On the low arc, aiming higher (more negative pitch) always lands higher, up to the
		// maximum-range angle; bisect between that and aiming steeply down.
		float high = maxRangePitch(p, horizontalDistance);
		float low = 80.0F;
		if (simulate(p, high, horizontalDistance).heightAtTarget() < dy) {
			return OptionalDouble.empty();
		}
		for (int i = 0; i < 40; i++) {
			float mid = (high + low) / 2;
			if (simulate(p, mid, horizontalDistance).heightAtTarget() >= dy) {
				high = mid;
			} else {
				low = mid;
			}
		}
		return OptionalDouble.of((high + low) / 2.0);
	}

	/** Pitch giving the greatest height at the distance (coarse search; the arc peaks around -30 to -45). */
	private static float maxRangePitch(Projectile p, double horizontalDistance) {
		float best = 0;
		double bestHeight = Double.NEGATIVE_INFINITY;
		for (float pitch = 0; pitch >= -70; pitch -= 1.0F) {
			double h = simulate(p, pitch, horizontalDistance).heightAtTarget();
			if (h > bestHeight) {
				bestHeight = h;
				best = pitch;
			}
		}
		return best;
	}

	/** Flight time in ticks for a solved pitch (used to lead moving targets). */
	public static int flightTicks(Projectile p, float pitch, double horizontalDistance) {
		return simulate(p, pitch, horizontalDistance).ticks();
	}
}

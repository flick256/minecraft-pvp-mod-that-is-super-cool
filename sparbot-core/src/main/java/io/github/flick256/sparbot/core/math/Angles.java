package io.github.flick256.sparbot.core.math;

/**
 * Rotation helpers using Minecraft's angle convention: yaw 0 faces +Z (south), yaw 90 faces -X
 * (west); pitch is positive looking down. Kept identical to the game so values can be passed through
 * unchanged.
 */
public final class Angles {
	private Angles() {
	}

	/** Wraps an angle in degrees into [-180, 180). */
	public static float wrapDegrees(float degrees) {
		float d = degrees % 360.0F;
		if (d >= 180.0F) {
			d -= 360.0F;
		}
		if (d < -180.0F) {
			d += 360.0F;
		}
		return d;
	}

	/** Yaw (degrees) that looks from {@code from} towards {@code to}. */
	public static float yawTowards(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		return wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
	}

	/** Pitch (degrees) that looks from {@code from} towards {@code to}. */
	public static float pitchTowards(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dy = to.y() - from.y();
		double dz = to.z() - from.z();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
	}

	/** Unit look vector for a yaw/pitch pair, the same formula as Entity#calculateViewVector. */
	public static Vec3 lookVector(float yaw, float pitch) {
		double pitchRad = Math.toRadians(pitch);
		double negYawRad = Math.toRadians(-yaw);
		double cosPitch = Math.cos(pitchRad);
		return new Vec3(Math.sin(negYawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(negYawRad) * cosPitch);
	}

	/** Absolute shortest angular difference between two yaws, in degrees. */
	public static float yawDistance(float a, float b) {
		return Math.abs(wrapDegrees(a - b));
	}
}

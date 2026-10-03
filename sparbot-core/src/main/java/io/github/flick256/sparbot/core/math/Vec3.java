package io.github.flick256.sparbot.core.math;

/** Immutable 3D vector. Minecraft-free twin of the game's Vec3 so the brain stays pure Java. */
public record Vec3(double x, double y, double z) {
	public static final Vec3 ZERO = new Vec3(0, 0, 0);

	public Vec3 add(Vec3 o) {
		return new Vec3(x + o.x, y + o.y, z + o.z);
	}

	public Vec3 subtract(Vec3 o) {
		return new Vec3(x - o.x, y - o.y, z - o.z);
	}

	public Vec3 scale(double s) {
		return new Vec3(x * s, y * s, z * s);
	}

	public double length() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	public double horizontalLength() {
		return Math.sqrt(x * x + z * z);
	}

	public double distanceTo(Vec3 o) {
		return subtract(o).length();
	}

	public double horizontalDistanceTo(Vec3 o) {
		return subtract(o).horizontalLength();
	}
}

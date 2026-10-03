package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.math.Vec3;

/** A block position the bot can see. */
public record BlockSpot(int x, int y, int z) {
	/** Centre of the block's top face: where a player looks to click on top of it. */
	public Vec3 topCenter() {
		return new Vec3(x + 0.5, y + 1.0, z + 0.5);
	}

	/** Horizontal distance from the block's centre column to {@code p}. */
	public double horizontalDistanceTo(Vec3 p) {
		double dx = x + 0.5 - p.x();
		double dz = z + 0.5 - p.z();
		return Math.sqrt(dx * dx + dz * dz);
	}
}

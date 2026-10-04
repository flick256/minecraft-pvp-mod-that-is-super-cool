package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;

/**
 * Where a simulated duel is fought: a walled square of stone, optionally with some uneven ground (raised
 * patches, pillars and dips, so a policy learns to deal with more than a flat floor), and how far apart
 * the two fighters start.
 *
 * @param half the inside spans -half to half - 1 in x and z
 * @param minStart closest start distance
 * @param maxStart farthest start distance
 * @param terrain uneven ground
 * @param legacyStarts the original sword-duel starts: {@code minStart} blocks apart along x, a little offset in z
 */
public record SimArena(int half, int wallHeight, double minStart, double maxStart, boolean terrain, boolean legacyStarts) {
	/** The sword duel arena: 24 x 24. */
	public static final SimArena DUEL = new SimArena(12, 6, 8, 8, false, true);
	/** A UHC duel arena: 32 x 32, flat. */
	public static final SimArena UHC = new SimArena(16, 6, 8, 16, false, false);
	/** A UHC duel arena with uneven ground. */
	public static final SimArena UHC_TERRAIN = new SimArena(16, 6, 8, 16, true, false);

	/** Start positions and facings: {x0, z0, x1, z1, yaw0, yaw1}. */
	double[] starts(Rng rng) {
		if (legacyStarts) {
			double offset = rng.nextDouble() * 4 - 2;
			return new double[] {-minStart / 2, offset, minStart / 2, -offset, -90, 90};
		}
		double d = minStart + rng.nextDouble() * (maxStart - minStart);
		double angle = rng.nextDouble() * Math.PI * 2;
		double room = half - 2.0 - d / 2;
		double cx = room > 0 ? (rng.nextDouble() * 2 - 1) * Math.min(room, 4) : 0;
		double cz = room > 0 ? (rng.nextDouble() * 2 - 1) * Math.min(room, 4) : 0;
		double x0 = cx - Math.cos(angle) * d / 2;
		double z0 = cz - Math.sin(angle) * d / 2;
		double x1 = cx + Math.cos(angle) * d / 2;
		double z1 = cz + Math.sin(angle) * d / 2;
		// Stand in the middle of a block.
		x0 = Math.floor(x0) + 0.5;
		z0 = Math.floor(z0) + 0.5;
		x1 = Math.floor(x1) + 0.5;
		z1 = Math.floor(z1) + 0.5;
		return new double[] {x0, z0, x1, z1, Angles.yawTowards(new Vec3(x0, 0, z0), new Vec3(x1, 0, z1)),
			Angles.yawTowards(new Vec3(x1, 0, z1), new Vec3(x0, 0, z0))};
	}

	/** The blocks, with the spaces around the two starts kept clear. */
	SimWorld build(Rng rng, double[] starts) {
		SimWorld w = SimWorld.arena(half, wallHeight, rng);
		if (!terrain) {
			return w;
		}
		int patches = rng.nextInt(0, 4);
		for (int k = 0; k < patches; k++) {
			int sx = rng.nextInt(2, 5);
			int sz = rng.nextInt(2, 5);
			int x0 = rng.nextInt(-half, half - sx);
			int z0 = rng.nextInt(-half, half - sz);
			for (int x = x0; x < x0 + sx; x++) {
				for (int z = z0; z < z0 + sz; z++) {
					if (clear(x, z, starts)) {
						w.init(x, 0, z, SimWorld.STONE);
					}
				}
			}
		}
		int pillars = rng.nextInt(0, 3);
		for (int k = 0; k < pillars; k++) {
			int x = rng.nextInt(-half, half - 1);
			int z = rng.nextInt(-half, half - 1);
			int h = rng.nextInt(2, 3);
			if (clear(x, z, starts)) {
				for (int y = 0; y < h; y++) {
					w.init(x, y, z, SimWorld.STONE);
				}
			}
		}
		int dips = rng.nextInt(0, 2);
		for (int k = 0; k < dips; k++) {
			int s = rng.nextInt(2, 3);
			int x0 = rng.nextInt(-half, half - s);
			int z0 = rng.nextInt(-half, half - s);
			for (int x = x0; x < x0 + s; x++) {
				for (int z = z0; z < z0 + s; z++) {
					if (clear(x, z, starts) && w.type(x, 0, z) == SimWorld.AIR) {
						w.init(x, -1, z, SimWorld.AIR);
					}
				}
			}
		}
		return w;
	}

	private static boolean clear(int x, int z, double[] starts) {
		for (int i = 0; i < 2; i++) {
			if (Math.abs(x + 0.5 - starts[2 * i]) < 2.0 && Math.abs(z + 0.5 - starts[2 * i + 1]) < 2.0) {
				return false;
			}
		}
		return true;
	}
}

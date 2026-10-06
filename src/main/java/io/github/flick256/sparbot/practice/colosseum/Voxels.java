package io.github.flick256.sparbot.practice.colosseum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sparse set of blocks for the sculptures (statues and creatures), stored per column so the blueprint
 * can look a column up at once. Coordinates are relative to the colosseum's centre. Later shapes overwrite
 * earlier ones, so details go on after the bulk.
 */
final class Voxels {
	private final Map<Long, Map<Integer, BlockState>> columns = new HashMap<>();
	private int minX = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxZ = Integer.MIN_VALUE;

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	void set(int x, int y, int z, BlockState state) {
		columns.computeIfAbsent(key(x, z), k -> new HashMap<>()).put(y, state);
		minX = Math.min(minX, x);
		maxX = Math.max(maxX, x);
		minZ = Math.min(minZ, z);
		maxZ = Math.max(maxZ, z);
	}

	/** The blocks in column (x, z), or null when there are none. */
	Map<Integer, BlockState> column(int x, int z) {
		if (x < minX || x > maxX || z < minZ || z > maxZ) {
			return null;
		}
		return columns.get(key(x, z));
	}

	int size() {
		int n = 0;
		for (Map<Integer, BlockState> c : columns.values()) {
			n += c.size();
		}
		return n;
	}

	/** A solid ball. */
	void ball(double cx, double cy, double cz, double r, BlockState state) {
		int r0 = (int) Math.ceil(r);
		for (int x = -r0; x <= r0; x++) {
			for (int y = -r0; y <= r0; y++) {
				for (int z = -r0; z <= r0; z++) {
					if (x * x + y * y + z * z <= r * r) {
						set((int) Math.round(cx + x), (int) Math.round(cy + y), (int) Math.round(cz + z), state);
					}
				}
			}
		}
	}

	/** A solid ellipsoid, axis-aligned. */
	void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, Shader shader) {
		for (int x = (int) -Math.ceil(rx); x <= Math.ceil(rx); x++) {
			for (int y = (int) -Math.ceil(ry); y <= Math.ceil(ry); y++) {
				for (int z = (int) -Math.ceil(rz); z <= Math.ceil(rz); z++) {
					double e = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
					if (e <= 1) {
						BlockState s = shader.at(x / rx, y / ry, z / rz, e);
						if (s != null) {
							set((int) Math.round(cx + x), (int) Math.round(cy + y), (int) Math.round(cz + z), s);
						}
					}
				}
			}
		}
	}

	/** What an ellipsoid is made of at normalised position (x, y, z), e its "radius squared" (1 at the skin). */
	interface Shader {
		BlockState at(double x, double y, double z, double e);
	}

	/** A box between two corners (inclusive). */
	void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	/** A tube from a to b, its radius going from ra to rb. */
	void tube(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, BlockState state) {
		double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
		int steps = Math.max(1, (int) Math.ceil(len / 0.5));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			ball(ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, ra + (rb - ra) * t, state);
		}
	}

	/** A one-block-thick triangle (a wing membrane). */
	void triangle(double[] a, double[] b, double[] c, BlockState state) {
		double ab = dist(a, b);
		double ac = dist(a, c);
		int n = (int) Math.ceil(Math.max(ab, ac) / 0.45) + 1;
		for (int i = 0; i <= n; i++) {
			for (int j = 0; j <= n - i; j++) {
				double u = i / (double) n;
				double v = j / (double) n;
				double x = a[0] + (b[0] - a[0]) * u + (c[0] - a[0]) * v;
				double y = a[1] + (b[1] - a[1]) * u + (c[1] - a[1]) * v;
				double z = a[2] + (b[2] - a[2]) * u + (c[2] - a[2]) * v;
				set((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), state);
			}
		}
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
	}

	/** Every column with blocks, as (x, z) pairs. */
	List<int[]> keys() {
		List<int[]> out = new ArrayList<>();
		for (Long k : columns.keySet()) {
			out.add(new int[] {(int) (k >> 32), (int) (long) k});
		}
		return out;
	}
}

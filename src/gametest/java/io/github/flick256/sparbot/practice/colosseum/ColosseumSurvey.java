package io.github.flick256.sparbot.practice.colosseum;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walks the colosseum's blueprint (no world needed) the way a player would, over one quarter of it (the south-east,
 * from the east gate to the south gate): standing on solid blocks, stepping or jumping up one block where there is
 * headroom, dropping up to three, climbing ladders. Then it checks what a player can reach:
 * <ul>
 * <li>each of the six great halls in the quarter, its floor and its balconies;</li>
 * <li>the ways into the three secret vaults in the quarter;</li>
 * <li>the second concourse (from the seats);</li>
 * <li>and, all the way round, no water anywhere with an open side it could run out of.</li>
 * </ul>
 */
public final class ColosseumSurvey {
	private static final int R = 160;
	private static final int YLO = CelestialColosseum.F - 1;
	private static final int YHI = CelestialColosseum.F + 120;

	private final Map<Long, BlockState[]> columns = new HashMap<>();
	/** Which columns the walk may use. */
	private final java.util.function.BiPredicate<Integer, Integer> region;

	private ColosseumSurvey(java.util.function.BiPredicate<Integer, Integer> region) {
		this.region = region;
	}

	/** The south-east quarter, and both sides of its two gates' passages (the Grand Stairs climb on both sides). */
	private static boolean quarter(int dx, int dz) {
		return (dx >= -2 && dz >= -2 || dx > 0 && Math.abs(dz) <= 14 || dz > 0 && Math.abs(dx) <= 14) && dx * dx + dz * dz <= R * R;
	}

	private static long col(int dx, int dz) {
		return ((long) dx << 32) ^ (dz & 0xffffffffL);
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 2048) << 40) | ((long) (dz + 2048) << 20) | (y + 2048);
	}

	private BlockState at(int dx, int y, int dz) {
		BlockState[] c = columns.computeIfAbsent(col(dx, dz), k -> region.test(dx, dz) || Math.abs(dx) <= R && Math.abs(dz) <= R ? CelestialColosseum.column(dx, dz)
			: null);
		if (c == null) {
			return Blocks.STONE.defaultBlockState();
		}
		return CelestialColosseum.at(c, y);
	}

	private static double top(BlockState s) {
		VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
	}

	/** Nothing to bump into (air, a carpet, a sign, a torch, water). */
	private static boolean open(BlockState s) {
		return top(s) <= 0.2 && !s.is(Blocks.LAVA);
	}

	private boolean stand(int dx, int y, int dz) {
		BlockState below = at(dx, y - 1, dz);
		return open(at(dx, y, dz)) && open(at(dx, y + 1, dz)) && (!open(below) || below.getBlock() instanceof LadderBlock
			|| at(dx, y, dz).getBlock() instanceof LadderBlock) && !below.is(Blocks.WATER);
	}

	/** What a survey found wrong (empty if nothing). */
	public static List<String> run() {
		return new ColosseumSurvey(ColosseumSurvey::quarter).survey();
	}

	private List<String> survey() {
		List<String> problems = new ArrayList<>();
		int[] start = {145, CelestialColosseum.F, 0};
		if (!stand(start[0], start[1], start[2])) {
			problems.add("can't stand at the start in the east gate passage");
			return problems;
		}
		LongOpenHashSet seen = walk(start[0], start[1], start[2], ColosseumSurvey::quarter);
		Set<String> reached = new HashSet<>();
		boolean concourse2 = false;
		for (long k : seen) {
			int dx = (int) (k >>> 40) - 2048;
			int dz = (int) (k >>> 20 & 0xfffff) - 2048;
			int y = (int) (k & 0xfffff) - 2048;
			double d = Math.sqrt(dx * dx + dz * dz);
			String room = room(dx, y, dz, d);
			if (room != null) {
				reached.add(room);
			}
			if (d > CelestialColosseum.TIER2 && d <= CelestialColosseum.CONCOURSE2 && y == CelestialColosseum.F + 77) {
				concourse2 = true;
			}
		}
		io.github.flick256.sparbot.SparBot.LOGGER.info("Colosseum survey: {} standing places, {} halls reached, {} expected", seen.size(), reached.size(),
			expected().size());
		if (!concourse2) {
			problems.add("the second concourse can't be reached from the seats");
		}
		for (int q : new int[] {0, 1, 3}) {
			int[] e = Vaults.entrance(q);
			boolean near = false;
			for (long k : seen) {
				int dx = (int) (k >>> 40) - 2048;
				int dz = (int) (k >>> 20 & 0xfffff) - 2048;
				int y = (int) (k & 0xfffff) - 2048;
				near |= y == e[1] && Math.hypot(dx - e[0], dz - e[2]) < 2.6;
			}
			if (!near) {
				problems.add("the way into vault " + q + " (" + Vaults.NAMES[q][0] + " " + Vaults.NAMES[q][1] + ") can't be reached");
			}
		}
		for (String want : expected()) {
			if (!reached.contains(want)) {
				problems.add("unreachable: " + want);
			}
		}
		water(problems);
		return problems;
	}

	/** Everywhere a player can get to from (dx, y, dz), within {@code inside}. */
	private LongOpenHashSet walk(int x0, int y0, int z0, java.util.function.BiPredicate<Integer, Integer> inside) {
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		seen.add(key(x0, y0, z0));
		queue.enqueue(key(x0, y0, z0));
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			long k = queue.dequeueLong();
			int dx = (int) (k >>> 40) - 2048;
			int dz = (int) (k >>> 20 & 0xfffff) - 2048;
			int y = (int) (k & 0xfffff) - 2048;
			if (at(dx, y, dz).getBlock() instanceof LadderBlock) {
				for (int dy : new int[] {1, -1}) {
					if (stand(dx, y + dy, dz) || at(dx, y + dy, dz).getBlock() instanceof LadderBlock && open(at(dx, y + dy + 1, dz))) {
						visit(seen, queue, dx, y + dy, dz);
					}
				}
			}
			for (int[] s : steps) {
				int nx = dx + s[0];
				int nz = dz + s[1];
				if (!inside.test(nx, nz)) {
					continue;
				}
				if (open(at(nx, y, nz)) && open(at(nx, y + 1, nz))) {
					// Walk on, or drop down to the first floor within three blocks.
					for (int fall = 0; fall <= 3; fall++) {
						int ny = y - fall;
						if (ny < YLO - 40) {
							break;
						}
						if (stand(nx, ny, nz)) {
							visit(seen, queue, nx, ny, nz);
							break;
						}
						if (!open(at(nx, ny - 1, nz)) || at(nx, ny - 1, nz).is(Blocks.WATER)) {
							break;
						}
					}
				} else if (open(at(dx, y + 2, dz)) && stand(nx, y + 1, nz) && y + 1 <= YHI) {
					// Up a step (a stair, or a jump).
					visit(seen, queue, nx, y + 1, nz);
				} else if (at(nx, y, nz).getBlock() instanceof LadderBlock && open(at(nx, y + 1, nz))) {
					visit(seen, queue, nx, y, nz);
				}
			}
		}
		return seen;
	}

	private void visit(LongOpenHashSet seen, LongArrayFIFOQueue queue, int dx, int y, int dz) {
		long k = key(dx, y, dz);
		if (seen.add(k)) {
			queue.enqueue(k);
		}
	}

	/** Which hall and level a standing place is in ("hall/floor", "hall/balcony", "hall/upper"), or null. */
	private static String room(int dx, int y, int dz, double d) {
		int hall = GrandHalls.at(d, Math.toDegrees(Math.atan2(dz, dx)));
		if (hall < 0) {
			return null;
		}
		String name = GrandHalls.name(hall)[0] + " " + GrandHalls.name(hall)[1];
		if (y == CelestialColosseum.F) {
			return name + "/floor";
		}
		if (y == CelestialColosseum.F + GrandHalls.B1) {
			return name + "/balcony";
		}
		if (y == CelestialColosseum.F + GrandHalls.B2) {
			return name + "/upper";
		}
		return null;
	}

	/** The six halls in the quarter: each one's floor, its balcony, and (in the outer Galleries) its upper balcony. */
	private static List<String> expected() {
		List<String> out = new ArrayList<>();
		for (int g = 0; g < 3; g++) {
			for (int h = 0; h < 2; h++) {
				int hall = g * 8 + h;
				String name = GrandHalls.name(hall)[0] + " " + GrandHalls.name(hall)[1];
				out.add(name + "/floor");
				out.add(name + "/balcony");
				if (g > 0) {
					out.add(name + "/upper");
				}
			}
		}
		return out;
	}

	/**
	 * Every water block in the quarter (and in the floating islands' springs), and in the halls' floors all the way
	 * round, closed in on every side but the top.
	 */
	private void water(List<String> problems) {
		List<int[]> cols = new ArrayList<>();
		for (int dx = -(R - 2); dx <= R - 2; dx++) {
			for (int dz = -(R - 2); dz <= R - 2; dz++) {
				if (dx * dx + dz * dz <= (R - 2) * (R - 2)) {
					cols.add(new int[] {dx, dz});
				}
			}
		}
		int leaks = 0;
		for (int[] c : cols) {
			boolean quarter = c[0] >= 0 && c[1] >= 0;
			for (int y = CelestialColosseum.S - (quarter ? 30 : 3); y <= (quarter ? YHI : CelestialColosseum.F + 1); y++) {
				if (!at(c[0], y, c[1]).is(Blocks.WATER)) {
					continue;
				}
				int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
				for (int[] a : around) {
					BlockState n = at(c[0] + a[0], y + a[1], c[1] + a[2]);
					if (!n.is(Blocks.WATER) && (n.isAir() || n.canBeReplaced(Fluids.FLOWING_WATER))) {
						if (leaks++ < 10) {
							problems.add("water at " + c[0] + ", " + y + ", " + c[1] + " can run " + a[0] + "/" + a[1] + "/" + a[2] + " into " + n);
						}
					}
				}
			}
		}
		if (leaks > 10) {
			problems.add("... and " + (leaks - 10) + " more leaks");
		}
	}

	// --- Viewpoints for the client test's tour ---

	/** A place to look from: blueprint x, y, z, yaw and pitch, and the screenshot's name. */
	public record View(String name, double x, double y, double z, float yaw, float pitch) {
	}

	private static float yaw(double vx, double vz) {
		return (float) Math.toDegrees(Math.atan2(-vx, vz));
	}

	/** Standing at distance {@code d} on bearing {@code deg}, {@code h} above storey {@code k}'s floor, looking outward (or in). */
	private static View polar(String name, double d, double deg, int k, double h, boolean outward, float pitch) {
		double a = Math.toRadians(deg);
		double cx = Math.cos(a);
		double cz = Math.sin(a);
		return new View(name, cx * d + 0.5, CelestialColosseum.F + ColosseumInterior.STOREY * k + h, cz * d + 0.5, outward ? yaw(cx, cz) : yaw(-cx, -cz), pitch);
	}

	/** Every great hall, seen from inside its first door on the inner corridor, looking across and along it. */
	public static List<View> halls() {
		List<View> out = new ArrayList<>();
		for (int hall = 0; hall < GrandHalls.HALLS.length; hall++) {
			int g = hall / 8;
			int q = hall / 2 % 4;
			int h = hall % 2;
			double[] band = ColosseumInterior.ROOMS[g];
			double d = band[0] + 2.5;
			double gw = GrandHalls.gateHalf(g);
			double len = Math.toRadians(45) * d - gw - GrandHalls.PIER;
			double arc = gw + len / 6;
			double phi = Math.toDegrees(arc / d);
			double deg = q * 90 + (h == 0 ? phi : 90 - phi);
			double a = Math.toRadians(deg);
			// Look along the hall toward its far end, a little outward.
			double ta = Math.toRadians(q * 90 + (h == 0 ? 45 : 45));
			double tx = Math.cos(ta) * (band[0] + band[1]) / 2 - Math.cos(a) * d;
			double tz = Math.sin(ta) * (band[0] + band[1]) / 2 - Math.sin(a) * d;
			String name = (GrandHalls.name(hall)[0] + " " + GrandHalls.name(hall)[1]).trim().toLowerCase(java.util.Locale.ROOT).replace("'", "")
				.replace(' ', '-');
			out.add(new View("hall-" + (hall + 1) + "-" + name, Math.cos(a) * d + 0.5, CelestialColosseum.F + 3.2, Math.sin(a) * d + 0.5, yaw(tx, tz),
				8));
		}
		return out;
	}

	/** The way in to vault {@code q} (blueprint dx, lower block's y, dz), and a look inside it. */
	public static int[] vaultEntrance(int q) {
		return Vaults.entrance(q);
	}

	public static View vault(int q) {
		double[] c = Vaults.centre(q);
		int[] e = Vaults.entrance(q);
		return new View("colosseum-vault-" + q, e[0] + 0.5, c[1] + 1.6, e[2] + 0.5, yaw(c[0] - e[0], c[2] - e[2]), 10);
	}

	/** Along the way down: the stairwell in the Cells, the stair, the Hall of the Fallen. */
	public static List<View> descent() {
		double c = Heartwell.COS;
		double s = Heartwell.SIN;
		List<View> out = new ArrayList<>();
		out.add(new View("colosseum-lower-door", c * 76 + 0.5, CelestialColosseum.F + 2.5, s * 76 + 0.5, yaw(-c, -s), 30));
		out.add(new View("colosseum-descent", c * 60 + 0.5, Heartwell.stairFeet(60) + 1.2, s * 60 + 0.5, yaw(-c, -s), 18));
		out.add(new View("colosseum-hall-of-the-fallen", c * 41 + 0.5, Heartwell.FEET + 1.2, s * 41 + 0.5, yaw(-c, -s), 2));
		return out;
	}

	public static View plaque() {
		double c = Heartwell.COS;
		double s = Heartwell.SIN;
		double a = Heartwell.STAIR_FOOT - 2 + 0.5;
		return new View("colosseum-champions-plaque", c * a - s * 1.5 + 0.5, Heartwell.FEET + 1.0, s * a + c * 1.5 + 0.5, yaw(-s, c), 8);
	}
}

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
 * <li>every hall in the quarter, on every level, and every level of the stair halls;</li>
 * <li>the second concourse (from the seats);</li>
 * <li>no water anywhere with an open side it could run out of.</li>
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

	private static boolean quarter(int dx, int dz) {
		return dx >= -2 && dz >= -2 && dx * dx + dz * dz <= R * R;
	}

	/** The way down: a strip along the Heartwell's bearing, and the arena. */
	private static boolean descent(int dx, int dz) {
		double a = dx * Heartwell.COS + dz * Heartwell.SIN;
		double lat = -dx * Heartwell.SIN + dz * Heartwell.COS;
		return Math.abs(lat) <= 9 && a >= -26 && a <= 82 || Math.hypot(dx, dz) <= Heartwell.SHELL + 1;
	}

	private static long col(int dx, int dz) {
		return ((long) dx << 32) ^ (dz & 0xffffffffL);
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 2048) << 40) | ((long) (dz + 2048) << 20) | (y + 2048);
	}

	private BlockState at(int dx, int y, int dz) {
		BlockState[] c = columns.computeIfAbsent(col(dx, dz), k -> region.test(dx, dz) ? CelestialColosseum.column(dx, dz) : null);
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
		List<String> problems = new ColosseumSurvey(ColosseumSurvey::quarter).survey();
		problems.addAll(new ColosseumSurvey(ColosseumSurvey::descent).down());
		return problems;
	}

	/** From the Cells, down the Lower Door's stair, through the Hall of the Fallen, into the arena and up onto the throne's dais. */
	private List<String> down() {
		List<String> problems = new ArrayList<>();
		int sx = (int) Math.round(Heartwell.COS * 75);
		int sz = (int) Math.round(Heartwell.SIN * 75);
		if (!stand(sx, CelestialColosseum.F, sz)) {
			problems.add("can't stand in the Cells at " + sx + ", " + sz);
			return problems;
		}
		LongOpenHashSet seen = walk(sx, CelestialColosseum.F, sz, ColosseumSurvey::descent);
		boolean hall = false;
		boolean arena = false;
		boolean dais = false;
		for (long k : seen) {
			int dx = (int) (k >>> 40) - 2048;
			int dz = (int) (k >>> 20 & 0xfffff) - 2048;
			int y = (int) (k & 0xfffff) - 2048;
			double a = dx * Heartwell.COS + dz * Heartwell.SIN;
			double d = Math.hypot(dx, dz);
			hall |= y == Heartwell.FEET && a >= Heartwell.HALL && a < Heartwell.STAIR_FOOT;
			arena |= y == Heartwell.FEET && d < 10;
			dais |= y == Heartwell.FEET + 2 && -a > 16.5 && d < Heartwell.RADIUS;
		}
		if (!hall) {
			problems.add("the Hall of the Fallen can't be reached down the Lower Door's stair");
		}
		if (!arena) {
			problems.add("the arena can't be reached from the Hall of the Fallen");
		}
		if (!dais) {
			problems.add("the throne's dais can't be climbed");
		}
		return problems;
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

	/** Which hall a standing place is in ("gallery/sector/level", the annex behind a stair marked "+annex"), or null. */
	private static String room(int dx, int y, int dz, double d) {
		int band = ColosseumInterior.band(ColosseumInterior.ROOMS, d);
		if (band < 0 || (y - CelestialColosseum.F) % ColosseumInterior.STOREY != 0) {
			return null;
		}
		int k = (y - CelestialColosseum.F) / ColosseumInterior.STOREY;
		double deg = Math.toDegrees(Math.atan2(dz, dx));
		int sector = ColosseumInterior.sector(deg);
		double u = d - ColosseumInterior.ROOMS[band][0];
		boolean annex = band == 1 && StairHall.is(band, sector) && k <= StairHall.top(band) && u >= ColosseumInterior.ANNEX;
		return (band + 1) + "/" + (sector + 1) + "/" + (k + 1) + (annex ? "+annex" : "");
	}

	/** Every hall in the quarter (sectors 2 to 11, clear of the gates' passages), on every level a corridor reaches. */
	private static List<String> expected() {
		List<String> out = new ArrayList<>();
		for (int band = 0; band < 3; band++) {
			for (int sector = 1; sector <= 10; sector++) {
				for (int k = 0; k <= ColosseumInterior.TOP[band]; k++) {
					double outer = ColosseumInterior.ROOMS[band][1] - 0.5;
					if (!ColosseumInterior.storey(outer, k)) {
						continue;
					}
					boolean stair = StairHall.is(band, sector);
					if (stair && k > StairHall.top(band) && band != 1) {
						continue;
					}
					out.add((band + 1) + "/" + (sector + 1) + "/" + (k + 1));
					if (stair && band == 1 && k <= StairHall.top(band)) {
						out.add((band + 1) + "/" + (sector + 1) + "/" + (k + 1) + "+annex");
					}
				}
			}
		}
		return out;
	}

	/** Every water block in the quarter, and in the floating islands' springs, closed in on every side but the top. */
	private void water(List<String> problems) {
		List<int[]> cols = new ArrayList<>();
		for (int dx = 0; dx <= R - 2; dx++) {
			for (int dz = 0; dz <= R - 2; dz++) {
				if (dx * dx + dz * dz <= (R - 2) * (R - 2)) {
					cols.add(new int[] {dx, dz});
				}
			}
		}
		int leaks = 0;
		for (int[] c : cols) {
			for (int y = CelestialColosseum.S - 30; y <= YHI; y++) {
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

	/** Halls of {@code n} different kinds, each seen from just inside its door. */
	public static List<View> halls(int n) {
		List<View> out = new ArrayList<>();
		Set<Integer> kinds = new HashSet<>();
		int[][] levels = {{2, 0}, {1, 0}, {2, 3}, {0, 0}, {1, 2}, {2, 6}, {0, 2}, {1, 4}, {2, 8}, {1, 6}, {0, 3}, {2, 1}, {1, 1}, {2, 5}};
		for (int[] bk : levels) {
			int band = bk[0];
			int k = bk[1];
			for (int sector = 2; sector < 46 && out.size() < n; sector += 3) {
				if (StairHall.is(band, sector) || Landmarks.at(band, sector, k) >= 0 || sector % 12 == 0 || sector % 12 == 11) {
					continue;
				}
				int kind = RoomStyles.kind(band, sector, k, false);
				if (kinds.contains(kind)) {
					continue;
				}
				boolean in = RoomStyles.doorIn(band, sector, k, false);
				double depth = ColosseumInterior.ROOMS[band][1] - ColosseumInterior.ROOMS[band][0];
				double u = in ? 1.2 : depth - 1.2;
				double d = ColosseumInterior.ROOMS[band][0] + u;
				if (!ColosseumInterior.storey(d, k)) {
					continue;
				}
				kinds.add(kind);
				String noun = RoomKinds.NOUNS[kind][0].toLowerCase(java.util.Locale.ROOT);
				out.add(polar("colosseum-hall-" + noun, d, (sector + 0.5) * ColosseumInterior.SECTOR, k, 1.4, in, 12));
			}
		}
		return out;
	}

	/** The stair halls: the ground floor of one, looking up its flights, and a high landing of another. */
	public static List<View> stairs() {
		return List.of(polar("colosseum-stairhall", ColosseumInterior.ROOMS[1][0] + 1.0, 6.5 * ColosseumInterior.SECTOR, 0, 1.4, true, -12),
			polar("colosseum-stairhall-high", ColosseumInterior.ROOMS[2][0] + 1.5, 30.5 * ColosseumInterior.SECTOR, 5, 1.4, true, -8));
	}

	/** The way in to vault {@code q} (blueprint dx, lower block's y, dz), and a look inside it. */
	public static int[] vaultEntrance(int q) {
		return Vaults.entrance(q);
	}

	public static View vault(int q) {
		double deg = Math.toDegrees(Vaults.angle(q));
		double d = ColosseumInterior.ROOMS[2][0] + Vaults.START + 0.8;
		double a = Math.toRadians(deg);
		return new View("colosseum-vault-" + q, Math.cos(a) * d + 0.5, Vaults.floorY(q) + 1.6, Math.sin(a) * d + 0.5, yaw(Math.cos(a), Math.sin(a)), 12);
	}

	/** Along the way down: the stairwell in the Cells, the stair, the Hall of the Fallen, the arena and its dome. */
	public static List<View> heartwell() {
		double c = Heartwell.COS;
		double s = Heartwell.SIN;
		List<View> out = new ArrayList<>();
		out.add(new View("colosseum-lower-door", c * 76 + 0.5, CelestialColosseum.F + 2.5, s * 76 + 0.5, yaw(-c, -s), 30));
		out.add(new View("colosseum-descent", c * 60 + 0.5, Heartwell.stairFeet(60) + 1.2, s * 60 + 0.5, yaw(-c, -s), 18));
		out.add(new View("colosseum-hall-of-the-fallen", c * 41 + 0.5, Heartwell.FEET + 1.2, s * 41 + 0.5, yaw(-c, -s), 2));
		out.add(new View("colosseum-heartwell", c * 21 + 0.5, Heartwell.FEET + 3.5, s * 21 + 0.5, yaw(-c, -s), 4));
		out.add(new View("colosseum-heartwell-dome", -c * 8 + 0.5, Heartwell.FEET + 2, -s * 8 + 0.5, yaw(c, s), -40));
		out.add(new View("colosseum-heartwell-throne", -c * 10 + 0.5, Heartwell.FEET + 3, -s * 10 + 0.5, yaw(-c, -s), 6));
		return out;
	}

	/** Where a challenger steps into the arena (blueprint x, feet y, z), and the plaque in the Hall of the Fallen. */
	public static double[] arenaStep() {
		return new double[] {Heartwell.COS * (Heartwell.RADIUS - 4) + 0.5, Heartwell.FEET, Heartwell.SIN * (Heartwell.RADIUS - 4) + 0.5};
	}

	public static View plaque() {
		double c = Heartwell.COS;
		double s = Heartwell.SIN;
		double a = Heartwell.STAIR_FOOT - 2 + 0.5;
		return new View("colosseum-champions-plaque", c * a - s * 1.5 + 0.5, Heartwell.FEET + 1.0, s * a + c * 1.5 + 0.5, yaw(-s, c), 8);
	}
}

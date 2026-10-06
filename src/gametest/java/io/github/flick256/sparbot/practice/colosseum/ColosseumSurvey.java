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

	private ColosseumSurvey() {
	}

	private static long col(int dx, int dz) {
		return ((long) dx << 32) ^ (dz & 0xffffffffL);
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 2048) << 40) | ((long) (dz + 2048) << 20) | (y + 2048);
	}

	private BlockState at(int dx, int y, int dz) {
		BlockState[] c = columns.computeIfAbsent(col(dx, dz), k -> {
			if (dx < -2 || dz < -2 || dx > R || dz > R || dx * dx + dz * dz > R * R) {
				return null;
			}
			return CelestialColosseum.column(dx, dz);
		});
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
		return new ColosseumSurvey().survey();
	}

	private List<String> survey() {
		List<String> problems = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		int[] start = {145, CelestialColosseum.F, 0};
		if (!stand(start[0], start[1], start[2])) {
			problems.add("can't stand at the start in the east gate passage");
			return problems;
		}
		seen.add(key(start[0], start[1], start[2]));
		queue.enqueue(key(start[0], start[1], start[2]));
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		Set<String> reached = new HashSet<>();
		boolean concourse2 = false;
		while (!queue.isEmpty()) {
			long k = queue.dequeueLong();
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
			boolean ladder = at(dx, y, dz).getBlock() instanceof LadderBlock;
			if (ladder) {
				for (int dy : new int[] {1, -1}) {
					if (stand(dx, y + dy, dz) || at(dx, y + dy, dz).getBlock() instanceof LadderBlock && open(at(dx, y + dy + 1, dz))) {
						visit(seen, queue, dx, y + dy, dz);
					}
				}
			}
			for (int[] s : steps) {
				int nx = dx + s[0];
				int nz = dz + s[1];
				if (nx < -2 || nz < -2 || nx * nx + nz * nz > R * R) {
					continue;
				}
				if (open(at(nx, y, nz)) && open(at(nx, y + 1, nz))) {
					// Walk on, or drop down to the first floor within three blocks.
					for (int fall = 0; fall <= 3; fall++) {
						int ny = y - fall;
						if (ny < YLO) {
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
			for (int y = CelestialColosseum.S - 6; y <= YHI; y++) {
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
}

package io.github.flick256.sparbot.practice.colosseum;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walks the Deep's blueprint (no world needed) the way a player would: standing on solid blocks, stepping or jumping up
 * one, dropping up to three. It checks:
 * <ul>
 * <li>the way down: from the Cells, down the stair, through the Hall of the Fallen to the Brink, along the ledge and
 * out over the Oath Bridge to the platform; and not through the Laurel Door while it's shut;</li>
 * <li>the Leap: a clear drop down the Well onto the arena's floor;</li>
 * <li>the arena: shut in (no way out with the Gate of Triumph closed), and the throne's dais can be climbed, with room
 * on it for Vaelor;</li>
 * <li>the Triumphal Way, with its gates open: from the arena to every pedestal in the Hall of Triumph, up the Gallery of
 * Witness, up the Stair of Stars, through the Laurel Door onto the ledge and out to the platform;</li>
 * <li>no lava with an open side it could run out of (but the falls, which run down into their pools).</li>
 * </ul>
 */
public final class DeepSurvey {
	private final Map<Long, BlockState[]> columns = new HashMap<>();
	private final boolean open;

	private DeepSurvey(boolean open) {
		this.open = open;
	}

	private static long col(int dx, int dz) {
		return ((long) dx << 32) ^ (dz & 0xffffffffL);
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 2048) << 40) | ((long) (dz + 2048) << 20) | (y + 2048);
	}

	private BlockState at(int dx, int y, int dz) {
		if (open && (TheDeep.gate(dx, dz) && y >= TheDeep.FEET && y <= TheDeep.FEET + 5
			|| TheDeep.laurelDoor(dx, dz) && y >= TheDeep.BRINK_FEET && y <= TheDeep.BRINK_FEET + 3)) {
			return Blocks.AIR.defaultBlockState();
		}
		BlockState[] c = columns.computeIfAbsent(col(dx, dz), k -> CelestialColosseum.column(dx, dz));
		if (c == null) {
			return Blocks.STONE.defaultBlockState();
		}
		return CelestialColosseum.at(c, y);
	}

	private static double top(BlockState s) {
		VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
	}

	private static boolean open(BlockState s) {
		return top(s) <= 0.2 && !s.is(Blocks.LAVA) && !s.is(Blocks.WATER);
	}

	private boolean stand(int dx, int y, int dz) {
		return open(at(dx, y, dz)) && open(at(dx, y + 1, dz)) && !open(at(dx, y - 1, dz)) && !at(dx, y - 1, dz).is(Blocks.LAVA)
			&& !(at(dx, y - 1, dz).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock);
	}

	private LongOpenHashSet walk(int x0, int y0, int z0, BiPredicate<Integer, Integer> inside) {
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
			for (int[] s : steps) {
				int nx = dx + s[0];
				int nz = dz + s[1];
				if (!inside.test(nx, nz)) {
					continue;
				}
				if (open(at(nx, y, nz)) && open(at(nx, y + 1, nz))) {
					for (int fall = 0; fall <= 3; fall++) {
						int ny = y - fall;
						if (stand(nx, ny, nz)) {
							visit(seen, queue, nx, ny, nz);
							break;
						}
						if (!open(at(nx, ny - 1, nz))) {
							break;
						}
					}
				} else if (open(at(dx, y + 2, dz)) && stand(nx, y + 1, nz)) {
					visit(seen, queue, nx, y + 1, nz);
				}
			}
		}
		return seen;
	}

	private static void visit(LongOpenHashSet seen, LongArrayFIFOQueue queue, int dx, int y, int dz) {
		long k = key(dx, y, dz);
		if (seen.add(k)) {
			queue.enqueue(k);
		}
	}

	private interface Place {
		boolean test(int dx, int y, int dz);
	}

	private static boolean any(LongOpenHashSet seen, Place p) {
		for (long k : seen) {
			if (p.test((int) (k >>> 40) - 2048, (int) (k & 0xfffff) - 2048, (int) (k >>> 20 & 0xfffff) - 2048)) {
				return true;
			}
		}
		return false;
	}

	private static double a(int dx, int dz) {
		return dx * TheDeep.COS + dz * TheDeep.SIN;
	}

	private static double lat(int dx, int dz) {
		return -dx * TheDeep.SIN + dz * TheDeep.COS;
	}

	private static double a2(int dx, int dz) {
		return dx * TheDeep.COS2 + dz * TheDeep.SIN2;
	}

	private static double l2(int dx, int dz) {
		return -dx * TheDeep.SIN2 + dz * TheDeep.COS2;
	}

	/** What the survey found wrong (empty if nothing). */
	public static List<String> run() {
		List<String> problems = new ArrayList<>();
		new DeepSurvey(false).down(problems);
		new DeepSurvey(false).arena(problems);
		new DeepSurvey(true).way(problems);
		new DeepSurvey(false).lava(problems);
		return problems;
	}

	/** From the Cells down to the Brink and out to the platform; the Laurel Door keeps the Stair of Stars shut. */
	private void down(List<String> problems) {
		int sx = (int) Math.round(TheDeep.COS * 75);
		int sz = (int) Math.round(TheDeep.SIN * 75);
		if (!stand(sx, CelestialColosseum.F, sz)) {
			problems.add("can't stand in the Cells at " + sx + ", " + sz);
			return;
		}
		BiPredicate<Integer, Integer> region = (dx, dz) -> Math.abs(lat(dx, dz)) <= 9 && a(dx, dz) >= -2 && a(dx, dz) <= 82
			|| Math.hypot(dx, dz) <= TheDeep.LEDGE + 1 || a2(dx, dz) > 0 && a2(dx, dz) < 60 && Math.abs(l2(dx, dz)) <= 4;
		LongOpenHashSet seen = walk(sx, CelestialColosseum.F, sz, region);
		if (!any(seen, (dx, y, dz) -> y == Heartwell.FEET && a(dx, dz) >= Heartwell.HALL && a(dx, dz) < Heartwell.STAIR_FOOT)) {
			problems.add("the Hall of the Fallen can't be reached down the Lower Door's stair");
		}
		if (!any(seen, (dx, y, dz) -> y == TheDeep.BRINK_FEET && Math.hypot(dx, dz) > 18 && Math.hypot(dx, dz) < TheDeep.LEDGE
			&& Math.abs(TheDeep.diff(Math.atan2(dz, dx), TheDeep.C)) > Math.PI / 2)) {
			problems.add("the ledge round the Brink can't be walked round to the far side");
		}
		if (!any(seen, (dx, y, dz) -> y == TheDeep.BRINK_FEET && Math.hypot(dx, dz) < TheDeep.PLATFORM - 0.5)) {
			problems.add("the platform over the Well can't be reached over the Oath Bridge");
		}
		if (any(seen, (dx, y, dz) -> a2(dx, dz) > TheDeep.LEDGE + 2.5 && Math.abs(l2(dx, dz)) < 2.5 && y >= TheDeep.BRINK_FEET - 6)) {
			problems.add("the Stair of Stars can be walked into from the Brink with the Laurel Door shut");
		}
		// The Leap: a clear drop down the Well onto the arena floor.
		for (int y = TheDeep.BRINK_FEET + 1; y >= TheDeep.FEET; y--) {
			if (!open(at(0, y, 0))) {
				problems.add("the Well is blocked at y " + y + " by " + at(0, y, 0));
				break;
			}
		}
		if (open(at(0, TheDeep.FLOOR, 0))) {
			problems.add("there's no floor under the Well");
		}
	}

	/** The arena is shut in, and the dais and throne are as they should be. */
	private void arena(List<String> problems) {
		LongOpenHashSet seen = walk(0, TheDeep.FEET, 0, (dx, dz) -> Math.hypot(dx, dz) <= TheDeep.REACH);
		if (any(seen, (dx, y, dz) -> Math.hypot(dx, dz) > TheDeep.ARENA + 1.5)) {
			problems.add("there's a way out of the arena with the Gate of Triumph shut");
		}
		double[] seat = TheDeep.throneSeat();
		int sx = (int) Math.floor(seat[0] + 0.5);
		int sz = (int) Math.floor(seat[2] + 0.5);
		if (!any(seen, (dx, y, dz) -> y == TheDeep.DAIS_TOP && Math.hypot(dx - sx, dz - sz) < 3)) {
			problems.add("the throne's dais can't be climbed");
		}
		for (int y = TheDeep.DAIS_TOP; y < TheDeep.DAIS_TOP + 4; y++) {
			if (!open(at(sx, y, sz))) {
				problems.add("Vaelor's seat is blocked at y " + y + " by " + at(sx, y, sz));
			}
		}
		if (open(at(sx, TheDeep.DAIS_TOP - 1, sz))) {
			problems.add("there's nothing under Vaelor's seat");
		}
		for (int i = 0; i < 6; i++) {
			double ca = TheDeep.C + Math.toRadians(30 + 60 * i);
			int cx = (int) Math.round(Math.cos(ca) * 21);
			int cz = (int) Math.round(Math.sin(ca) * 21);
			if (open(at(cx, TheDeep.FEET + 2, cz))) {
				problems.add("broken column " + i + " is missing");
			}
		}
	}

	/** With the gates open, from the arena all the way up. */
	private void way(List<String> problems) {
		LongOpenHashSet seen = walk(0, TheDeep.FEET, 0, (dx, dz) -> Math.hypot(dx, dz) <= TheDeep.REACH);
		io.github.flick256.sparbot.SparBot.LOGGER.info("Deep survey: {} standing places from the arena with the gates open", seen.size());
		if (!any(seen, (dx, y, dz) -> y == TheDeep.FEET && a(dx, dz) > TheDeep.FACADE + 2 && a(dx, dz) < TheDeep.HALL_FROM && Math.abs(lat(dx, dz)) < 2)) {
			problems.add("the bridge over the moat can't be reached from the arena through the Gate of Triumph");
		}
		int[][] pedestals = TheDeep.pedestals();
		for (int i = 0; i < pedestals.length; i++) {
			int[] p = pedestals[i];
			if (!any(seen, (dx, y, dz) -> y == TheDeep.FEET && Math.hypot(dx - p[0], dz - p[1]) <= 1.5)) {
				problems.add("pedestal " + (i + 1) + " in the Hall of Triumph can't be walked up to");
			}
			if (open(at(p[0], TheDeep.FEET + 1, p[1]))) {
				problems.add("pedestal " + (i + 1) + " is missing");
			}
		}
		if (!any(seen, (dx, y, dz) -> y >= TheDeep.GALLERY_TOP && Math.hypot(dx, dz) > 85 && TheDeep.diff(Math.atan2(dz, dx), TheDeep.C) > TheDeep.TURN
			- 0.15)) {
			problems.add("the top of the Gallery of Witness can't be reached");
		}
		if (!any(seen, (dx, y, dz) -> y == TheDeep.BRINK_FEET && a2(dx, dz) > TheDeep.LEDGE + 1 && a2(dx, dz) < TheDeep.LEDGE + 8
			&& Math.abs(l2(dx, dz)) < 2.5)) {
			problems.add("the top of the Stair of Stars can't be reached; on it, the furthest in: " + furthest(seen));
		}
		if (!any(seen, (dx, y, dz) -> y == TheDeep.BRINK_FEET && Math.hypot(dx, dz) > 17 && Math.hypot(dx, dz) < TheDeep.LEDGE)) {
			problems.add("the Brink can't be reached through the Laurel Door");
		}
		if (!any(seen, (dx, y, dz) -> y == TheDeep.BRINK_FEET && Math.hypot(dx, dz) < TheDeep.PLATFORM - 0.5)) {
			problems.add("the platform can't be reached on the way back up");
		}
	}

	/** The standing place on the Stair of Stars furthest in (smallest along), for a report. */
	private String furthest(LongOpenHashSet seen) {
		double best = 1e9;
		String out = "none";
		for (long k : seen) {
			int dx = (int) (k >>> 40) - 2048;
			int dz = (int) (k >>> 20 & 0xfffff) - 2048;
			int y = (int) (k & 0xfffff) - 2048;
			if (Math.abs(l2(dx, dz)) < 2.6 && a2(dx, dz) > 20 && a2(dx, dz) < best && y > -10) {
				best = a2(dx, dz);
				StringBuilder next = new StringBuilder();
				for (int dy = -2; dy <= 3; dy++) {
					next.append(" ").append(y + dy).append("=").append(at((int) Math.round(dx - TheDeep.COS2), y + dy, (int) Math.round(dz - TheDeep.SIN2)).getBlock()
						.getDescriptionId());
				}
				out = dx + "," + y + "," + dz + " (along " + Math.round(a2(dx, dz) * 10) / 10.0 + ", floor " + TheDeep.stairFloor(a2(dx, dz)) + "); inward:" + next;
			}
		}
		return out;
	}

	/** No lava with an open side, but the falls. */
	private void lava(List<String> problems) {
		int leaks = 0;
		int r = (int) TheDeep.REACH;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int y = TheDeep.GROUND - 6; y <= 0; y++) {
					if (!at(dx, y, dz).is(Blocks.LAVA)) {
						continue;
					}
					boolean fall = open(at(dx, y - 1, dz)) && !at(dx, y - 1, dz).is(Blocks.LAVA);
					if (fall) {
						continue;
					}
					int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] n : around) {
						BlockState s = at(dx + n[0], y, dz + n[1]);
						if (!s.is(Blocks.LAVA) && open(s)) {
							if (leaks++ < 10) {
								problems.add("lava at " + dx + ", " + y + ", " + dz + " can run into " + (dx + n[0]) + ", " + (dz + n[1]));
							}
						}
					}
				}
			}
		}
		if (leaks > 10) {
			problems.add("... and " + (leaks - 10) + " more lava leaks");
		}
	}

	// --- For the client test's tour ---

	private static float yaw(double vx, double vz) {
		return (float) Math.toDegrees(Math.atan2(-vx, vz));
	}

	private static ColosseumSurvey.View polar(String name, double r, double ang, double y, double lookAng, float pitch) {
		return new ColosseumSurvey.View(name, Math.cos(ang) * r + 0.5, y, Math.sin(ang) * r + 0.5, yaw(Math.cos(lookAng), Math.sin(lookAng)), pitch);
	}

	/** Viewpoints round the Deep (blueprint x, y, z). */
	public static java.util.List<ColosseumSurvey.View> views() {
		double c = TheDeep.C;
		double c2 = TheDeep.C + TheDeep.TURN;
		java.util.List<ColosseumSurvey.View> out = new java.util.ArrayList<>();
		out.add(polar("deep-brink", 27, c, TheDeep.BRINK_FEET + 1.6, c + Math.PI, 6));
		out.add(polar("deep-heart", 9, c, TheDeep.BRINK_FEET + 1.6, c + Math.PI, -22));
		out.add(polar("deep-well", 3.2, c, TheDeep.BRINK_FEET + 1.6, c + Math.PI, 80));
		out.add(polar("deep-cavern", 62, c2, 12, c2 + Math.PI, 28));
		out.add(polar("deep-colosseum", 52, c + Math.PI, -6, c, 30));
		out.add(polar("deep-arena", 27, c, TheDeep.FEET + 1.6, c + Math.PI, -6));
		out.add(polar("deep-vaelor-throne", 25, c + Math.PI, TheDeep.DAIS_TOP + 1.2, c + Math.PI, 6));
		out.add(polar("deep-facade", 88, c + Math.toRadians(40), TheDeep.GROUND + 4, c + Math.toRadians(40) + Math.PI, -18));
		double mid = (TheDeep.GALLERY_FROM + TheDeep.GALLERY_END) / 2;
		double ang = c + mid;
		out.add(new ColosseumSurvey.View("deep-gallery", Math.cos(ang) * (TheDeep.wall(ang) - 1) + 0.5, TheDeep.galleryFeet(mid) + 0.6,
			Math.sin(ang) * (TheDeep.wall(ang) - 1) + 0.5, yaw(-Math.sin(ang) - Math.cos(ang) * 0.6, Math.cos(ang) - Math.sin(ang) * 0.6), 4));
		out.add(polar("deep-stair-of-stars", 63, c2, TheDeep.stairFloor(63) + 1.6, c2 + Math.PI, 24));
		return out;
	}

	/** Where to stand: on the platform by the Well, and in the Well (the Leap). */
	public static double[] platform() {
		return new double[] {Math.cos(TheDeep.C) * 3 + 0.5, TheDeep.BRINK_FEET, Math.sin(TheDeep.C) * 3 + 0.5};
	}

	public static double[] inTheWell() {
		return new double[] {0.5, TheDeep.BRINK_FEET - 3, 0.5};
	}

	/** In front of the throne, looking at it: blueprint x, y, z and yaw. */
	public static double[] beforeThrone() {
		double ang = TheDeep.C + Math.PI;
		return new double[] {Math.cos(ang) * 18 + 0.5, TheDeep.FEET, Math.sin(ang) * 18 + 0.5, yaw(Math.cos(ang), Math.sin(ang))};
	}

	/** Just out through the Gate of Triumph, looking down the Avenue. */
	public static double[] avenue() {
		return new double[] {Math.cos(TheDeep.C) * 30 + 0.5, TheDeep.FEET, Math.sin(TheDeep.C) * 30 + 0.5, yaw(Math.cos(TheDeep.C), Math.sin(TheDeep.C))};
	}

	/** In the Hall of Triumph's door, looking at the apse. */
	public static double[] hall() {
		return new double[] {Math.cos(TheDeep.C) * 85 + 0.5, TheDeep.FEET, Math.sin(TheDeep.C) * 85 + 0.5, yaw(Math.cos(TheDeep.C), Math.sin(TheDeep.C))};
	}

	/** Beside reward {@code i}'s pedestal, on the hall's side of it. */
	public static double[] byPedestal(int i) {
		double[] top = TheDeep.pedestalTop(i);
		double back = -1.3;
		return new double[] {top[0] + Math.cos(TheDeep.C) * back, TheDeep.FEET, top[2] + Math.sin(TheDeep.C) * back};
	}

	/** On the Stair of Stars by the Laurel Door. */
	public static double[] byLaurelDoor() {
		double ang = TheDeep.C + TheDeep.TURN;
		double r = TheDeep.LEDGE + 4;
		return new double[] {Math.cos(ang) * r + 0.5, TheDeep.stairFloor(r) + 1, Math.sin(ang) * r + 0.5};
	}

	public static int fieldTop() {
		return CelestialColosseum.S;
	}
}

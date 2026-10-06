package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Celestial Colosseum, the practice world's centrepiece: six hundred blocks across.
 *
 * <p>The Grand Bowl in the middle: a field almost a hundred blocks across, paved round a heraldic compass
 * rose; a rune-lit podium wall; three tiers of seats rising over a hundred blocks, split by two concourses,
 * with an imperial box over the south gate; a vaulted undercroft lit from its ceiling beneath the seats; a
 * colossal crown wall with four storeys of glowing arches and flying buttresses; sixteen great towers with
 * purpur spires; and a striped canopy over the top tier. Round it a paved plaza, a glowing moat crossed by
 * four bridges guarded by colossal knights, and cherry gardens. On the diagonals four satellite stadiums,
 * each a whole arena of its own (Fire, Frost, Grove and Void). Above: a dragon coiling over the bowl, a
 * phoenix, a sky whale, glowing jellyfish, a great halo, a sky citadel and floating islands with waterfalls.
 *
 * <p>The whole thing is a blueprint: {@link #column} gives every block of a column (or null where nothing
 * is built), so building and resetting are the same pass over the columns.
 */
public final class CelestialColosseum {
	public static final int CX = PracticeLayout.GRAND_X;
	public static final int CZ = PracticeLayout.GRAND_Z;
	public static final int REACH = PracticeLayout.GRAND_REACH;
	static final int S = PracticeLayout.SURFACE;
	static final int F = PracticeLayout.FLOOR;
	public static final int Y0 = S - 12;
	public static final int Y1 = 316;
	static final int H = Y1 - Y0 + 1;

	// The Grand Bowl, by distance from the centre.
	static final double FIELD = 46.5;
	static final double PODIUM = 50.5;
	static final double TIER1 = 80.5;
	static final double CONCOURSE1 = 86.5;
	static final double TIER2 = 116.5;
	static final double CONCOURSE2 = 121.5;
	static final double TIER3 = 146.5;
	static final double CROWN = 152.5;
	static final double BUTTRESS = 162.5;
	static final double PLAZA = 178.5;
	static final double MOAT = 192.5;
	static final double GARDEN = 216.0;
	static final double TOWER_RING = 149.5;
	static final double TOWER_R = 7.5;
	/** Satellite stadiums on the diagonals, this far from the centre on each axis. */
	static final double SAT = 176;
	static final double SAT_R = 48.5;

	/** The 0.8 colosseum stood here: columns of it outside this one's footprint are cleared back to desert. */
	private static final int OLD_X = 0;
	private static final int OLD_Z = -420;
	private static final int OLD_REACH = 122;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
	private static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
	private static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	private static final BlockState CHISELED = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
	private static final BlockState BLACK_BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState BLACK_WALL = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
	private static final BlockState BRICK_WALL = Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState();
	private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
	private static final BlockState SEA_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState END_ROD = Blocks.END_ROD.defaultBlockState();
	private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
	private static final BlockState SOUL_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState();
	private static final BlockState HANGING_SOUL = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	private static final BlockState PURPLE_GLASS = Blocks.STAINED_GLASS.purple().defaultBlockState();
	private static final BlockState PURPLE_PANE = Blocks.STAINED_GLASS_PANE.purple().defaultBlockState();
	private static final BlockState CHERRY_LOG = Blocks.CHERRY_LOG.defaultBlockState();
	private static final BlockState CHERRY_LEAVES = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState();
	private static final BlockState TUFF = Blocks.POLISHED_TUFF.defaultBlockState();
	private static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();
	private static final BlockState TUFF_CHISELED = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
	private static final BlockState WOOL_PURPLE = Blocks.WOOL.purple().defaultBlockState();
	private static final BlockState WOOL_WHITE = Blocks.WOOL.white().defaultBlockState();
	private static final BlockState PAD = Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState();

	private CelestialColosseum() {
	}

	// --- Columns ---

	public static int columns() {
		return (2 * REACH + 1) * (2 * REACH + 1);
	}

	public static int dx(int column) {
		return column % (2 * REACH + 1) - REACH;
	}

	public static int dz(int column) {
		return column / (2 * REACH + 1) - REACH;
	}

	/** The column index of (dx, dz). */
	public static int index(int dx, int dz) {
		return (dz + REACH) * (2 * REACH + 1) + dx + REACH;
	}

	/**
	 * Every block of the column at (dx, dz) from the centre, from {@link #Y0} to {@link #Y1}, or null where
	 * nothing is built (the desert is left as it is).
	 */
	public static BlockState[] column(int dx, int dz) {
		double d = Math.sqrt(dx * dx + dz * dz);
		Map<Integer, BlockState> sculpture = Sculptures.get().column(dx, dz);
		Sat sat = satellite(dx, dz);
		boolean sky = skyHere(dx, dz);
		boolean old = Math.abs(CX + dx - OLD_X) <= OLD_REACH && Math.abs(CZ + dz - OLD_Z) <= OLD_REACH;
		boolean landing = dz > GARDEN - 12 && dz <= LANDING_END && Math.abs(dx) <= 9.5;
		if (d > GARDEN && sat == null && sculpture == null && !sky && !old && !landing) {
			return null;
		}
		BlockState[] c = new BlockState[H];
		Arrays.fill(c, AIR);
		ground(c);
		double ang = Math.atan2(dz, dx);
		double deg = Math.toDegrees(ang);
		double arc = ang * d;
		Gate gate = gate(dx, dz, 6.5);
		if (sat != null) {
			satellite(c, sat);
		} else if (d <= FIELD) {
			field(c, dx, dz, d, deg);
		} else if (d <= PODIUM) {
			podium(c, dx, dz, d, deg, arc, gate);
		} else if (d <= TIER1) {
			tier(c, dx, dz, d, arc, gate, (int) Math.floor(d) - 51, F + 11, 1);
		} else if (d <= CONCOURSE1) {
			concourse(c, d, arc, gate, F + 42, TIER1 + 1);
		} else if (d <= TIER2) {
			tier(c, dx, dz, d, arc, gate, (int) Math.floor(d) - 87, F + 45, 2);
		} else if (d <= CONCOURSE2) {
			concourse(c, d, arc, gate, F + 76, TIER2 + 1);
		} else if (d <= TIER3) {
			tier(c, dx, dz, d, arc, gate, (int) Math.floor(d) - 122, F + 79, 3);
		} else if (d <= CROWN) {
			crown(c, dx, dz, d, arc, gate);
		} else if (d <= BUTTRESS) {
			buttress(c, d, deg, arc, gate);
		} else if (d <= PLAZA) {
			plaza(c, d, deg, gate);
		} else if (d <= MOAT) {
			moat(c, d, deg, gate);
		} else if (d <= GARDEN) {
			garden(c, dx, dz, d, gate);
		}
		if (landing) {
			landing(c, dx, dz);
		}
		if (dx == 0 && dz == RETURN_TUNNEL) {
			put(c, S, AMETHYST);
			put(c, F, PAD);
		}
		if (sat == null) {
			tower(c, dx, dz);
			canopy(c, d, deg);
			imperialBox(c, dx, dz, d, gate);
		}
		sky(c, dx, dz, d, deg);
		if (sculpture != null) {
			for (Map.Entry<Integer, BlockState> e : sculpture.entrySet()) {
				put(c, e.getKey(), e.getValue());
			}
		}
		return c;
	}

	private static void put(BlockState[] c, int y, BlockState state) {
		if (y >= Y0 && y <= Y1) {
			c[y - Y0] = state;
		}
	}

	private static void fill(BlockState[] c, int y0, int y1, BlockState state) {
		for (int y = Math.max(y0, Y0); y <= Math.min(y1, Y1); y++) {
			c[y - Y0] = state;
		}
	}

	private static BlockState at(BlockState[] c, int y) {
		return y >= Y0 && y <= Y1 ? c[y - Y0] : AIR;
	}

	/** The flat desert's own layers. */
	private static void ground(BlockState[] c) {
		fill(c, Y0, S - 5, SANDSTONE);
		fill(c, S - 4, S, SAND);
	}

	/** Outward from the centre, rounded to a compass direction. */
	static Direction outward(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Distance in blocks along the circle of radius d from the nearest multiple of {@code step} degrees. */
	static double offStep(double deg, double step, double d) {
		double off = Math.abs(deg - step * Math.round(deg / step));
		return Math.toRadians(off) * d;
	}

	/** A gate's corridor, along a compass direction from the centre. */
	record Gate(int gx, int gz, double along, double perp) {
	}

	static Gate gate(double dx, double dz, double half) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] g : dirs) {
			double along = dx * g[0] + dz * g[1];
			double perp = Math.abs(dx * g[1] - dz * g[0]);
			if (along > 0 && perp <= half) {
				return new Gate(g[0], g[1], along, perp);
			}
		}
		return null;
	}

	// --- The field ---

	/**
	 * The field: earth and stone to dig into, paved in flagstone courses round a heraldic compass rose, a
	 * knotwork band, and a tuff border with a gilded edge and glowing stones (the 0.8.1 floor, half as big
	 * again).
	 */
	private static void field(BlockState[] c, int dx, int dz, double d, double deg) {
		fill(c, Y0, S - 4, STONE);
		fill(c, S - 3, S - 1, DIRT);
		double k = 1.5;
		double e = d / k;
		double ang = Math.atan2(dz, dx);
		int ring = (int) Math.floor(d / 3);
		int stone = (int) Math.floor((ang + Math.PI) * Math.max(d, 1) / 4.0);
		BlockState floor = (ring + stone) % 2 == 0 ? TILES : POLISHED;
		int rose = compassRose(e, deg);
		if (d > FIELD - 1) {
			floor = BLACK_BRICKS;
		} else if (Math.abs(e - 29) < 0.5 / k) {
			floor = BLACKSTONE;
		} else if (e > 26.5) {
			floor = offStep(deg, 15, d) < 0.7 && Math.abs(e - 27.75) < 0.75 ? SEA_LANTERN : offStep(deg, 7.5, d) < 0.6 ? TUFF_CHISELED : TUFF_BRICKS;
		} else if (Math.abs(e - 26) < 0.5) {
			floor = GILDED;
		} else if (e > 16.5 && e < 19.5) {
			double t = ang * 18 / 2.2;
			double w1 = 18 + 0.9 * Math.sin(t);
			double w2 = 18 - 0.9 * Math.sin(t);
			floor = Math.abs(e - w1) < 0.45 || Math.abs(e - w2) < 0.45 ? BLACKSTONE : e < 16.9 || e > 19.1 ? TUFF_BRICKS : TUFF;
		} else if (e < 1.5) {
			floor = GILDED;
		} else if (e < 3.5) {
			floor = TUFF_CHISELED;
		} else if (Math.abs(e - 4) < 0.6) {
			floor = AMETHYST;
		} else if (Math.abs(e - 5) < 0.5) {
			floor = BLACK_BRICKS;
		} else if (rose == 3) {
			floor = BLACK_BRICKS;
		} else if (rose == 1) {
			floor = Blocks.CALCITE.defaultBlockState();
		} else if (rose == 2) {
			floor = TUFF;
		} else if (rose == 4) {
			floor = Blocks.SMOOTH_STONE.defaultBlockState();
		} else if (rose == 5) {
			floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
		}
		put(c, S, floor);
		if (d > FIELD - 1 && offStep(deg, 7.5, d) < 0.6 && gate(dx, dz, 6.5) == null) {
			put(c, F + 6, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, outward(-dx, -dz)));
		}
	}

	private static int compassRose(double d, double deg) {
		int main = star(d, deg, 0, 16, 7.5);
		if (main != 0) {
			return main;
		}
		int diagonal = star(d, deg, 45, 11.5, 5.5);
		return diagonal == 1 ? 4 : diagonal == 2 ? 5 : diagonal;
	}

	private static int star(double d, double deg, double first, double tip, double valley) {
		double p = first + 90 * Math.round((deg - first) / 90);
		double off = deg - p;
		double along = d * Math.cos(Math.toRadians(off));
		double across = Math.abs(d * Math.sin(Math.toRadians(off)));
		double corner = valley * Math.cos(Math.toRadians(45));
		if (along < 0 || along > tip) {
			return 0;
		}
		double halfWidth = along <= corner ? along : corner * (tip - along) / (tip - corner);
		if (across > halfWidth + 0.01) {
			return 0;
		}
		if (halfWidth - across < 0.5 || tip - along < 0.5) {
			return 3;
		}
		return off > 0 ? 1 : 2;
	}

	// --- The podium ---

	/** The podium wall: blackstone eleven high, crying-obsidian runes, a gilded band, braziers and soul lanterns on the crest. */
	private static void podium(BlockState[] c, int dx, int dz, double d, double deg, double arc, Gate gate) {
		fill(c, Y0, F + 9, BLACK_BRICKS);
		put(c, F + 8, GILDED);
		if (offStep(deg, 5, d) < 0.6) {
			put(c, F + 3, CRYING);
			put(c, F + 4, CRYING);
		}
		if (Math.floorMod(Math.round(arc), 2) == 0) {
			put(c, F + 10, BLACK_WALL);
		}
		if (offStep(deg + 5, 10, d) < 0.6 && d > 48.5) {
			// A brazier: soul fire burning on soul soil (a campfire's smoke would streak the stands).
			put(c, F + 10, Blocks.SOUL_SOIL.defaultBlockState());
			put(c, F + 11, Blocks.SOUL_FIRE.defaultBlockState());
		} else if (offStep(deg, 10, d) < 0.6 && d > 48.5) {
			put(c, F + 10, BLACK_BRICKS);
			put(c, F + 11, SOUL_LANTERN);
		}
		if (gate != null && gate.perp() <= 5.5) {
			fill(c, F, F + 7, AIR);
			put(c, S, POLISHED);
			put(c, F + 8, CHISELED);
		}
	}

	// --- The tiers ---

	/**
	 * One row of seats on tier {@code tier}: stairs facing the field (deepslate brick, blackstone, then
	 * deepslate tile going up), purpur aisles with a glowing step every third row, a three-block shell under
	 * them and, under that, the undercroft: open halls on a grid of pillars, lit from the shell's underside.
	 */
	private static void tier(BlockState[] c, int dx, int dz, double d, double arc, Gate gate, int k, int base, int tier) {
		int seat = base + k;
		BlockState shell = tier == 2 ? BLACK_BRICKS : BRICKS;
		fill(c, seat - 3, seat - 1, shell);
		boolean pillar = Math.floorMod(Math.round(d), 10) <= 1 && Math.floorMod(Math.round(arc), 12) <= 1;
		boolean tunnel = gate != null && gate.perp() <= 5.5;
		if (pillar && !tunnel) {
			fill(c, S, seat - 1, tier == 2 ? BLACK_BRICKS : TILES);
		} else {
			put(c, S, Math.floorMod(Math.round(d) + Math.round(arc), 2) == 0 ? POLISHED : TILES);
			if (Math.floorMod(Math.round(d), 10) == 5 && Math.floorMod(Math.round(arc), 12) == 6) {
				put(c, seat - 3, SEA_LANTERN);
			}
		}
		boolean aisle = Math.floorMod(Math.round(arc), 16) == 0;
		Block stair = aisle ? Blocks.PURPUR_STAIRS : tier == 1 ? Blocks.DEEPSLATE_BRICK_STAIRS : tier == 2 ? Blocks.POLISHED_BLACKSTONE_STAIRS
			: Blocks.DEEPSLATE_TILE_STAIRS;
		put(c, seat, stair.defaultBlockState().setValue(StairBlock.FACING, outward(dx, dz)));
		if (aisle && k % 3 == 1) {
			put(c, seat, SEA_LANTERN);
		}
		if (tunnel && tier == 1) {
			// The gate's tunnel through the first tier's low rows (above it the undercroft is open anyway).
			fill(c, F, Math.min(F + 7, seat - 4), AIR);
			put(c, S, POLISHED);
			if (gate.perp() < 0.5 && Math.floorMod(Math.round(gate.along()), 6) == 0) {
				put(c, Math.min(F + 7, seat - 4), HANGING_SOUL);
			}
		}
	}

	/** A concourse between two tiers: a walk with a rail over the tier below and lanterns, on a shell over the undercroft. */
	private static void concourse(BlockState[] c, double d, double arc, Gate gate, int floor, double railAt) {
		fill(c, floor - 3, floor, BRICKS);
		put(c, floor, Math.floorMod(Math.round(arc), 5) == 0 ? CHISELED : POLISHED);
		boolean pillar = Math.floorMod(Math.round(arc), 12) <= 1 && !(gate != null && gate.perp() <= 5.5);
		if (pillar) {
			fill(c, S, floor, TILES);
		} else {
			put(c, S, POLISHED);
		}
		if (d <= railAt) {
			put(c, floor + 1, BLACK_WALL);
			if (Math.floorMod(Math.round(arc), 14) == 0) {
				put(c, floor + 2, LANTERN);
			}
		}
	}

	// --- The crown wall ---

	/**
	 * The crown wall round the top of the bowl: 54 blocks above the top seats, four storeys of glowing arches
	 * on the outside (open, then purple glass lit from behind), pilasters between them, bands, battlements and
	 * banners, and at each gate a colossal arch thirty blocks high under a raised portcullis.
	 */
	private static void crown(BlockState[] c, int dx, int dz, double d, double arc, Gate gate) {
		int top = F + 118;
		fill(c, Y0, top, BRICKS);
		for (int band : new int[] {F + 30, F + 58, F + 86, top}) {
			put(c, band, POLISHED);
		}
		if (Math.floorMod(Math.round(arc), 4) < 2) {
			put(c, top + 1, BRICKS);
			put(c, top + 2, BRICKS);
		}
		int phase = Math.floorMod(Math.round(arc), 13);
		boolean outer = d > 151.5;
		boolean mid = d > 150.5 && !outer;
		if (phase == 0) {
			if (outer) {
				fill(c, F, top - 1, POLISHED);
			}
			if (d > 152 && gate == null) {
				put(c, top - 6, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, outward(dx, dz)));
			}
		} else if (phase >= 2 && phase <= 10) {
			boolean core = phase >= 4 && phase <= 8;
			for (int[] tier : new int[][] {{F + 2, F + 26}, {F + 34, F + 54}, {F + 62, F + 82}, {F + 90, F + 112}}) {
				for (int y = tier[0]; y <= tier[1]; y++) {
					// A round arch: the corners filled in as the top comes down to the sides.
					int fromTop = tier[1] - y;
					int fromSide = Math.min(phase - 2, 10 - phase);
					boolean shoulder = fromTop < 3 - fromSide;
					boolean pane = core && y > tier[0] + 1 && y < tier[1] - 3;
					if (outer) {
						put(c, y, shoulder ? BRICKS : AIR);
					} else if (mid) {
						put(c, y, pane ? PURPLE_GLASS : BLACKSTONE);
					} else {
						put(c, y, pane ? SEA_LANTERN : BLACKSTONE);
					}
				}
				if (phase == 6 && outer) {
					put(c, tier[1] + 1, CHISELED);
				}
			}
		}
		if (gate != null && gate.perp() <= 5.5) {
			fill(c, F, F + 30, AIR);
			put(c, S, POLISHED);
			if (outer || mid) {
				put(c, F + 30, Blocks.IRON_BARS.defaultBlockState());
				put(c, F + 31, CHISELED);
				put(c, F + 32, gate.perp() < 0.5 ? AMETHYST : POLISHED);
			}
		}
	}

	/** Flying buttresses against the crown wall, a sloping fin every few degrees. */
	private static void buttress(BlockState[] c, double d, double deg, double arc, Gate gate) {
		put(c, S, POLISHED);
		if (gate != null && gate.perp() <= 7.5) {
			return;
		}
		if (offStep(deg + 11.25 / 2, 11.25, 158) < 1.6) {
			double u = d - CROWN;
			int top = (int) Math.round(F + 104 - u * 9.5);
			fill(c, S, top, TILES);
			put(c, top, POLISHED);
			if (u > 8.5) {
				put(c, top + 1, LANTERN);
			}
		}
	}

	// --- The towers ---

	/**
	 * Sixteen great towers on the crown wall between the gates: deepslate tiles banded in amethyst, two
	 * lantern rooms behind purple glass, battlements, and a purpur spire forty blocks high, a lantern and a rod
	 * at its tip.
	 */
	private static void tower(BlockState[] c, int dx, int dz) {
		for (int i = 0; i < 16; i++) {
			double a = Math.toRadians(11.25 + i * 22.5);
			double ox = dx - Math.cos(a) * TOWER_RING;
			double oz = dz - Math.sin(a) * TOWER_RING;
			double t = Math.hypot(ox, oz);
			if (t > TOWER_R + 0.5) {
				continue;
			}
			int body = F + 136;
			boolean rim = t > TOWER_R - 1;
			fill(c, Y0, body, rim ? TILES : BRICKS);
			for (int band : new int[] {F + 40, F + 80, F + 120, body - 1}) {
				put(c, band, AMETHYST);
			}
			for (int room : new int[] {F + 100, F + 126}) {
				if (t <= TOWER_R - 2) {
					fill(c, room - 4, room + 4, AIR);
					if (t < 0.5) {
						put(c, room, SEA_LANTERN);
					}
				} else if (rim && (Math.abs(ox) < 1.6 || Math.abs(oz) < 1.6 || Math.abs(Math.abs(ox) - Math.abs(oz)) < 1.2)) {
					fill(c, room - 4, room + 4, PURPLE_GLASS);
				}
			}
			if (rim && Math.floorMod((int) Math.round(ox) + (int) Math.round(oz), 2) == 0) {
				put(c, body + 1, BRICK_WALL);
			}
			for (int y = body + 1; y <= body + 42; y++) {
				double rr = TOWER_R * (1 - (y - body - 1) / 42.0) - 0.3;
				if (t <= rr) {
					put(c, y, rr - t < 1.4 ? PURPUR : BRICKS);
				}
			}
			if (t < 0.5) {
				put(c, body + 43, SEA_LANTERN);
				put(c, body + 44, END_ROD);
			}
			return;
		}
	}

	/** The canopy over the top tier: purple and white stripes of cloth from the crown wall inward. */
	private static void canopy(BlockState[] c, double d, double deg) {
		if (d < 124 || d > 151) {
			return;
		}
		int y = (int) Math.round(F + 114 + (151 - d) * 0.22);
		put(c, y, Math.floorMod((int) Math.floor((deg + 180) / 4), 2) == 0 ? WOOL_PURPLE : WOOL_WHITE);
	}

	/**
	 * The imperial box over the south gate on the first tier: a balcony jutting out over the rows below it, a
	 * gilded balustrade of purple glass with banners hanging under it, a carpeted dais, three thrones against
	 * a back wall hung with banners, gilded purpur columns and a purpur canopy.
	 */
	private static void imperialBox(BlockState[] c, int dx, int dz, double d, Gate gate) {
		if (gate == null || gate.gz() != 1 || gate.perp() > 14.5 || d < 61 || d > 77) {
			return;
		}
		int floor = F + 30;
		double p = gate.perp();
		boolean front = d < 62.5;
		boolean back = d > 75.5;
		boolean side = p > 13.5;
		// The balcony floor, three thick; the rows of seats below it carry on underneath.
		fill(c, floor - 2, floor, BLACK_BRICKS);
		put(c, floor - 2, front ? CHISELED : BLACK_BRICKS);
		fill(c, floor + 1, floor + 14, AIR);
		put(c, floor, Math.floorMod(Math.round(p), 3) == 0 ? GILDED : POLISHED);
		if (front || side) {
			put(c, floor, GILDED);
			put(c, floor + 1, PURPLE_PANE);
			if (front && Math.floorMod(Math.round(p), 4) == 2) {
				put(c, floor - 3, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
			}
		} else if (back) {
			fill(c, floor + 1, floor + 10, BLACK_BRICKS);
			put(c, floor + 6, GILDED);
		} else {
			put(c, floor + 1, Blocks.CARPET.purple().defaultBlockState());
		}
		if ((front || back) && p > 12.5 || front && p < 0.5) {
			fill(c, floor + 1, floor + 9, Blocks.PURPUR_PILLAR.defaultBlockState());
			put(c, floor + 1, GILDED);
			put(c, floor + 9, GILDED);
		}
		put(c, floor + 10, d < 62 ? Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
			: Blocks.PURPUR_SLAB.defaultBlockState());
		if (front && Math.floorMod(Math.round(p), 4) == 0 && p > 0.5) {
			put(c, floor + 9, HANGING_SOUL);
		}
		if (d > 74.5 && d <= 75.5 && (p < 0.5 || Math.abs(p - 4) < 0.5)) {
			// The thrones: a seat facing the field, its back amethyst crowned with gold.
			put(c, floor + 1, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		} else if (back && (p < 0.5 || Math.abs(p - 4) < 0.5)) {
			fill(c, floor + 1, floor + 3, AMETHYST);
			put(c, floor + 4, GILDED);
			put(c, floor + 5, Blocks.GOLD_BLOCK.defaultBlockState());
		}
		if (back && d < 76.5 && Math.floorMod(Math.round(p), 3) == 2) {
			put(c, floor + 8, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
		}
	}

	// --- Plaza, moat, gardens ---

	/** The plaza round the bowl: radiating paving, and lamp posts on a ring. */
	private static void plaza(BlockState[] c, double d, double deg, Gate gate) {
		put(c, S, Math.floorMod((int) Math.floor((deg + 180) / 3), 2) == 0 ? POLISHED : TILES);
		if (Math.abs(d - 170) < 0.6 && offStep(deg, 6, d) < 0.6 && gate == null) {
			fill(c, F, F + 3, BRICK_WALL);
			put(c, F + 4, LANTERN);
		}
	}

	/** The moat, lit from below, crossed by a bridge at each gate. */
	private static void moat(BlockState[] c, double d, double deg, Gate gate) {
		put(c, S - 5, Blocks.PRISMARINE_BRICKS.defaultBlockState());
		put(c, S - 4, offStep(deg, 3, d) < 0.6 ? SEA_LANTERN : Blocks.PRISMARINE_BRICKS.defaultBlockState());
		fill(c, S - 3, S, WATER);
		if (d < 179.5 || d > 191.5) {
			fill(c, S - 4, S, BRICKS);
			put(c, S, POLISHED);
		}
		if (gate != null && gate.perp() <= 8.5) {
			put(c, S, gate.perp() > 7.5 ? BRICKS : POLISHED);
			if (gate.perp() > 7.5) {
				put(c, F, BRICK_WALL);
				if (Math.floorMod(Math.round(gate.along()), 4) == 0) {
					put(c, F + 1, BRICK_WALL);
					put(c, F + 2, SOUL_LANTERN);
				}
			}
		}
	}

	/** Cherry gardens round the moat, a path out from each gate. */
	private static void garden(BlockState[] c, int dx, int dz, double d, Gate gate) {
		int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 21);
		if (d > GARDEN - 6 + roll % 6) {
			return;
		}
		fill(c, S - 3, S - 1, DIRT);
		put(c, S, GRASS);
		if (gate != null && gate.perp() <= 6.5) {
			put(c, S, gate.perp() <= 4.5 ? POLISHED : Blocks.DIRT_PATH.defaultBlockState());
			return;
		}
		double best = Double.MAX_VALUE;
		double tx = 0;
		double tz = 0;
		for (int i = 0; i < 40; i++) {
			double a = Math.toRadians(4.5 + i * 9);
			for (double ring : new double[] {200, 209}) {
				double cx = Math.cos(a + (ring > 205 ? 0.04 : 0)) * ring;
				double cz = Math.sin(a + (ring > 205 ? 0.04 : 0)) * ring;
				double t = Math.hypot(dx - cx, dz - cz);
				if (t < best) {
					best = t;
					tx = cx;
					tz = cz;
				}
			}
		}
		if (best <= 3.5) {
			cherry(c, dx - tx, dz - tz, F, roll);
		} else if (roll < 140) {
			put(c, F, roll < 50 ? Blocks.PINK_PETALS.defaultBlockState() : roll < 100 ? Blocks.SHORT_GRASS.defaultBlockState()
				: roll < 115 ? Blocks.ALLIUM.defaultBlockState() : roll < 128 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
				: Blocks.PINK_TULIP.defaultBlockState());
		}
	}

	static void cherry(BlockState[] c, double ox, double oz, int y, int roll) {
		double t = Math.hypot(ox, oz);
		if (t < 0.5) {
			fill(c, y, y + 5, CHERRY_LOG);
		}
		for (int dy = 4; dy <= 8; dy++) {
			double r = dy <= 5 ? 3.3 : dy <= 7 ? 2.7 : 1.6;
			if (t <= r && !(t < 0.5 && dy <= 5) && at(c, y + dy).isAir()) {
				put(c, y + dy, CHERRY_LEAVES);
			}
		}
		if (t > 1 && t <= 3.3 && roll % 3 == 0 && at(c, y).isAir() && at(c, y - 1).is(Blocks.GRASS_BLOCK)) {
			put(c, y, Blocks.PINK_PETALS.defaultBlockState());
		}
	}

	/** Where the south causeway ends. */
	static final int LANDING_END = 240;

	/**
	 * The south causeway past the gardens, where the hub pad lands you: a paved road between lantern posts
	 * under a great arch, the pad back to the hub behind you, and the bowl ahead.
	 */
	private static void landing(BlockState[] c, int dx, int dz) {
		int p = Math.abs(dx);
		if (c[S - Y0].is(Blocks.GRASS_BLOCK) && dz < GARDEN && p > 4) {
			return;
		}
		fill(c, S - 3, S - 1, BRICKS);
		fill(c, F, F + 20, AIR);
		put(c, S, p <= 4 ? (Math.floorMod(dz, 4) == 0 ? CHISELED : POLISHED) : TILES);
		if (p >= 9 || dz >= LANDING_END - 1) {
			put(c, S, BRICKS);
			put(c, F, BRICK_WALL);
			if (Math.floorMod(dz, 4) == 0 || dz >= LANDING_END - 1 && p % 4 == 0) {
				put(c, F + 1, BRICK_WALL);
				put(c, F + 2, SOUL_LANTERN);
			}
		}
		// The arch over the arrival: amethyst-banded pillars, a lintel, banners facing the bowl.
		int archZ = RETURN_ARRIVAL + 4;
		if (dz == archZ || dz == archZ + 1) {
			if (p == 7 || p == 8) {
				fill(c, F, F + 12, p == 8 ? TILES : BLACK_BRICKS);
				put(c, F + 4, AMETHYST);
				put(c, F + 9, AMETHYST);
				put(c, F + 13, CHISELED);
				put(c, F + 14, END_ROD);
			} else if (p < 7) {
				fill(c, F + 11, F + 13, p == 0 ? AMETHYST : BLACK_BRICKS);
				put(c, F + 14, p % 2 == 0 ? BLACK_WALL : AIR);
				if (dz == archZ && p % 3 == 0) {
					put(c, F + 10, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
				}
			}
		}
		if (dx == 0 && dz == RETURN_ARRIVAL) {
			put(c, S, AMETHYST);
			put(c, F, PAD);
		}
	}

	// --- Satellite stadiums ---

	/** A satellite stadium's look. */
	record Theme(String name, BlockState floor, BlockState floor2, BlockState accent, BlockState wall, Block seat, Block aisle, BlockState glow,
		BlockState spire, BlockState trim) {
	}

	static final Theme[] THEMES = {
		new Theme("Fire", Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
			Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.SHROOMLIGHT.defaultBlockState(),
			Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState()),
		new Theme("Frost", Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
			Blocks.CALCITE.defaultBlockState(), Blocks.QUARTZ_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.SEA_LANTERN.defaultBlockState(),
			Blocks.PACKED_ICE.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState()),
		new Theme("Grove", Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
			Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS,
			Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
			Blocks.STONE_BRICKS.defaultBlockState()),
		new Theme("Void", Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(),
			Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_STONE_BRICK_STAIRS, Blocks.PURPUR_STAIRS, Blocks.END_ROD.defaultBlockState(),
			Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState())};

	/** Where a column is in a satellite stadium. */
	record Sat(int index, double lx, double lz, double d, double deg) {
	}

	/** The satellite stadium centres: north-east, south-east, south-west, north-west. */
	public static double[] satelliteCentre(int i) {
		int[][] c = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
		return new double[] {c[i][0] * SAT, c[i][1] * SAT};
	}

	static Sat satellite(int dx, int dz) {
		for (int i = 0; i < 4; i++) {
			double[] s = satelliteCentre(i);
			double lx = dx - s[0];
			double lz = dz - s[1];
			double d = Math.hypot(lx, lz);
			if (d <= SAT_R + 4) {
				return new Sat(i, lx, lz, d, Math.toDegrees(Math.atan2(lz, lx)));
			}
		}
		return null;
	}

	/**
	 * A satellite stadium: a 40-block field in its theme's colours with a glowing star at the middle, a
	 * podium wall, twenty rows of seats, a crown wall with glowing windows, six spired towers, a gate facing
	 * the Grand Bowl and a paved apron round it.
	 */
	private static void satellite(BlockState[] c, Sat s) {
		Theme th = THEMES[s.index()];
		double d = s.d();
		double arc = Math.toRadians(s.deg()) * d;
		double[] centre = satelliteCentre(s.index());
		// Its gate points at the bowl's centre.
		double gx = -centre[0];
		double gz = -centre[1];
		double gl = Math.hypot(gx, gz);
		gx /= gl;
		gz /= gl;
		double along = s.lx() * gx + s.lz() * gz;
		double perp = Math.abs(s.lx() * gz - s.lz() * gx);
		boolean gate = along > 0 && perp <= 3.5;
		if (d <= 20.5) {
			fill(c, Y0, S - 1, STONE);
			int rings = (int) Math.floor(d / 2.5);
			boolean starPoint = offStep(s.deg(), 45, d) < 1.2 + (8 - Math.min(8, d)) * 0.4 && d < 9;
			put(c, S, d < 1.5 ? th.glow() : starPoint ? th.accent() : rings % 2 == 0 ? th.floor() : th.floor2());
			if (Math.abs(d - 19) < 0.5 && offStep(s.deg(), 15, d) < 0.6) {
				put(c, S, th.glow());
			}
		} else if (d <= 23.5) {
			fill(c, Y0, F + 6, th.wall());
			put(c, F + 5, th.trim());
			if (offStep(s.deg(), 12, d) < 0.6) {
				put(c, F + 2, th.accent());
			}
			if (Math.floorMod(Math.round(arc), 2) == 0) {
				put(c, F + 7, th.trim());
			}
			if (gate) {
				fill(c, F, F + 4, AIR);
				put(c, S, th.floor());
			}
		} else if (d <= 43.5) {
			int k = (int) Math.floor(d) - 24;
			int seat = F + 8 + k;
			fill(c, seat - 3, seat - 1, th.wall());
			boolean pillar = Math.floorMod(Math.round(d), 8) <= 1 && Math.floorMod(Math.round(arc), 10) <= 1;
			if (pillar && !gate) {
				fill(c, S, seat - 1, th.wall());
			} else {
				put(c, S, th.floor2());
			}
			boolean aisle = Math.floorMod(Math.round(arc), 12) == 0;
			put(c, seat, (aisle ? th.aisle() : th.seat()).defaultBlockState().setValue(StairBlock.FACING, satOutward(s)));
			if (aisle && k % 3 == 1) {
				put(c, seat, th.glow().is(Blocks.END_ROD) ? SEA_LANTERN : th.glow());
			}
			if (gate) {
				fill(c, F, Math.min(F + 5, seat - 4), AIR);
				put(c, S, th.floor());
			}
		} else if (d <= 47.5) {
			int top = F + 40;
			fill(c, Y0, top, th.wall());
			put(c, F + 20, th.trim());
			put(c, top, th.trim());
			if (Math.floorMod(Math.round(arc), 4) < 2) {
				put(c, top + 1, th.wall());
			}
			int phase = Math.floorMod(Math.round(arc), 9);
			if (phase >= 3 && phase <= 5) {
				for (int[] t : new int[][] {{F + 3, F + 14}, {F + 24, F + 35}}) {
					fill(c, t[0], t[1], d > 46.5 ? AIR : d > 45.5 ? PURPLE_GLASS : SEA_LANTERN);
				}
			}
			if (gate) {
				fill(c, F, F + 12, AIR);
				put(c, S, th.floor());
				put(c, F + 13, th.trim());
			}
		} else {
			put(c, S, th.trim());
		}
		// Six towers.
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(30 + i * 60);
			double ox = s.lx() - Math.cos(a) * 45.5;
			double oz = s.lz() - Math.sin(a) * 45.5;
			double t = Math.hypot(ox, oz);
			if (t > 4.6) {
				continue;
			}
			int body = F + 54;
			fill(c, Y0, body, t > 3.6 ? th.trim() : th.wall());
			put(c, F + 44, th.accent());
			if (t <= 2.6) {
				fill(c, F + 46, F + 51, AIR);
				if (t < 0.5) {
					put(c, F + 48, th.glow().is(Blocks.END_ROD) ? SEA_LANTERN : th.glow());
				}
			} else if (t > 3.6 && (Math.abs(ox) < 1.2 || Math.abs(oz) < 1.2)) {
				fill(c, F + 46, F + 51, PURPLE_GLASS);
			}
			for (int y = body + 1; y <= body + 22; y++) {
				double rr = 4.6 * (1 - (y - body - 1) / 22.0) - 0.3;
				if (t <= rr) {
					put(c, y, th.spire());
				}
			}
			if (t < 0.5) {
				put(c, body + 23, SEA_LANTERN);
				put(c, body + 24, END_ROD);
			}
		}
	}

	private static Direction satOutward(Sat s) {
		return outward(s.lx(), s.lz());
	}

	// --- The sky ---

	/** Floating islands with waterfalls, the halo and the sky citadel: where they are. */
	static final double CITADEL_X = 0;
	static final double CITADEL_Z = -262;

	private static boolean skyHere(int dx, int dz) {
		if (Math.hypot(dx - CITADEL_X, dz - CITADEL_Z) <= 33) {
			return true;
		}
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(i * 60);
			if (Math.hypot(dx - Math.cos(a) * 232, dz - Math.sin(a) * 232) <= 13 && satellite(dx, dz) == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Above the bowl: a great halo of purpur set with sea lanterns and end rods, sixteen crystal shards, an
	 * amethyst crystal over the middle. Round it, floating islands pouring waterfalls into the moat and out
	 * over the desert, and the sky citadel to the north.
	 */
	private static void sky(BlockState[] c, int dx, int dz, double d, double deg) {
		int halo = F + 150;
		if (d > 58 && d <= 62) {
			put(c, halo, offStep(deg, 15, d) < 0.8 ? SEA_LANTERN : PURPUR);
			if (d > 59 && d <= 61) {
				put(c, halo - 1, AMETHYST);
			}
			if (d > 59.5 && d <= 60.5 && offStep(deg + 7.5, 15, d) < 0.6) {
				put(c, halo + 1, END_ROD);
			}
		}
		for (int i = 0; i < 16; i++) {
			double a = Math.toRadians(11.25 + i * 22.5);
			double s = Math.hypot(dx - Math.cos(a) * 70, dz - Math.sin(a) * 70);
			int y0 = F + 118 + (i % 2) * 10;
			if (s <= 0.5) {
				fill(c, y0, y0 + 12, AMETHYST);
				put(c, y0 + 13, END_ROD);
			} else if (s <= 1.5) {
				fill(c, y0 + 3, y0 + 9, AMETHYST);
			} else if (s <= 2.3) {
				fill(c, y0 + 5, y0 + 7, AMETHYST);
			}
		}
		int core = F + 100;
		int dist = Math.abs(dx) + Math.abs(dz);
		for (int y = core - 6; y <= core + 6; y++) {
			if (dist + Math.abs(y - core) <= 6) {
				put(c, y, dist + Math.abs(y - core) <= 1 ? SEA_LANTERN : AMETHYST);
			}
		}
		if (dist == 0) {
			put(c, core + 7, END_ROD);
		}
		// Floating islands with waterfalls, out past the gardens.
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(i * 60);
			double ox = dx - Math.cos(a) * 232;
			double oz = dz - Math.sin(a) * 232;
			double t = Math.hypot(ox, oz);
			if (t > 12.5 || satellite(dx, dz) != null) {
				continue;
			}
			int top = F + 60 + (i % 3) * 14;
			int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 41);
			int depth = (int) Math.round((12.5 - t) * 1.4) + roll % 4 + 1;
			fill(c, top - depth, top - 3, STONE);
			fill(c, top - 2, top - 1, DIRT);
			put(c, top, GRASS);
			if (t < 0.5) {
				fill(c, top - depth, top - 1, AIR);
				put(c, top, WATER);
			} else if (Math.abs(ox - 4) < 3.4 && Math.abs(oz + 3) < 3.4 && Math.hypot(ox - 4, oz + 3) <= 3.4) {
				cherry(c, ox - 4, oz + 3, top + 1, roll);
			} else if (t > 10 && roll < 300) {
				put(c, top + 1, Blocks.AMETHYST_CLUSTER.defaultBlockState());
			} else if (t > 11 && roll < 420) {
				fill(c, top - depth - 9, top - depth - 1, CHAIN);
			}
		}
		citadel(c, dx, dz);
	}

	/**
	 * The sky citadel north of the bowl: a castle on a floating island thirty blocks across, a curtain wall
	 * with a gateway, four corner towers and a great keep, lit purple windows and purpur spires, cherry trees,
	 * chains hanging below.
	 */
	private static void citadel(BlockState[] c, int dx, int dz) {
		double lx = dx - CITADEL_X;
		double lz = dz - CITADEL_Z;
		double t = Math.hypot(lx, lz);
		if (t > 32.5) {
			return;
		}
		int top = F + 120;
		int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 63);
		int depth = (int) Math.round((32.5 - t) * 1.3) + roll % 5 + 2;
		fill(c, top - depth, top - 3, t > 24 ? STONE : Blocks.DEEPSLATE.defaultBlockState());
		fill(c, top - 2, top - 1, DIRT);
		double square = Math.max(Math.abs(lx), Math.abs(lz));
		put(c, top, square < 16 ? POLISHED : GRASS);
		if (t > 30 && roll < 110) {
			fill(c, top - depth - 14, top - depth - 1, CHAIN);
		}
		if (Math.round(square) == 16 && square <= 16.5) {
			boolean gateway = lz > 15.5 && Math.abs(lx) <= 2.5;
			fill(c, top + 1, top + 12, TILES);
			if (gateway) {
				fill(c, top + 1, top + 7, AIR);
				put(c, top + 8, CHISELED);
			}
			if (((int) Math.round(lx) + (int) Math.round(lz)) % 2 == 0) {
				put(c, top + 13, BRICKS);
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				double ox = lx - sx * 16;
				double oz = lz - sz * 16;
				double r = Math.hypot(ox, oz);
				if (r <= 4.6) {
					spiredTower(c, ox, oz, r, 4.6, top + 1, top + 26, 16);
				}
			}
		}
		if (t <= 8.6) {
			spiredTower(c, lx, lz, t, 8.6, top + 1, top + 44, 30);
		}
		int[][] trees = {{24, 6}, {-24, -6}, {6, -24}, {-8, 24}, {20, -18}, {-20, 18}};
		for (int[] tr : trees) {
			double ox = lx - tr[0];
			double oz = lz - tr[1];
			if (Math.hypot(ox, oz) <= 3.4) {
				cherry(c, ox, oz, top + 1, roll);
			}
		}
	}

	private static void spiredTower(BlockState[] c, double ox, double oz, double t, double radius, int base, int bodyTop, int spire) {
		boolean rim = t > radius - 1;
		fill(c, base, bodyTop, rim ? TILES : BRICKS);
		int room = base + (bodyTop - base) * 2 / 3;
		put(c, base + (bodyTop - base) / 3, AMETHYST);
		put(c, bodyTop - 1, AMETHYST);
		if (t <= radius - 2) {
			fill(c, room - 3, room + 3, AIR);
			if (t < 0.5) {
				put(c, room, SEA_LANTERN);
			}
		} else if (rim && (Math.abs(ox) < 1.4 || Math.abs(oz) < 1.4)) {
			fill(c, room - 3, room + 3, PURPLE_GLASS);
		}
		if (rim && ((int) Math.round(ox) + (int) Math.round(oz)) % 2 == 0) {
			put(c, bodyTop + 1, BRICK_WALL);
		}
		for (int y = bodyTop + 1; y <= bodyTop + spire; y++) {
			double rr = radius * (1 - (y - bodyTop - 1) / (double) spire) - 0.3;
			if (t <= rr) {
				put(c, y, rr - t < 1.3 ? PURPUR : BRICKS);
			}
		}
		if (t < 0.5) {
			put(c, bodyTop + spire + 1, SEA_LANTERN);
			put(c, bodyTop + spire + 2, END_ROD);
		}
	}

	// --- Placement ---

	/** Whether a block hangs on a neighbour, so it is set after everything solid. */
	public static boolean attached(BlockState state) {
		Block b = state.getBlock();
		return b instanceof WallBannerBlock || b instanceof LanternBlock || b == Blocks.PINK_PETALS || b == Blocks.END_ROD
			|| b == Blocks.AMETHYST_CLUSTER || b == Blocks.IRON_CHAIN || b == Blocks.SHORT_GRASS || b == Blocks.ALLIUM
			|| b == Blocks.LILY_OF_THE_VALLEY || b == Blocks.PINK_TULIP || b == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE || b == Blocks.SOUL_FIRE;
	}

	/** Where the hub pad lands you (south of the centre): on the south causeway, looking up at the great gate. */
	public static final int ARRIVAL = PracticeLayout.GRAND_ARRIVAL;
	/** Pads back to the hub: behind the arrival, and in the south tunnel by the field. */
	public static final int RETURN_ARRIVAL = PracticeLayout.GRAND_RETURN_BRIDGE;
	public static final int RETURN_TUNNEL = PracticeLayout.GRAND_RETURN_TUNNEL;
}

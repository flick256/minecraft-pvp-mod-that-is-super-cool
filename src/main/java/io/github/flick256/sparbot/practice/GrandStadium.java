package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Arcane Colosseum: one grand stadium for free-for-all fights, medieval stone with an arcane glow.
 * From the middle out: a field paved in flagstone courses round a heraldic compass rose, a knotwork band and a
 * glowing border, a blackstone podium wall with crying-obsidian runes and purple banners, two tiers of seats with
 * purpur aisles split by a lantern-lit promenade, royal boxes over the four gates, a three-storey arcaded
 * outer wall lit by soul lanterns from inside, twelve towers with purple spires and glowing lantern rooms,
 * a moat crossed by four bridges under raised portcullises, a cherry-blossom garden, and above it all a
 * floating halo, crystal shards and floating cherry islands hung on chains; two islands pouring waterfalls
 * into the moat; great rune portals hovering over the north and south gates; drifting rock fragments; and,
 * beyond the north gate, a citadel on its own floating island.
 *
 * <p>The whole stadium is a blueprint: {@link #column} gives every block of one column, so building it and
 * resetting it are the same pass (only blocks that differ are set), spread over ticks for a reset.
 */
final class GrandStadium {
	static final int CX = PracticeLayout.GRAND_X;
	static final int CZ = PracticeLayout.GRAND_Z;
	/** Half the side of the square the stadium (islands included) fits in. */
	static final int REACH = PracticeLayout.GRAND_REACH;
	private static final int S = PracticeLayout.SURFACE;
	private static final int F = PracticeLayout.FLOOR;
	/** The column runs from here... */
	static final int Y0 = S - 10;
	/** ...to here. */
	static final int Y1 = F + 96;
	private static final int H = Y1 - Y0 + 1;

	private static final double FIELD = 30.5;
	private static final double PODIUM = 33.5;
	private static final double LOWER = 43.5;
	private static final double PROMENADE = 46.5;
	private static final double UPPER = 55.5;
	private static final double OUTER = 58.5;
	private static final double WALK = 61.5;
	private static final double MOAT = 68.5;
	/** Twelve towers on this circle, between the gates. */
	private static final double TOWER_RING = 57.0;
	private static final double TOWER_R = 4.5;

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
	private static final BlockState SHADOW = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState PURPUR = Blocks.PURPUR_BLOCK.defaultBlockState();
	private static final BlockState PURPUR_PILLAR = Blocks.PURPUR_PILLAR.defaultBlockState();
	private static final BlockState PRISMARINE = Blocks.PRISMARINE_BRICKS.defaultBlockState();
	private static final BlockState SEA_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState END_ROD = Blocks.END_ROD.defaultBlockState();
	private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
	private static final BlockState SOUL_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState();
	private static final BlockState HANGING_SOUL = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	private static final BlockState HANGING_LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	private static final BlockState PURPLE_GLASS = Blocks.STAINED_GLASS.purple().defaultBlockState();
	private static final BlockState PURPLE_CARPET = Blocks.CARPET.purple().defaultBlockState();
	private static final BlockState CHERRY_LOG = Blocks.CHERRY_LOG.defaultBlockState();
	private static final BlockState CHERRY_LEAVES = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState();

	private GrandStadium() {
	}

	/** Columns in build order: rows of the square round the centre. */
	static int columns() {
		return (2 * REACH + 1) * (2 * REACH + 1);
	}

	static int dx(int column) {
		return column % (2 * REACH + 1) - REACH;
	}

	static int dz(int column) {
		return column / (2 * REACH + 1) - REACH;
	}

	/**
	 * Every block of the column at dx, dz from the centre, from {@link #Y0} up to {@link #Y1}. Blocks that
	 * hang on a neighbour (banners, lanterns, petals, rods, clusters, chains) are placed in a second pass.
	 */
	static BlockState[] column(int dx, int dz) {
		if (!builtAt(dx, dz)) {
			return null;
		}
		BlockState[] c = new BlockState[H];
		Arrays.fill(c, AIR);
		double d = Math.sqrt(dx * dx + dz * dz);
		double ang = Math.atan2(dz, dx);
		double deg = Math.toDegrees(ang);
		double arc = ang * d;
		Gate gate = gate(dx, dz);
		ground(c);
		if (d <= FIELD) {
			field(c, dx, dz, d, deg);
		} else if (d <= PODIUM) {
			podium(c, dx, dz, d, deg, arc, gate);
		} else if (d <= LOWER) {
			int k = (int) Math.floor(d) - 34;
			stands(c, dx, dz, arc, F + 7 + k, gate, false, k);
		} else if (d <= PROMENADE) {
			promenade(c, d, arc, gate);
		} else if (d <= UPPER) {
			int k = (int) Math.floor(d) - 47;
			stands(c, dx, dz, arc, F + 18 + k, gate, true, k);
		} else if (d <= OUTER) {
			outerWall(c, dx, dz, d, arc, gate);
		} else if (d <= WALK) {
			walkway(c, d, deg, gate);
		} else if (d <= MOAT) {
			moat(c, d, deg, gate);
		} else {
			garden(c, dx, dz, d);
		}
		tower(c, dx, dz);
		gateFacade(c, dx, dz, d, gate);
		sky(c, dx, dz, d, deg);
		islands(c, dx, dz);
		waterfallIslands(c, dx, dz);
		portals(c, dx, dz);
		fragments(c, dx, dz);
		citadel(c, dx, dz);
		return c;
	}

	/** Whether anything is built in this column (beyond the garden there is only what floats). */
	static boolean builtAt(int dx, int dz) {
		if (Math.sqrt(dx * dx + dz * dz) <= 81) {
			return true;
		}
		if (Math.hypot(dx - CITADEL_X, dz - CITADEL_Z) <= 17.5) {
			return true;
		}
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(45 + i * 90);
			if (Math.hypot(dx - Math.cos(a) * 80, dz - Math.sin(a) * 80) <= 7) {
				return true;
			}
		}
		for (Fragment f : FRAGMENTS) {
			if (Math.hypot(dx - f.x(), dz - f.z()) <= f.r() + 0.5) {
				return true;
			}
		}
		return false;
	}

	private static void put(BlockState[] c, int y, BlockState state) {
		if (y >= Y0 && y <= Y1) {
			c[y - Y0] = state;
		}
	}

	private static void fill(BlockState[] c, int y0, int y1, BlockState state) {
		for (int y = y0; y <= y1; y++) {
			put(c, y, state);
		}
	}

	private static BlockState at(BlockState[] c, int y) {
		return y >= Y0 && y <= Y1 ? c[y - Y0] : AIR;
	}

	/** The flat desert's own layers, as the world generated them. */
	private static void ground(BlockState[] c) {
		fill(c, Y0, S - 5, SANDSTONE);
		fill(c, S - 4, S, SAND);
	}

	/** Which gate a column lies in (each a corridor 7 wide along a compass direction), if any. */
	private record Gate(int gx, int gz, double along, double perp) {
	}

	private static Gate gate(int dx, int dz) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] g : dirs) {
			double along = dx * g[0] + dz * g[1];
			double perp = Math.abs(dx * g[1] - dz * g[0]);
			if (along > 0 && perp <= 4.5) {
				return new Gate(g[0], g[1], along, perp);
			}
		}
		return null;
	}

	/** Outward, from the centre, rounded to a compass direction. */
	private static Direction outward(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Distance in blocks along the circle of radius d from the nearest multiple of {@code step} degrees. */
	private static double offStep(double deg, double step, double d) {
		double off = Math.abs(deg - step * Math.round(deg / step));
		return Math.toRadians(off) * d;
	}

	private static final BlockState TUFF = Blocks.POLISHED_TUFF.defaultBlockState();
	private static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();
	private static final BlockState TUFF_CHISELED = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
	private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

	/**
	 * The field: stone and earth to dig into, paved like an old arena. Concentric courses of flagstones in two
	 * shades of deepslate; a heraldic compass rose in the middle (four long points in calcite and tuff over four
	 * diagonal ones in smooth stone and andesite, each split into a light and a shaded half and edged in
	 * blackstone, round a gilded boss ringed in amethyst); a braided knotwork
	 * band; and a tuff border with a gilded edge and twelve glowing stones.
	 */
	private static void field(BlockState[] c, int dx, int dz, double d, double deg) {
		fill(c, Y0, S - 4, STONE);
		fill(c, S - 3, S - 1, DIRT);
		double ang = Math.atan2(dz, dx);
		int ring = (int) Math.floor(d / 3);
		int stone = (int) Math.floor((ang + Math.PI) * Math.max(d, 1) / 4.0);
		BlockState floor = (ring + stone) % 2 == 0 ? TILES : POLISHED;
		int rose = compassRose(d, deg);
		if (d > 29.5) {
			floor = BLACK_BRICKS;
		} else if (Math.abs(d - 29) < 0.5) {
			floor = BLACKSTONE;
		} else if (d > 26.5) {
			floor = offStep(deg, 30, d) < 0.6 && Math.abs(d - 27.75) < 0.75 ? SEA_LANTERN : offStep(deg, 15, d) < 0.6 ? TUFF_CHISELED : TUFF_BRICKS;
		} else if (Math.abs(d - 26) < 0.5) {
			floor = GILDED;
		} else if (d > 16.5 && d < 19.5) {
			// Knotwork: two cords braided round the band.
			double t = ang * 18 / 2.2;
			double w1 = 18 + 0.9 * Math.sin(t);
			double w2 = 18 - 0.9 * Math.sin(t);
			floor = Math.abs(d - w1) < 0.45 || Math.abs(d - w2) < 0.45 ? BLACKSTONE : d < 16.9 || d > 19.1 ? TUFF_BRICKS : TUFF;
		} else if (d < 1.5) {
			floor = GILDED;
		} else if (d < 3.5) {
			floor = TUFF_CHISELED;
		} else if (Math.abs(d - 4) < 0.6) {
			floor = AMETHYST;
		} else if (Math.abs(d - 5) < 0.5) {
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
		// Banners on the podium wall's inner face.
		if (d > 29.5 && offStep(deg, 15, d) < 0.6 && gate(dx, dz) == null) {
			Direction in = outward(-dx, -dz);
			put(c, F + 4, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, in));
		}
	}

	/**
	 * Where (d, deg) falls on the compass rose: 0 outside it; for the four main points (north, east, south,
	 * west, 16 blocks long) 1 the light half and 2 the dark half; for the four diagonal points beneath them
	 * (11 long) 4 and 5; 3 the dark edge round every point.
	 */
	private static int compassRose(double d, double deg) {
		int main = star(d, deg, 0, 16, 7.5);
		if (main != 0) {
			return main;
		}
		int diagonal = star(d, deg, 45, 11.5, 5.5);
		return diagonal == 1 ? 4 : diagonal == 2 ? 5 : diagonal;
	}

	/**
	 * A four-point star with points at {@code first} + k x 90 degrees: tips {@code tip} out, the sides meeting
	 * between the points at radius {@code valley}. 0 outside, 3 on its edge, 1 or 2 either half of a point.
	 */
	private static int star(double d, double deg, double first, double tip, double valley) {
		double p = first + 90 * Math.round((deg - first) / 90);
		double off = deg - p;
		double along = d * Math.cos(Math.toRadians(off));
		double across = Math.abs(d * Math.sin(Math.toRadians(off)));
		double corner = valley * Math.cos(Math.toRadians(45));
		if (along < 0 || along > tip) {
			return 0;
		}
		// The point's side runs straight from the tip to the valley corner (corner, corner).
		double halfWidth = along <= corner ? along : corner * (tip - along) / (tip - corner);
		if (across > halfWidth + 0.01) {
			return 0;
		}
		if (halfWidth - across < 0.75 || tip - along < 0.75) {
			return 3;
		}
		return off > 0 ? 1 : 2;
	}


	/** The podium wall round the field: blackstone with glowing rune stones, a gilded band and a crest. */
	private static void podium(BlockState[] c, int dx, int dz, double d, double deg, double arc, Gate gate) {
		fill(c, Y0, F + 6, BLACK_BRICKS);
		put(c, F + 5, GILDED);
		if (offStep(deg, 10, d) < 0.6) {
			put(c, F + 2, CRYING);
		}
		if (Math.floorMod(Math.round(arc), 2) == 0) {
			put(c, F + 7, BLACK_WALL);
		}
		if (offStep(deg + 7.5, 15, d) < 0.6) {
			put(c, F + 7, BLACK_BRICKS);
			put(c, F + 8, SOUL_LANTERN);
		}
		if (offStep(deg, 30, d) < 0.6 && d > 31.5 && d <= 32.5) {
			// Soul-fire braziers on the crest.
			put(c, F + 7, CHISELED);
			put(c, F + 8, Blocks.SOUL_CAMPFIRE.defaultBlockState());
		}
		if (gate != null && gate.perp() <= 3.5) {
			// The field-side mouth of the gate: open to the floor, under a chiselled lintel.
			fill(c, F, F + 4, AIR);
			put(c, S, POLISHED);
			put(c, F + 5, CHISELED);
			if (gate.perp() > 2.5) {
				put(c, F + 4, BLACK_BRICKS);
			}
		}
	}

	/** One row of seats: deepslate stairs facing the field, purpur in the aisles; the gates' tunnels and royal boxes. */
	private static void stands(BlockState[] c, int dx, int dz, double arc, int seatY, Gate gate, boolean upper, int k) {
		fill(c, Y0, seatY - 1, BRICKS);
		boolean aisle = Math.floorMod(Math.round(arc), 14) == 0;
		Block stair = aisle ? Blocks.PURPUR_STAIRS : Blocks.DEEPSLATE_BRICK_STAIRS;
		put(c, seatY, stair.defaultBlockState().setValue(StairBlock.FACING, outward(dx, dz)));
		if (aisle && k % 3 == 1) {
			// A glowing step every third row of an aisle.
			put(c, seatY, SEA_LANTERN);
		}
		if (gate == null) {
			return;
		}
		if (gate.perp() <= 3.5) {
			tunnel(c, gate);
		}
		if (upper) {
			// The royal box over the gate: a carpeted floor, a rail, purpur pillars and a canopy.
			int floor = F + 20;
			fill(c, F + 6, floor, BRICKS);
			fill(c, floor + 1, Y1 - 20, AIR);
			put(c, floor, POLISHED);
			boolean side = gate.perp() > 3.5;
			boolean front = k == 0;
			if (side || front) {
				put(c, floor + 1, Blocks.STAINED_GLASS_PANE.purple().defaultBlockState());
			} else {
				put(c, floor + 1, PURPLE_CARPET);
			}
			if (side && (front || k == 8)) {
				fill(c, floor + 1, floor + 4, PURPUR_PILLAR);
			}
			put(c, floor + 5, Blocks.PURPUR_SLAB.defaultBlockState());
			if (!side && gate.perp() < 0.5 && k == 4) {
				put(c, floor + 4, HANGING_LANTERN);
			}
		}
	}

	/** A pad back to the hub: an amethyst block under a pressure plate. */
	private static void pad(BlockState[] c) {
		put(c, S, AMETHYST);
		put(c, F, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
	}

	/** A gate's tunnel through the stands: six high, lit by hanging soul lanterns. */
	private static void tunnel(BlockState[] c, Gate gate) {
		put(c, S, POLISHED);
		fill(c, F, F + 5, AIR);
		if (gate.gz() == 1 && gate.perp() < 0.5 && Math.round(gate.along()) == PracticeLayout.GRAND_RETURN_TUNNEL) {
			pad(c);
		}
		if (gate.perp() > 2.5) {
			put(c, F + 5, BRICKS);
		}
		if (gate.perp() < 0.5 && Math.floorMod(Math.round(gate.along()), 5) == 0) {
			put(c, F + 5, HANGING_SOUL);
		}
	}

	/** The promenade between the tiers: a lantern-lit walk with a rail over the lower seats. */
	private static void promenade(BlockState[] c, double d, double arc, Gate gate) {
		int floor = F + 16;
		fill(c, Y0, floor, BRICKS);
		put(c, floor, Math.floorMod(Math.round(arc), 4) == 0 ? CHISELED : POLISHED);
		if (d <= 44.5) {
			put(c, floor + 1, BLACK_WALL);
			if (Math.floorMod(Math.round(arc), 12) == 0) {
				put(c, floor + 2, LANTERN);
			}
		}
		if (gate != null && gate.perp() <= 3.5) {
			tunnel(c, gate);
		}
	}

	/**
	 * The outer wall: deepslate, banded, with three storeys of arches on the outside (dark inside, a soul
	 * lantern glowing at the back of each), pilasters between them, battlements and banners.
	 */
	private static void outerWall(BlockState[] c, int dx, int dz, double d, double arc, Gate gate) {
		int top = F + 30;
		fill(c, Y0, top, BRICKS);
		put(c, F + 9, POLISHED);
		put(c, F + 19, POLISHED);
		put(c, top, POLISHED);
		if (Math.floorMod(Math.round(arc), 4) < 2) {
			put(c, top + 1, BRICKS);
			if (d > 57.5 && Math.floorMod(Math.round(arc), 36) == 0) {
				put(c, top + 2, LANTERN);
			}
		}
		int phase = Math.floorMod(Math.round(arc), 9);
		if (phase == 0) {
			if (d > 57.5) {
				fill(c, F, top - 1, POLISHED);
			}
		} else if (phase >= 2 && phase <= 6) {
			// Each arch is a glowing window: open one block deep, then purple glass lit from behind.
			boolean core = phase >= 3 && phase <= 5;
			for (int[] tier : new int[][] {{F, F + 5}, {F + 11, F + 16}, {F + 21, F + 26}}) {
				int y0 = tier[0];
				int y1 = tier[1];
				for (int y = y0; y <= y1; y++) {
					boolean shoulder = (phase == 2 || phase == 6) && y == y1;
					boolean pane = core && y > y0 && y < y1;
					if (d > 57.5) {
						put(c, y, shoulder ? BRICKS : AIR);
					} else if (d > 56.5) {
						put(c, y, pane ? PURPLE_GLASS : SHADOW);
					} else {
						put(c, y, pane ? SEA_LANTERN : SHADOW);
					}
				}
				if (phase == 4 && d > 57.5) {
					put(c, y1 + 1, CHISELED);
				}
			}
		}
		if (gate != null && gate.perp() <= 3.5) {
			tunnel(c, gate);
			if (d > 57.5) {
				// The gate arch: taller than the tunnel, a raised portcullis showing its teeth.
				fill(c, F, F + 8, AIR);
				put(c, F + 8, Blocks.IRON_BARS.defaultBlockState());
				put(c, F + 9, CHISELED);
			}
		}
		if (d > 57.5 && phase == 0 && gate == null) {
			put(c, F + 27, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, outward(dx, dz)));
		}
	}

	/** The walk round the outside, with lamp posts. */
	private static void walkway(BlockState[] c, double d, double deg, Gate gate) {
		put(c, S, POLISHED);
		if (d > 60 && offStep(deg, 20, d) < 0.6 && gate == null) {
			fill(c, F, F + 2, BRICK_WALL);
			put(c, F + 3, LANTERN);
		}
	}

	/** The moat, glowing from below, crossed by a bridge at each gate. */
	private static void moat(BlockState[] c, double d, double deg, Gate gate) {
		put(c, S - 4, PRISMARINE);
		put(c, S - 3, offStep(deg, 12, d) < 0.6 ? SEA_LANTERN : PRISMARINE);
		fill(c, S - 2, S, WATER);
		if (d < 62.5 || d > 67.5) {
			fill(c, S - 3, S, BRICKS);
			put(c, S, POLISHED);
		}
		if (gate != null) {
			put(c, S, gate.perp() > 3.5 ? BRICKS : POLISHED);
			if (gate.gz() == 1 && gate.perp() < 0.5 && Math.round(gate.along()) == PracticeLayout.GRAND_RETURN_BRIDGE) {
				pad(c);
			}
			if (gate.perp() > 3.5) {
				put(c, F, BRICK_WALL);
				if (Math.floorMod(Math.round(gate.along()), 3) == 0) {
					put(c, F + 1, BRICK_WALL);
					put(c, F + 2, SOUL_LANTERN);
				}
			}
		}
	}

	/** The cherry-blossom garden round the moat, fading into the desert. */
	private static void garden(BlockState[] c, int dx, int dz, double d) {
		int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 21);
		if (d > 76 + roll % 5) {
			return;
		}
		fill(c, S - 3, S - 1, DIRT);
		put(c, S, GRASS);
		Gate gate = gate(dx, dz);
		if (gate != null && gate.perp() <= 2.5) {
			put(c, S, Blocks.DIRT_PATH.defaultBlockState());
			return;
		}
		// Cherry trees on a ring between the gates.
		double best = Double.MAX_VALUE;
		double tx = 0;
		double tz = 0;
		for (int i = 0; i < 16; i++) {
			double a = Math.toRadians(11.25 + i * 22.5);
			double cx = Math.cos(a) * 72;
			double cz = Math.sin(a) * 72;
			double t = Math.hypot(dx - cx, dz - cz);
			if (t < best) {
				best = t;
				tx = cx;
				tz = cz;
			}
		}
		if (best <= 3.5) {
			cherry(c, dx - tx, dz - tz, F, roll);
		} else if (roll < 120) {
			put(c, F, roll < 40 ? Blocks.PINK_PETALS.defaultBlockState() : roll < 90 ? Blocks.SHORT_GRASS.defaultBlockState()
				: roll < 100 ? Blocks.ALLIUM.defaultBlockState() : roll < 110 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
				: Blocks.PINK_TULIP.defaultBlockState());
		}
	}

	/** A cherry tree around (0, 0) standing at {@code y}: trunk and a round pink crown. */
	private static void cherry(BlockState[] c, double ox, double oz, int y, int roll) {
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

	/**
	 * Twelve towers between the gates on the outer wall: deepslate tiles banded in amethyst, a glowing
	 * lantern room behind purple glass, battlements, and a purpur spire with a lantern and a rod at the tip.
	 */
	private static void tower(BlockState[] c, int dx, int dz) {
		for (int i = 0; i < 12; i++) {
			double a = Math.toRadians(15 + i * 30);
			double ox = dx - Math.cos(a) * TOWER_RING;
			double oz = dz - Math.sin(a) * TOWER_RING;
			double t = Math.hypot(ox, oz);
			if (t > TOWER_R + 0.5) {
				continue;
			}
			int body = F + 44;
			fill(c, Y0, body, t > TOWER_R - 1 ? TILES : BRICKS);
			put(c, F + 31, AMETHYST);
			put(c, F + 43, AMETHYST);
			put(c, F + 14, POLISHED);
			put(c, F + 24, POLISHED);
			// The lantern room: hollow, a sea lantern in the middle, purple glass windows.
			if (t <= 2.5) {
				fill(c, F + 33, F + 39, AIR);
				if (t < 0.5) {
					put(c, F + 36, SEA_LANTERN);
				}
			} else if (t > TOWER_R - 1 && (Math.abs(ox) < 1.2 || Math.abs(oz) < 1.2)) {
				fill(c, F + 34, F + 38, PURPLE_GLASS);
			}
			if (t > TOWER_R - 1 && Math.floorMod((int) Math.round(ox) + (int) Math.round(oz), 2) == 0) {
				put(c, body + 1, BRICK_WALL);
			}
			// The spire.
			for (int y = body + 1; y <= body + 18; y++) {
				double rr = TOWER_R * (1 - (y - body - 1) / 18.0) - 0.5;
				if (t <= rr) {
					put(c, y, rr - t < 1.2 ? PURPUR : BRICKS);
				}
			}
			if (t < 0.5) {
				put(c, body + 19, SEA_LANTERN);
				put(c, body + 20, END_ROD);
			}
			return;
		}
	}

	/** Out in front of each gate: two great pillars with soul fires and banners, an arch between them. */
	private static void gateFacade(BlockState[] c, int dx, int dz, double d, Gate gate) {
		if (gate == null || d < 58.5 || d > 61.5) {
			return;
		}
		double p = gate.perp();
		if (p > 3.5 && p <= 4.5) {
			fill(c, S, F + 14, CHISELED);
			fill(c, F, F + 13, TILES);
			put(c, F + 14, Blocks.SOUL_CAMPFIRE.defaultBlockState());
			put(c, F + 10, AMETHYST);
		} else if (p <= 3.5 && d > 59.5 && d <= 60.5) {
			// The arch between the pillars: a lintel with an amethyst keystone, shouldered by upside-down stairs.
			put(c, F + 12, TILES);
			put(c, F + 13, p < 0.5 ? AMETHYST : TILES);
			if (p > 2.5) {
				double side = dx * -gate.gz() + dz * gate.gx();
				Direction toPillar = side > 0 ? outward(-gate.gz(), gate.gx()) : outward(gate.gz(), -gate.gx());
				put(c, F + 11, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toPillar).setValue(StairBlock.HALF, Half.TOP));
			}
		}
	}

	/**
	 * Above the field: a floating halo of purpur set with sea lanterns and end rods, eight crystal shards
	 * round it, and an amethyst crystal over the middle, all far above any fight.
	 */
	private static void sky(BlockState[] c, int dx, int dz, double d, double deg) {
		int halo = F + 52;
		if (d > 23.5 && d <= 25.5) {
			put(c, halo, offStep(deg, 30, d) < 0.7 ? SEA_LANTERN : PURPUR);
			if (d > 24 && d <= 25 && offStep(deg + 15, 30, d) < 0.6) {
				put(c, halo + 1, END_ROD);
			}
		}
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(22.5 + i * 45);
			double s = Math.hypot(dx - Math.cos(a) * 27, dz - Math.sin(a) * 27);
			if (s <= 0.5) {
				fill(c, F + 40, F + 46, AMETHYST);
				put(c, F + 47, END_ROD);
			} else if (s <= 1.5) {
				fill(c, F + 42, F + 44, AMETHYST);
			}
		}
		int core = F + 36;
		int dist = Math.abs(dx) + Math.abs(dz);
		for (int y = core - 3; y <= core + 3; y++) {
			if (dist + Math.abs(y - core) <= 3) {
				put(c, y, dist == 0 && y == core ? SEA_LANTERN : AMETHYST);
			}
		}
		if (dist == 0) {
			put(c, core + 4, END_ROD);
		}
	}

	/** Four floating cherry islands outside the walls, hung from the sky on chains. */
	private static void islands(BlockState[] c, int dx, int dz) {
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(45 + i * 90);
			double ox = dx - Math.cos(a) * 80;
			double oz = dz - Math.sin(a) * 80;
			double t = Math.hypot(ox, oz);
			if (t > 6.5) {
				continue;
			}
			int top = F + 30;
			int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 33);
			int depth = (int) Math.round((6.5 - t) * 1.6) + roll % 3;
			fill(c, top - depth, top - 3, STONE);
			fill(c, top - 2, top - 1, DIRT);
			put(c, top, GRASS);
			if (t < 0.5) {
				cherry(c, 0, 0, top + 1, roll);
			} else if (t <= 3.6) {
				cherry(c, ox, oz, top + 1, roll);
			} else if (t > 5 && roll < 220) {
				put(c, top + 1, Blocks.AMETHYST_CLUSTER.defaultBlockState());
			}
			if (t > 4.5 && t <= 5.5 && roll < 120) {
				// Chains hanging from the underside.
				fill(c, top - depth - 6, top - depth - 1, CHAIN);
			}
			return;
		}
	}

	/** Two islands floating over the moat, east-north-east and west-south-west, each pouring a waterfall into it. */
	private static void waterfallIslands(BlockState[] c, int dx, int dz) {
		for (double angle : new double[] {30, 210}) {
			double a = Math.toRadians(angle);
			double ox = dx - Math.cos(a) * 65;
			double oz = dz - Math.sin(a) * 65;
			double t = Math.hypot(ox, oz);
			if (t > 5.5) {
				continue;
			}
			int top = F + 36;
			int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 41);
			int depth = (int) Math.round((5.5 - t) * 1.5) + roll % 3 + 1;
			fill(c, top - depth, top - 2, STONE);
			put(c, top - 1, DIRT);
			put(c, top, Blocks.MOSS_BLOCK.defaultBlockState());
			if (t < 0.5) {
				// The spring: a source over a shaft through the island, falling all the way to the moat.
				fill(c, top - depth, top - 1, AIR);
				put(c, top, WATER);
			} else if (t > 3.5 && t <= 4.5 && roll < 400) {
				put(c, top + 1, roll < 150 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : Blocks.AZURE_BLUET.defaultBlockState());
			} else if (t > 4.5 && roll < 140) {
				fill(c, top - depth - 5, top - depth - 1, CHAIN);
			}
			return;
		}
	}

	/**
	 * Rune portals: two great upright rings hovering over the north and south gates, purpur outside, amethyst
	 * inside, set with sea lanterns and with end rods round the rim.
	 */
	private static void portals(BlockState[] c, int dx, int dz) {
		if (Math.abs(dz) != 63 || Math.abs(dx) > 14) {
			return;
		}
		int yc = F + 44;
		double outer = 12.5;
		for (int y = yc - 14; y <= yc + 14; y++) {
			double r = Math.hypot(dx, y - yc);
			double a = Math.toDegrees(Math.atan2(y - yc, dx));
			if (r > outer - 1 && r <= outer) {
				put(c, y, offStep(a, 30, r) < 0.7 ? SEA_LANTERN : PURPUR);
			} else if (r > outer - 2 && r <= outer - 1) {
				put(c, y, AMETHYST);
			} else if (r > outer && r <= outer + 1 && offStep(a + 15, 30, r) < 0.5 && y > yc) {
				put(c, y, END_ROD);
			}
		}
	}

	/** A drifting rock fragment: where it floats and how big it is. */
	private record Fragment(double x, double z, int y, double r) {
	}

	private static final java.util.List<Fragment> FRAGMENTS = fragments();

	private static java.util.List<Fragment> fragments() {
		java.util.List<Fragment> list = new java.util.ArrayList<>();
		for (int i = 0; i < 30; i++) {
			int roll = PracticeLayout.scatter(i, 3, 77);
			double a = Math.toRadians(i * 12 + roll % 9);
			double d = 64 + roll % 38;
			double x = Math.cos(a) * d;
			double z = Math.sin(a) * d;
			if (Math.hypot(x - CITADEL_X, z - CITADEL_Z) < 22 || Math.abs(z) > 58 && Math.abs(x) < 16) {
				continue;
			}
			boolean nearIsland = false;
			for (int k = 0; k < 4; k++) {
				double b = Math.toRadians(45 + k * 90);
				nearIsland |= Math.hypot(x - Math.cos(b) * 80, z - Math.sin(b) * 80) < 10;
			}
			for (double w : new double[] {30, 210}) {
				nearIsland |= Math.hypot(x - Math.cos(Math.toRadians(w)) * 65, z - Math.sin(Math.toRadians(w)) * 65) < 9;
			}
			if (!nearIsland) {
				list.add(new Fragment(x, z, F + 34 + roll % 40, 1.2 + (roll % 17) / 10.0));
			}
		}
		return list;
	}

	/** Rock fragments drifting round the colosseum at different heights, some crowned with amethyst. */
	private static void fragments(BlockState[] c, int dx, int dz) {
		for (Fragment f : FRAGMENTS) {
			double t = Math.hypot(dx - f.x(), dz - f.z());
			if (t > f.r() + 0.5) {
				continue;
			}
			double half = Math.sqrt(Math.max(0, (f.r() + 0.5) * (f.r() + 0.5) - t * t));
			int bottom = f.y() - (int) Math.ceil(half * 1.2);
			int top = f.y() + (int) Math.floor(half * 0.5);
			int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 55);
			fill(c, bottom, top, roll % 3 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : roll % 3 == 1 ? STONE : Blocks.TUFF.defaultBlockState());
			put(c, top, roll < 300 ? Blocks.MOSS_BLOCK.defaultBlockState() : at(c, top));
			if (roll < 140 && t < f.r() - 0.3) {
				put(c, top + 1, Blocks.AMETHYST_CLUSTER.defaultBlockState());
			}
		}
	}

	/** The sky citadel's island, north of the colosseum. */
	static final double CITADEL_X = 0;
	static final double CITADEL_Z = -104;

	/**
	 * The sky citadel: a castle on its own floating island beyond the north gate. A curtain wall with
	 * battlements and a gateway round a courtyard; four corner towers and a tall keep, deepslate banded in
	 * amethyst, with purple windows lit from inside and purpur spires; cherry trees outside the walls, and
	 * chains hanging from the island's underside.
	 */
	private static void citadel(BlockState[] c, int dx, int dz) {
		double lx = dx - CITADEL_X;
		double lz = dz - CITADEL_Z;
		double t = Math.hypot(lx, lz);
		if (t > 16.5) {
			return;
		}
		int top = F + 52;
		int roll = PracticeLayout.scatter(CX + dx, CZ + dz, 63);
		int depth = (int) Math.round((16.5 - t) * 1.25) + roll % 4 + 2;
		fill(c, top - depth, top - 3, t > 12 ? STONE : Blocks.DEEPSLATE.defaultBlockState());
		fill(c, top - 2, top - 1, DIRT);
		double square = Math.max(Math.abs(lx), Math.abs(lz));
		put(c, top, square < 8 ? POLISHED : GRASS);
		if (t > 14.5 && roll < 90) {
			fill(c, top - depth - 8, top - depth - 1, CHAIN);
		}
		// Curtain wall with a gateway on the south side.
		if (Math.round(square) == 8 && square <= 8.5) {
			boolean gateway = lz > 7.5 && Math.abs(lx) <= 1.5;
			fill(c, top + 1, top + 6, TILES);
			if (gateway) {
				fill(c, top + 1, top + 3, AIR);
				put(c, top + 4, CHISELED);
			}
			if (((int) Math.round(lx) + (int) Math.round(lz)) % 2 == 0) {
				put(c, top + 7, BRICKS);
			}
		}
		// Corner towers.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				double ox = lx - sx * 8;
				double oz = lz - sz * 8;
				double r = Math.hypot(ox, oz);
				if (r <= 2.6) {
					spiredTower(c, ox, oz, r, 2.6, top + 1, top + 14, 8);
				}
			}
		}
		// The keep.
		if (t <= 4.6) {
			spiredTower(c, lx, lz, t, 4.6, top + 1, top + 24, 16);
		}
		// Cherry trees outside the walls.
		int[][] trees = {{12, 3}, {-12, -3}, {3, -12}, {-4, 12}};
		for (int[] tr : trees) {
			double ox = lx - tr[0];
			double oz = lz - tr[1];
			if (Math.hypot(ox, oz) <= 3.4) {
				cherry(c, ox, oz, top + 1, roll);
			}
		}
	}

	/**
	 * A round tower from {@code base} to {@code bodyTop}: deepslate tiles round brick, amethyst bands, a
	 * lantern room behind purple windows, battlements, and a purpur spire {@code spire} blocks high with a
	 * sea lantern and an end rod at the tip.
	 */
	private static void spiredTower(BlockState[] c, double ox, double oz, double t, double radius, int base, int bodyTop, int spire) {
		boolean rim = t > radius - 1;
		fill(c, base, bodyTop, rim ? TILES : BRICKS);
		int room = base + (bodyTop - base) * 2 / 3;
		put(c, base + (bodyTop - base) / 3, AMETHYST);
		put(c, bodyTop - 1, AMETHYST);
		if (t <= radius - 2) {
			fill(c, room - 2, room + 2, AIR);
			if (t < 0.5) {
				put(c, room, SEA_LANTERN);
			}
		} else if (rim && (Math.abs(ox) < 1.2 || Math.abs(oz) < 1.2)) {
			fill(c, room - 2, room + 2, PURPLE_GLASS);
		}
		if (rim && ((int) Math.round(ox) + (int) Math.round(oz)) % 2 == 0) {
			put(c, bodyTop + 1, BRICK_WALL);
		}
		for (int y = bodyTop + 1; y <= bodyTop + spire; y++) {
			double rr = radius * (1 - (y - bodyTop - 1) / (double) spire) - 0.3;
			if (t <= rr) {
				put(c, y, rr - t < 1.2 ? PURPUR : BRICKS);
			}
		}
		if (t < 0.5) {
			put(c, bodyTop + spire + 1, SEA_LANTERN);
			put(c, bodyTop + spire + 2, END_ROD);
		}
	}

	/** Whether a block hangs on a neighbour, so it goes in after everything solid. */
	static boolean attached(BlockState state) {
		Block b = state.getBlock();
		return b instanceof WallBannerBlock || b instanceof LanternBlock || b == Blocks.PINK_PETALS || b == Blocks.END_ROD
			|| b == Blocks.AMETHYST_CLUSTER || b == Blocks.IRON_CHAIN || b == Blocks.SHORT_GRASS || b == Blocks.ALLIUM
			|| b == Blocks.LILY_OF_THE_VALLEY || b == Blocks.PINK_TULIP;
	}

	/**
	 * Applies columns [from, to) of the blueprint: solid blocks ({@code attachedPass} false) or the hanging
	 * ones (true). Only blocks that differ from the blueprint are set. Returns how many were set.
	 */
	static int apply(ServerLevel level, int from, int to, boolean attachedPass) {
		int changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = from; i < Math.min(to, columns()); i++) {
			int dx = dx(i);
			int dz = dz(i);
			BlockState[] col = column(dx, dz);
			if (col == null) {
				continue;
			}
			for (int y = Y0; y <= Y1; y++) {
				BlockState want = col[y - Y0];
				if (attached(want) != attachedPass) {
					continue;
				}
				pos.set(CX + dx, y, CZ + dz);
				if (!level.getBlockState(pos).equals(want)) {
					level.setBlock(pos, want, Block.UPDATE_CLIENTS);
					changed++;
				}
			}
		}
		return changed;
	}
}

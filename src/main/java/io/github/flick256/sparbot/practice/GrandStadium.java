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
 * From the middle out: a field inlaid with a magic circle (amethyst rings, a purpur hexagram, glowing rune
 * stones), a blackstone podium wall with crying-obsidian runes and purple banners, two tiers of seats with
 * purpur aisles split by a lantern-lit promenade, royal boxes over the four gates, a three-storey arcaded
 * outer wall lit by soul lanterns from inside, twelve towers with purple spires and glowing lantern rooms,
 * a moat crossed by four bridges under raised portcullises, a cherry-blossom garden, and above it all a
 * floating halo, crystal shards and four floating cherry islands hung on chains.
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
	static final int Y1 = F + 66;
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
		return c;
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

	/** The field: stone and earth to dig into, and the magic circle in the floor. */
	private static void field(BlockState[] c, int dx, int dz, double d, double deg) {
		fill(c, Y0, S - 4, STONE);
		fill(c, S - 3, S - 1, DIRT);
		BlockState floor = ((int) Math.floor(d / 3) + (int) Math.floor((deg + 180) / 15)) % 2 == 0 ? TILES : POLISHED;
		if (d <= 1.6) {
			floor = AMETHYST;
		} else if (Math.abs(d - 6) < 0.5 || Math.abs(d - 25) < 0.5) {
			floor = AMETHYST;
		} else if (Math.abs(d - 14) < 0.5) {
			floor = PRISMARINE;
		} else if (hexagram(dx, dz, 14)) {
			floor = PURPUR;
		} else if (Math.abs(d - 20) < 0.7 && offStep(deg, 15, d) < 0.7) {
			floor = SEA_LANTERN;
		} else if (d > 25.5 && d < 29.5 && offStep(deg, 30, d) < 0.55) {
			floor = PRISMARINE;
		} else if (d > 29.5) {
			floor = BLACK_BRICKS;
		} else if (d < 6 && offStep(deg, 60, d) < 0.5) {
			floor = PURPUR;
		}
		put(c, S, floor);
		// Banners on the podium wall's inner face, and soul lanterns between them.
		if (d > 29.5 && offStep(deg, 15, d) < 0.6 && gate(dx, dz) == null) {
			Direction in = outward(-dx, -dz);
			put(c, F + 4, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, in));
		}
	}

	/** Whether (dx, dz) is on the hexagram inscribed in a circle of radius r. */
	private static boolean hexagram(int dx, int dz, double r) {
		for (int t = 0; t < 2; t++) {
			for (int i = 0; i < 3; i++) {
				double a1 = Math.toRadians(90 + t * 60 + i * 120);
				double a2 = Math.toRadians(90 + t * 60 + (i + 1) * 120);
				if (segment(dx, dz, r * Math.cos(a1), r * Math.sin(a1), r * Math.cos(a2), r * Math.sin(a2)) < 0.55) {
					return true;
				}
			}
		}
		return false;
	}

	private static double segment(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double t = Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz)));
		return Math.hypot(px - ax - t * vx, pz - az - t * vz);
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

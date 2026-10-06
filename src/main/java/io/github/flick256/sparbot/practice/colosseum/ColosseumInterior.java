package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACKSTONE;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_WALL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHAIN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHISELED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CRYING;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.GILDED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.HANGING_SOUL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.POLISHED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPLE_GLASS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.S;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.TILES;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.WATER;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.fill;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The inside of the Grand Bowl's stands, under the seats: what visitors walk through to reach them, and
 * where the colosseum's story is hidden (see {@link ColosseumLore}).
 *
 * <p>The space under each tier is split into storeys every {@value #STOREY} blocks. Three ring corridors run
 * all the way round (the Inner Ring behind the podium, the Middle and Outer Rings under the concourses), and
 * between them lie the three Galleries, forty-eight sectors each, numbered sunwise from the east gate. Every
 * hall is one of sixteen kinds, named on a plaque over its door, with beams, sconces and its own furnishings;
 * six are landmarks of the story. Each gate's passage opens, under the first two tiers, into a great atrium
 * where the Grand Stairs climb in two flights and a bridge to the first concourse; spiral stairs in the Middle
 * and Outer Rings climb to both concourses.
 */
final class ColosseumInterior {
	/** Storey height: floor to floor. */
	static final int STOREY = 8;
	/** Room sectors, in degrees (48 of them). */
	static final double SECTOR = 7.5;
	/** The ring corridors and the room bands (the Galleries), by distance from the centre (between them, one-block walls). */
	static final double[][] CORRIDORS = {{50.5, 56.5}, {80.5, 86.5}, {116.5, 122.5}};
	static final double[][] ROOMS = {{57.5, 79.5}, {87.5, 115.5}, {123.5, 146.5}};
	static final String[] RINGS = {"THE INNER RING", "THE MIDDLE RING", "THE OUTER RING"};
	/** Spiral staircases: in the two outer corridors, up to the concourse floors above them. */
	static final double[] STAIR_RING = {83.5, 119.0};
	static final int[] STAIR_TOP = {F + 42, F + 76};
	static final double STAIR_R = 2.4;
	/** The atrium of the Grand Stairs in each gate passage, by distance along the gate. */
	static final double ATRIUM_IN = 86.5;
	static final double ATRIUM_OUT = 116.5;

	private ColosseumInterior() {
	}

	/** The lowest block of the seating's shell above distance {@code d}, or -1 where there are no stands. */
	static int underside(double d) {
		if (d <= CelestialColosseum.PODIUM) {
			return -1;
		}
		if (d <= CelestialColosseum.TIER1) {
			return F + 8 + (int) Math.floor(d) - 51;
		}
		if (d <= CelestialColosseum.CONCOURSE1) {
			return F + 39;
		}
		if (d <= CelestialColosseum.TIER2) {
			return F + 42 + (int) Math.floor(d) - 87;
		}
		if (d <= CelestialColosseum.CONCOURSE2) {
			return F + 73;
		}
		if (d <= CelestialColosseum.TIER3) {
			return F + 76 + (int) Math.floor(d) - 122;
		}
		return -1;
	}

	/** Whether storey {@code k} (feet at F + 8k) fits under the seats at distance {@code d}. */
	static boolean storey(double d, int k) {
		int u = underside(d);
		return u >= 0 && (k == 0 || F + STOREY * k <= u - 5);
	}

	static int band(double[][] bands, double d) {
		for (int i = 0; i < bands.length; i++) {
			if (d > bands[i][0] && d <= bands[i][1]) {
				return i;
			}
		}
		return -1;
	}

	/** The sector (0-47, sunwise from the east gate) at {@code deg}. */
	static int sector(double deg) {
		return (int) Math.floor((deg % 360 + 360) % 360 / SECTOR) % 48;
	}

	static void build(BlockState[] c, int dx, int dz, double d, double deg) {
		int u = underside(d);
		if (u < 0) {
			return;
		}
		fill(c, F, u - 1, AIR);
		CelestialColosseum.Gate wide = CelestialColosseum.gate(dx, dz, 12.5);
		if (wide != null) {
			double lat = dx * wide.gz() - dz * wide.gx();
			if (wide.along() > ATRIUM_IN && wide.along() <= ATRIUM_OUT) {
				atrium(c, dx, dz, wide, lat, u);
				return;
			}
			if (wide.perp() <= 6.5) {
				passage(c, dx, dz, d, wide, lat, u);
				return;
			}
		}
		for (int i = 0; i < STAIR_RING.length; i++) {
			for (int j = 0; j < 8; j++) {
				double a = Math.toRadians(22.5 + 45 * j);
				double ox = dx - Math.cos(a) * STAIR_RING[i];
				double oz = dz - Math.sin(a) * STAIR_RING[i];
				double r = Math.hypot(ox, oz);
				if (r <= STAIR_R) {
					spiral(c, ox, oz, r, u, STAIR_TOP[i]);
					return;
				}
			}
		}
		double sectorDeg = ((deg % SECTOR) + SECTOR) % SECTOR;
		double v = (sectorDeg - SECTOR / 2) * Math.PI / 180 * d;
		double halfW = SECTOR / 2 * Math.PI / 180 * d;
		int corridor = band(CORRIDORS, d);
		int rooms = band(ROOMS, d);
		int sector = sector(deg);
		boolean radialWall = rooms >= 0 && halfW - Math.abs(v) < 0.5;
		for (int k = 0; storey(d, k); k++) {
			int feet = F + STOREY * k;
			int ceiling = storey(d, k + 1) ? feet + STOREY - 1 : u;
			if (k > 0) {
				put(c, feet - 1, k % 2 == 0 ? TILES : POLISHED);
			}
			if (corridor < 0 && rooms < 0) {
				// A ring wall: a door at the middle of each sector where there is floor on both sides, windows either side of it.
				fill(c, feet, ceiling - 1, BRICKS);
				boolean open = storey(d - 1, k) && storey(d + 1, k);
				if (Math.abs(v) <= 1.0 && open) {
					fill(c, feet, feet + 2, AIR);
					put(c, feet + 3, CHISELED);
				} else if (open && Math.abs(v) >= 2.2 && Math.abs(v) <= 3.2 && ceiling - feet > 4) {
					fill(c, feet + 1, feet + 3, PURPLE_GLASS);
				}
			} else if (radialWall) {
				fill(c, feet, ceiling - 1, BRICKS);
				double mid = (ROOMS[rooms][0] + ROOMS[rooms][1]) / 2;
				if (Math.abs(d - mid) <= 1.0) {
					fill(c, feet, feet + 2, AIR);
					put(c, feet + 3, CHISELED);
				}
			} else if (corridor >= 0) {
				corridor(c, dx, dz, d, deg, corridor, feet, ceiling, k, sector, v);
			} else {
				double inner = ROOMS[rooms][0];
				Room r = new Room(c, dx, dz, d - inner, ROOMS[rooms][1] - inner, v, halfW - 0.5, feet, ceiling, sector, rooms, k);
				int landmark = Landmarks.at(rooms, sector, k);
				if (landmark >= 0) {
					Landmarks.room(r, landmark);
				} else {
					room(r, kind(sector, rooms, k));
				}
			}
		}
	}

	// --- Gate passages and the Grand Stairs ---

	private static String gateName(CelestialColosseum.Gate g) {
		return g.gx() > 0 ? "East" : g.gx() < 0 ? "West" : g.gz() > 0 ? "South" : "North";
	}

	/**
	 * A gate's passage (outside its atrium): a vaulted hall twelve high, lit down the middle, walled off with
	 * doors into the rings it crosses (each named over its door), the Visitor's Guide on a lectern near the
	 * outer end, and a welcome over the way in.
	 */
	private static void passage(BlockState[] c, int dx, int dz, double d, CelestialColosseum.Gate g, double lat, int u) {
		int top = Math.min(F + 11, u - 1);
		double p = Math.abs(lat);
		Direction toCentreLine = lat > 0 ? outward(-g.gz(), g.gx()) : outward(g.gz(), -g.gx());
		if (p <= 5.5) {
			fill(c, top + 1, u - 1, BRICKS);
			put(c, top + 1, p < 1.5 ? POLISHED : TILES);
			put(c, S, p < 0.5 ? CHISELED : POLISHED);
			if (p < 0.5 && Math.floorMod(Math.round(g.along()), 6) == 0) {
				put(c, top, HANGING_SOUL);
			}
			int along = (int) Math.floor(g.along());
			if (along == 140 && p > 3.5 && p <= 4.5 && lat > 0) {
				ColosseumLore.lectern(c, dx, F, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toCentreLine)
					.setValue(LecternBlock.HAS_BOOK, true), ColosseumLore.GUIDE);
			}
			if (p > 4.5) {
				int ring = band(CORRIDORS, d);
				if (ring >= 0 && Math.abs(d - (CORRIDORS[ring][0] + CORRIDORS[ring][1]) / 2) < 0.5) {
					wallSign(c, dx, F + 3, dz, toCentreLine, DyeColor.YELLOW, true, RINGS[ring], ring == 0 ? "First Gallery" : "Galleries",
						ring == 0 ? "beyond" : ring == 1 ? "I and II" : "II and III", ring == 0 ? "" : "spiral stairs");
				} else if (along == 144 && lat > 0) {
					wallSign(c, dx, F + 3, dz, toCentreLine, DyeColor.YELLOW, true, "THE CELESTIAL", "COLOSSEUM", gateName(g) + " Gate", "Guide here");
				}
			}
		} else {
			fill(c, F, u - 1, TILES);
			if (band(CORRIDORS, d) >= 0 && band(CORRIDORS, d - 1) >= 0 && band(CORRIDORS, d + 1) >= 0) {
				fill(c, F, F + 2, AIR);
				put(c, F + 3, CHISELED);
			} else if (Math.floorMod(Math.round(g.along()), 6) == 3) {
				put(c, F + 3, SEA_LANTERN);
			}
		}
	}

	/**
	 * The atrium in a gate passage under the first two tiers: the passage opens to the underside of the seats
	 * high above, hung with chandeliers. Beside it, behind balustrades, the Grand Stairs: one flight climbs
	 * outward from the Middle Ring, a bridge crosses the atrium, and the second flight climbs back to a landing
	 * that comes out through the seats onto the first concourse.
	 */
	private static void atrium(BlockState[] c, int dx, int dz, CelestialColosseum.Gate g, double lat, int u) {
		double a = g.along();
		int fa = (int) Math.floor(a);
		double p = Math.abs(lat);
		boolean sideA = lat > 0;
		Direction out = outward(g.gx(), g.gz());
		Direction in = outward(-g.gx(), -g.gz());
		Direction toCentreLine = sideA ? outward(-g.gz(), g.gx()) : outward(g.gz(), -g.gx());
		int feetA = a < 88 ? F : a < 111 ? F + fa - 87 : F + 24;
		int feetB = a < 92 ? F + 43 : a < 111 ? F + 135 - fa : F + 24;
		int feet = sideA ? feetA : feetB;
		boolean bridge = a > 111;
		boolean vomitory = !sideA && a <= 93;
		if (p <= 5.5) {
			put(c, S, p < 0.5 ? CHISELED : Math.floorMod(fa, 4) == 0 ? TILES : POLISHED);
			if (p > 3.5 && p <= 4.5 && Math.floorMod(fa, 4) == 2) {
				put(c, S, SEA_LANTERN);
			}
			if (bridge) {
				fill(c, F + 22, F + 23, BLACK_BRICKS);
				put(c, F + 23, p < 1.5 ? GILDED : POLISHED);
				if (a <= 112 || a > 115.5) {
					put(c, F + 24, BLACK_WALL);
					if (Math.round(p) % 3 == 0) {
						put(c, F + 25, LANTERN);
					}
				} else if (p > 4.5) {
					put(c, F + 21, HANGING_SOUL);
				}
				if (p > 4.5) {
					put(c, F + 21, TILES);
				}
			}
			if (p < 0.5 && (fa == 96 || fa == 104)) {
				// Chandeliers on long chains from the seats far above.
				fill(c, F + 19, u - 1, CHAIN);
				put(c, F + 18, LANTERN.setValue(LanternBlock.HANGING, true));
			}
			if (p > 0.5 && p < 1.5 && (fa == 96 || fa == 104)) {
				put(c, F + 19, CHAIN);
				put(c, F + 18, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
			return;
		}
		if (p <= 6.5) {
			// The balustrade between the atrium and the stairs, its rail following them up.
			int rail = bridge ? F + 24 : vomitory ? F + 49 : feet;
			fill(c, S, rail - 1, BRICKS);
			put(c, rail, BLACK_WALL);
			if (!bridge && fa % 4 == 0) {
				put(c, rail - 1, SEA_LANTERN);
				put(c, rail + 1, LANTERN);
			}
			if (!bridge && fa % 4 == 2 && rail > F + 3) {
				// Sconces on the atrium side of the balustrade, lighting the hall below.
				put(c, F + 3, SEA_LANTERN);
			}
			return;
		}
		if (p <= 11.5) {
			int step = (bridge ? F + 24 : feet) - 1;
			fill(c, S, step, BRICKS);
			boolean runner = Math.abs(p - 9) < 0.5;
			boolean flight = !bridge && (sideA ? a >= 88 : a >= 92);
			if (flight) {
				Block stair = runner ? Blocks.PURPUR_STAIRS : Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
				put(c, step, stair.defaultBlockState().setValue(StairBlock.FACING, sideA ? out : in));
			} else {
				put(c, step, runner ? CelestialColosseum.PURPUR : bridge ? POLISHED : TILES);
			}
			if (vomitory) {
				// The way out through the first rows of seats onto the concourse.
				fill(c, F + 43, F + 48, AIR);
			}
			if (fa == 87 && sideA && p > 10.5) {
				wallSign(c, dx, F + 3, dz, toCentreLine, DyeColor.YELLOW, true, "THE GRAND", "STAIR", "to the First", "Concourse");
			}
			if (fa == 113 && p > 10.5) {
				wallSign(c, dx, F + 27, dz, toCentreLine, DyeColor.YELLOW, true, "THE BRIDGE", "OF THE", "SEVEN", sideA ? "on, and up" : "");
			}
			return;
		}
		// The outer wall, with windows that climb with the stair.
		int topWall = vomitory ? Math.max(u - 1, F + 49) : u - 1;
		fill(c, S, topWall, TILES);
		int stand = bridge ? F + 24 : feet;
		if (Math.floorMod(fa, 6) == 3 && stand + 3 < u - 1) {
			fill(c, stand + 1, stand + 3, PURPLE_GLASS);
		} else if (Math.floorMod(fa, 6) == 0) {
			put(c, stand + 4, SEA_LANTERN);
		}
	}

	// --- Spiral staircases ---

	/**
	 * A spiral staircase round a lit newel, one turn a storey, from the ground to the concourse floor
	 * {@code top}; railings where the floors meet it, and round the opening at the top.
	 */
	private static void spiral(BlockState[] c, double ox, double oz, double r, int u, int top) {
		fill(c, F, top + 2, AIR);
		put(c, S, POLISHED);
		if (r < 0.9) {
			fill(c, F, top, TILES);
			for (int y = F + 3; y <= top; y += 4) {
				put(c, y, SEA_LANTERN);
			}
			put(c, top + 1, LANTERN);
			return;
		}
		double th = Math.atan2(oz, ox);
		if (th < 0) {
			th += Math.PI * 2;
		}
		int s = Math.min(STOREY - 1, (int) (th / (Math.PI * 2) * STOREY));
		for (int k = 0; F + STOREY * k + s <= top; k++) {
			put(c, F + STOREY * k + s, (k + s) % 2 == 0 ? BLACK_BRICKS : POLISHED);
		}
		boolean rim = r > STAIR_R - 0.9;
		if (!rim) {
			return;
		}
		// Railings at each storey's floor (never over a step's headroom), and round the opening at the top.
		for (int k = 1; F + STOREY * k <= top; k++) {
			if (s >= 1 && s <= STOREY - 3) {
				put(c, F + STOREY * k, BLACK_WALL);
			}
		}
		int sTop = (top - F) % STOREY;
		if (s > sTop) {
			put(c, top + 1, BLACK_WALL);
		}
	}

	// --- Corridors ---

	private static void corridor(BlockState[] c, int dx, int dz, double d, double deg, int ring, int feet, int ceiling, int k, int sector, double v) {
		double[] band = CORRIDORS[ring];
		double mid = (band[0] + band[1]) / 2;
		long arc = Math.round(Math.toRadians(deg) * d);
		boolean centre = Math.abs(d - mid) < 0.5;
		int floor = feet - 1;
		put(c, floor, centre && Math.floorMod(arc, 4) == 0 ? CHISELED : Math.floorMod(arc, 2) == 0 ? POLISHED : TILES);
		if (centre && Math.floorMod(arc, 6) == 0) {
			hang(c, feet, ceiling, LANTERN.setValue(LanternBlock.HANGING, true));
		}
		if (centre && Math.floorMod(arc, 6) == 3) {
			put(c, floor, SEA_LANTERN);
		}
		boolean innerEdge = d - band[0] < 1.0;
		boolean outerEdge = band[1] - d < 1.0;
		if (!innerEdge && !outerEdge) {
			return;
		}
		Direction away = innerEdge ? outward(dx, dz) : outward(-dx, -dz);
		boolean byDoor = Math.abs(v) < 1.6;
		if (byDoor) {
			// (keep the way to the door clear)
		} else if (Math.floorMod(arc, 12) == 6) {
			// Banners on the walls, purple and cyan in turn.
			Block banner = Math.floorMod(arc / 12, 2) == 0 ? Blocks.WALL_BANNER.purple() : Blocks.WALL_BANNER.cyan();
			put(c, feet + 3, banner.defaultBlockState().setValue(WallBannerBlock.FACING, away));
		} else if (k == 0 && Math.floorMod(arc, 12) >= 9 && Math.floorMod(arc, 12) <= 10) {
			put(c, feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, away.getOpposite()));
		} else if (Math.floorMod(arc, 12) == 2 && ceiling - feet > 3) {
			put(c, feet + 2, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, away));
		}
		// The plaque over each door: which hall lies beyond.
		int beyond = outerEdge ? ring : ring - 1;
		double there = outerEdge ? band[1] + 2 : band[0] - 2;
		if (Math.abs(v) < 0.5 && beyond >= 0 && storey(there, k) && storey(d, k)) {
			String[] name = name(beyond, sector, k);
			wallSign(c, dx, feet + 3, dz, away, DyeColor.YELLOW, true, "Sector " + (sector + 1), name[0], name[1],
				"Gallery " + ColosseumLore.ROMAN[beyond] + ", L" + (k + 1));
		}
	}

	/** A lantern hung from the ceiling: straight under it, or on a chain where the room is tall. */
	static void hang(BlockState[] c, int feet, int ceiling, BlockState lantern) {
		if (ceiling - feet > STOREY) {
			fill(c, feet + 6, ceiling - 1, CHAIN);
			put(c, feet + 5, lantern);
		} else {
			put(c, ceiling - 1, lantern);
		}
	}

	/** A dark oak sign on the wall behind it, facing {@code facing}. */
	static void wallSign(BlockState[] c, int dx, int y, int dz, Direction facing, DyeColor color, boolean glow, String... lines) {
		ColosseumLore.sign(c, dx, y, dz, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), color, glow, lines);
	}

	// --- Rooms ---

	/** One column of a room, and where it is in the room. */
	static final class Room {
		final BlockState[] c;
		final int dx;
		final int dz;
		/** From the inner wall (0) to the outer ({@link #depth}). */
		final double u;
		final double depth;
		/** Across, from the middle; {@link #halfW} to each side wall. */
		final double v;
		final double halfW;
		final int feet;
		final int ceiling;
		final int sector;
		final int band;
		final int k;
		final int iu;
		final int iv;
		final double av;
		final boolean outer;
		final boolean inner;
		final boolean side;
		/** Where the doors are: kept clear. */
		final boolean doorway;
		final Direction in;
		final Direction out;
		final Direction towardSide;
		final Direction awayFromSide;

		Room(BlockState[] c, int dx, int dz, double u, double depth, double v, double halfW, int feet, int ceiling, int sector, int band, int k) {
			this.c = c;
			this.dx = dx;
			this.dz = dz;
			this.u = u;
			this.depth = depth;
			this.v = v;
			this.halfW = halfW;
			this.feet = feet;
			this.ceiling = ceiling;
			this.sector = sector;
			this.band = band;
			this.k = k;
			this.iu = (int) Math.floor(u);
			this.iv = (int) Math.round(v);
			this.av = Math.abs(v);
			this.outer = depth - u < 1.0;
			this.inner = u < 1.0;
			this.side = halfW - av < 1.0;
			this.doorway = av <= 1.6 && (u < 2.0 || depth - u < 2.0) || Math.abs(u - depth / 2) <= 1.6 && halfW - av < 2.0;
			this.in = outward(-dx, -dz);
			this.out = outward(dx, dz);
			Direction plus = outward(-dz, dx);
			Direction minus = outward(dz, -dx);
			this.towardSide = v > 0 ? plus : minus;
			this.awayFromSide = v > 0 ? minus : plus;
		}

		int floor() {
			return feet - 1;
		}

		boolean flatCeiling() {
			return ceiling - feet == STOREY - 1;
		}

		boolean mid(double tolerance) {
			return Math.abs(u - depth / 2) < tolerance;
		}

		int roll(int salt) {
			return PracticeLayout.scatter(sector * 31 + iu, iv * 17 + k * 5 + band, salt);
		}

		void put(int y, BlockState s) {
			CelestialColosseum.put(c, y, s);
		}

		void fill(int y0, int y1, BlockState s) {
			CelestialColosseum.fill(c, y0, y1, s);
		}

		void sign(int y, Direction facing, DyeColor color, boolean glow, String... lines) {
			wallSign(c, dx, y, dz, facing, color, glow, lines);
		}

		void lectern(Direction facing, ColosseumLore.Book book) {
			ColosseumLore.lectern(c, dx, feet, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing)
				.setValue(LecternBlock.HAS_BOOK, true), book);
		}
	}

	static final int KINDS = 16;
	static final String[] KIND_NAMES = {"FEAST HALL", "ARMOURY", "LIBRARY", "SHRINE", "TRAINING HALL", "STRONGROOM", "ALCHEMIST", "LOUNGE",
		"STOREROOM", "BATHS", "GARDEN HALL", "MUSIC HALL", "CRYPT", "BARRACKS", "MAP ROOM", "KITCHEN"};

	static int kind(int sector, int band, int k) {
		return PracticeLayout.scatter(sector * 7 + band, k * 13 + 5, 77) % KINDS;
	}

	/** A hall's name: its kind and an epithet (or a landmark's own name), on two lines. */
	static String[] name(int band, int sector, int k) {
		int landmark = Landmarks.at(band, sector, k);
		if (landmark >= 0) {
			return Landmarks.NAMES[landmark];
		}
		int kind = kind(sector, band, k);
		String epithet = ColosseumLore.EPITHETS[PracticeLayout.scatter(sector, band * 11 + k, 19) % ColosseumLore.EPITHETS.length];
		return new String[] {KIND_NAMES[kind], epithet};
	}

	/** Things scrawled on walls here and there, overheard in the stands. */
	static final String[][] RUMOURS = {{"THE STAR HUMS", "WHEN THE", "STANDS ARE", "EMPTY"}, {"WHO WAS", "VAELOR?", "ASK THE", "ARCHIVIST"},
		{"NO BOUT IS", "EVER THE", "LAST BOUT", ""}, {"SEVEN SAT", "IN THE NORTH", "AND SIX", "CAME BACK"}, {"HEARD", "KNOCKING", "UNDER THE", "FIELD AGAIN"},
		{"THE WARDEN", "NEVER", "SLEEPS", ""}, {"LIGHT GOES UP", "DARK GOES", "DOWN", ""}, {"1000 - 0", "", "", ""}, {"DON'T STOP", "FIGHTING", "", ""},
		{"THE ROSE", "POINTS", "DOWN", ""}, {"COUNT THE", "BOUTS", "THE LEDGER", "DOES"}, {"ASTER WAS", "NEVER A", "GIFT", ""},
		{"THE CROWD", "IS THE", "KEEPER", ""}, {"WINTER OF 40:", "WE FOUGHT", "IN THE SNOW", "ALL NIGHT"}};

	/** One column of an ordinary hall: the shared fittings (beams, lanterns, sconces, a scrawl), then its kind's furnishings. */
	private static void room(Room r, int kind) {
		// Beams across the ceiling, lanterns in two rows, sconces down the side walls.
		if (r.flatCeiling() && r.iu % 4 == 0 && !r.side) {
			r.put(r.ceiling - 1, kind == 12 || kind == 5 ? BLACK_BRICKS : Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState());
		}
		if (Math.floorMod(r.iu, 5) == 2 && Math.abs(r.av - Math.max(1.5, r.halfW / 2)) < 0.5) {
			hang(r.c, r.feet, r.ceiling, (kind == 12 || kind == 3 ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState()
				.setValue(LanternBlock.HANGING, true));
		}
		if (r.side && r.iu % 6 == 3 && !r.doorway) {
			r.put(r.feet + 3, (kind == 12 ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH).defaultBlockState().setValue(WallTorchBlock.FACING,
				r.awayFromSide));
		}
		if (r.outer && r.iv == 2 && PracticeLayout.scatter(r.sector, r.band * 7 + r.k, 3) % 3 == 0) {
			String[] rumour = RUMOURS[PracticeLayout.scatter(r.sector, r.band + r.k * 3, 23) % RUMOURS.length];
			r.sign(r.feet + 2, r.in, DyeColor.RED, false, rumour);
		}
		switch (kind) {
			case 0 -> feast(r);
			case 1 -> armoury(r);
			case 2 -> library(r);
			case 3 -> shrine(r);
			case 4 -> training(r);
			case 5 -> strongroom(r);
			case 6 -> alchemist(r);
			case 7 -> lounge(r);
			case 8 -> storeroom(r);
			case 9 -> baths(r);
			case 10 -> garden(r);
			case 11 -> music(r);
			case 12 -> crypt(r);
			case 13 -> barracks(r);
			case 14 -> mapRoom(r);
			default -> kitchen(r);
		}
	}

	private static BlockState candles(int n, boolean lit) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, lit);
	}

	/** A feast hall: a long table down the middle with candles, benches either side, barrels and banners. */
	static void feast(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
		if (r.doorway) {
			return;
		}
		boolean tableRun = r.u >= 3 && r.depth - r.u >= 3;
		if (r.av < 0.5 && tableRun) {
			r.put(r.feet, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			if (r.iu % 3 == 0) {
				r.put(r.feet + 1, candles(3, true));
			} else if (r.iu % 3 == 1) {
				r.put(r.feet + 1, Blocks.DECORATED_POT.defaultBlockState());
			}
		} else if (r.av >= 0.5 && r.av < 1.5 && tableRun) {
			r.put(r.feet, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.towardSide));
		} else if (r.side && r.iu % 4 == 1) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
			r.put(r.feet + 1, Blocks.BARREL.defaultBlockState());
		} else if (r.outer && r.iv % 3 == 0 && r.iv != 0) {
			r.put(r.feet + 3, Blocks.WALL_BANNER.red().defaultBlockState().setValue(WallBannerBlock.FACING, r.in));
		} else if (r.outer && r.iv == 0) {
			// The high seat at the head of the table.
			r.put(r.feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.out));
			r.put(r.feet + 1, Blocks.GOLD_BLOCK.defaultBlockState());
		}
	}

	/** An armoury: anvils, grindstones and smithing tables on the far wall, weapon racks and barrels down the sides. */
	static void armoury(Room r) {
		r.put(r.floor(), r.av < 1.0 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : BLACK_BRICKS);
		if (r.doorway) {
			return;
		}
		if (r.outer) {
			BlockState[] row = {Blocks.ANVIL.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState(), Blocks.GRINDSTONE.defaultBlockState(),
				Blocks.BLAST_FURNACE.defaultBlockState()};
			r.put(r.feet, row[Math.floorMod(r.iv, 4)]);
			if (Math.floorMod(r.iv, 4) == 3) {
				r.put(r.feet + 1, Blocks.LANTERN.defaultBlockState());
			}
		} else if (r.side && r.iu % 2 == 0) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
			r.put(r.feet + 1, Blocks.IRON_BARS.defaultBlockState());
		} else if (r.side) {
			r.put(r.feet + 2, Blocks.WALL_BANNER.black().defaultBlockState().setValue(WallBannerBlock.FACING, r.awayFromSide));
		} else if (r.iu % 6 == 3 && Math.abs(r.av - r.halfW / 2) < 0.5) {
			// A rack: a post holding an iron plate and a shield of chain.
			r.put(r.feet, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			r.put(r.feet + 1, Blocks.IRON_BLOCK.defaultBlockState());
			r.put(r.feet + 2, Blocks.END_ROD.defaultBlockState());
		}
	}

	/** A library: bookshelves floor to ceiling, a red runner, lecterns to read at, an enchanting table, candles. */
	static void library(Room r) {
		r.put(r.floor(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
		if (r.av < 1.0 && !r.doorway) {
			r.put(r.feet, Blocks.CARPET.red().defaultBlockState());
		}
		if (r.doorway) {
			return;
		}
		if (r.side || r.outer) {
			for (int y = r.feet; y < r.ceiling; y++) {
				r.put(y, (y + r.iu + r.iv) % 5 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
			}
		} else if (r.iu % 5 == 3 && Math.abs(r.av - 2.5) < 0.5) {
			r.put(r.feet, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, r.awayFromSide));
		} else if (r.mid(0.5) && r.av < 0.5) {
			r.put(r.feet, Blocks.ENCHANTING_TABLE.defaultBlockState());
		} else if (r.iu % 5 == 0 && Math.abs(r.av - 2.5) < 0.5) {
			r.put(r.feet, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			r.put(r.feet + 1, candles(2, true));
		}
	}

	/** A shrine: calcite and amethyst, an altar of crying obsidian with candles and a rod of light, crystals by the walls. */
	static void shrine(Room r) {
		r.put(r.floor(), r.av < 1.0 ? AMETHYST : Math.floorMod(r.iu, 3) == 0 ? Blocks.CHISELED_TUFF.defaultBlockState()
			: Blocks.CALCITE.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (r.depth - r.u < 3.0 && r.av < 2.5) {
			r.put(r.feet, r.av < 0.5 ? CRYING : AMETHYST);
			r.put(r.feet + 1, r.av < 0.5 ? Blocks.END_ROD.defaultBlockState()
				: Blocks.DYED_CANDLE.purple().defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
		} else if (r.side && r.iu % 3 == 0) {
			r.put(r.feet, Blocks.AMETHYST_CLUSTER.defaultBlockState());
		} else if (r.side && r.iu % 3 == 1) {
			r.put(r.feet, Blocks.DYED_CANDLE.magenta().defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
		} else if (r.side) {
			r.fill(r.feet + 1, r.feet + 3, Blocks.STAINED_GLASS.magenta().defaultBlockState());
		} else if (r.iu % 4 == 2 && r.av >= 1.5 && r.av < 3.5 && r.u < r.depth - 4) {
			// Kneelers in rows before the altar.
			r.put(r.feet, Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.in));
		}
	}

	/** A training hall: targets on the far wall, straw dummies in rows, a rack of practice swords (barrels). */
	static void training(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (r.outer && r.iv % 3 == 0) {
			r.put(r.feet, Blocks.HAY_BLOCK.defaultBlockState());
			r.put(r.feet + 1, Blocks.TARGET.defaultBlockState());
		} else if (r.iu % 4 == 2 && r.iv % 4 == 0 && r.av < r.halfW - 1.5 && r.u > 2.5 && r.depth - r.u > 2.5) {
			// (A skull for a head, not a pumpkin: a pumpkin on placement looks for an iron golem to make.)
			r.put(r.feet, Blocks.HAY_BLOCK.defaultBlockState());
			r.put(r.feet + 1, Blocks.HAY_BLOCK.defaultBlockState());
			r.put(r.feet + 2, Blocks.SKELETON_SKULL.defaultBlockState());
		} else if (r.side && r.iu % 5 == 1) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
		} else if (r.inner && r.iv == 3) {
			r.sign(r.feet + 2, r.out, DyeColor.BLACK, false, "RULES OF THE", "FIELD: no blow", "after the horn,", "no bout unfought");
		}
	}

	/** A strongroom: gilded floor, heaps of gold, decorated pots, iron bars. */
	static void strongroom(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? GILDED : BLACKSTONE);
		if (r.doorway) {
			return;
		}
		int heap = r.roll(5) % 10;
		if (r.outer || r.side && heap < 5) {
			r.put(r.feet, heap < 3 ? Blocks.GOLD_BLOCK.defaultBlockState() : heap < 6 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState()
				: Blocks.BARREL.defaultBlockState());
			if (heap < 2 && r.outer) {
				r.put(r.feet + 1, Blocks.GOLD_BLOCK.defaultBlockState());
			}
		} else if (r.side) {
			r.fill(r.feet, r.feet + 2, Blocks.IRON_BARS.defaultBlockState());
		} else if (r.iu % 3 == 1 && Math.abs(r.av - r.halfW / 2) < 0.5) {
			r.put(r.feet, Blocks.DECORATED_POT.defaultBlockState());
		}
	}

	/** An alchemist's room: brewing stands on benches, cauldrons of water, potted fungus, pots. */
	static void alchemist(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu, 3) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : TILES);
		if (r.doorway) {
			return;
		}
		if (r.side) {
			r.put(r.feet, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			r.put(r.feet + 1, r.iu % 3 == 0 ? Blocks.BREWING_STAND.defaultBlockState() : r.iu % 3 == 1 ? Blocks.POTTED_CRIMSON_FUNGUS.defaultBlockState()
				: candles(1, true));
		} else if (r.iu % 4 == 2 && r.av < 0.5) {
			r.put(r.feet, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		} else if (r.outer && r.iv % 2 == 0) {
			r.put(r.feet, Blocks.DECORATED_POT.defaultBlockState());
		} else if (r.outer) {
			r.put(r.feet, Blocks.BOOKSHELF.defaultBlockState());
			r.put(r.feet + 1, Blocks.POTTED_WARPED_FUNGUS.defaultBlockState());
		}
	}

	/** A lounge: carpets, sofas along the walls, potted azaleas, a jukebox in the middle. */
	static void lounge(Room r) {
		r.put(r.floor(), Blocks.SPRUCE_PLANKS.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (r.side && r.iu % 5 != 0) {
			r.put(r.feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.towardSide));
		} else if (r.side) {
			r.put(r.feet, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		} else if (r.mid(0.5) && r.av < 0.5) {
			r.put(r.feet, Blocks.JUKEBOX.defaultBlockState());
		} else {
			r.put(r.feet, Math.floorMod(r.iu / 2 + r.iv / 2, 2) == 0 ? Blocks.CARPET.purple().defaultBlockState() : Blocks.CARPET.magenta().defaultBlockState());
		}
	}

	/** A storeroom: barrels and hay along the walls, crates stacked in the middle, furnaces and smokers on the far wall. */
	static void storeroom(Room r) {
		r.put(r.floor(), Blocks.SPRUCE_PLANKS.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (r.outer) {
			r.put(r.feet, r.iv % 2 == 0 ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
		} else if (r.side) {
			r.put(r.feet, r.iu % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
			if (r.iu % 2 == 0) {
				r.put(r.feet + 1, Blocks.BARREL.defaultBlockState());
			}
		} else if (r.iu % 6 == 3 && Math.floorMod(r.iv, 5) <= 1 && r.av < r.halfW - 2) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
			r.put(r.feet + 1, Math.floorMod(r.iv, 5) == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
		}
	}

	/** Baths: a long sunken-looking pool lit from beneath, prismarine all round, benches and ferns. */
	static void baths(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
		if (r.doorway) {
			return;
		}
		boolean pool = r.av < r.halfW - 2.0 && r.u > 3 && r.depth - r.u > 3;
		boolean rim = !pool && r.av < r.halfW - 1.0 && r.u > 2 && r.depth - r.u > 2;
		if (pool) {
			r.put(r.floor(), r.iu % 3 == 0 ? SEA_LANTERN : Blocks.PRISMARINE_BRICKS.defaultBlockState());
			r.put(r.feet, WATER);
		} else if (rim) {
			r.put(r.feet, Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		} else if (r.side && r.iu % 4 == 2) {
			r.put(r.feet, Blocks.POTTED_FERN.defaultBlockState());
		} else if (r.side) {
			r.put(r.feet, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.towardSide));
		}
	}

	/** A garden hall: moss underfoot, azaleas in beds, hedges of blossom, spore blossoms hanging from the beams, a fountain. */
	static void garden(Room r) {
		r.put(r.floor(), r.av < 1.0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
		if (r.flatCeiling() && r.iu % 3 == 1 && Math.floorMod(r.iv, 3) == 1) {
			r.put(r.ceiling - 1, Blocks.SPORE_BLOSSOM.defaultBlockState());
		}
		if (r.doorway) {
			return;
		}
		if (r.side) {
			r.put(r.feet, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			if (r.iu % 2 == 0) {
				r.put(r.feet + 1, Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			}
		} else if (r.mid(1.6) && r.av < 1.6) {
			// The fountain: a mossy basin round a spout.
			boolean spout = r.mid(0.5) && r.av < 0.5;
			boolean basin = r.mid(1.0) && r.av < 1.0;
			r.put(r.feet, spout ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState() : basin ? WATER : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			if (spout) {
				r.put(r.feet + 1, SEA_LANTERN);
			}
		} else if (r.av >= 1.0 && r.iu % 3 == 0 && r.roll(7) % 3 != 0) {
			r.put(r.feet, r.roll(9) % 2 == 0 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : Blocks.AZALEA.defaultBlockState());
		}
	}

	/** A music hall: a raised stage with note blocks, a jukebox and a bell, benches facing it, wool hangings. */
	static void music(Room r) {
		boolean stage = r.depth - r.u < 4.5;
		r.put(r.floor(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (stage) {
			r.put(r.feet, Blocks.SPRUCE_SLAB.defaultBlockState());
			if (r.outer && r.iv % 2 == 0) {
				r.put(r.feet + 1, Blocks.NOTE_BLOCK.defaultBlockState());
			} else if (r.iv == 0 && r.depth - r.u < 2.5 && r.depth - r.u >= 1.5) {
				r.put(r.feet + 1, Blocks.BELL.defaultBlockState());
			} else if (r.iv == 2 && r.depth - r.u < 2.5) {
				r.put(r.feet + 1, Blocks.JUKEBOX.defaultBlockState());
			}
		} else if (r.side) {
			r.fill(r.feet + 1, Math.min(r.ceiling - 1, r.feet + 4), r.iu % 2 == 0 ? Blocks.WOOL.red().defaultBlockState() : Blocks.WOOL.black().defaultBlockState());
		} else if (r.iu % 3 == 0 && r.av >= 1.0 && r.u > 2.5) {
			r.put(r.feet, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.in));
		}
	}

	/** A crypt: champions laid in stone, skulls, candles, cobwebs in the corners, soul light. */
	static void crypt(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu, 4) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : TILES);
		if (r.flatCeiling() && r.side && r.iu % 5 == 2) {
			r.put(r.ceiling - 1, Blocks.COBWEB.defaultBlockState());
		}
		if (r.doorway) {
			return;
		}
		boolean tombRow = Math.abs(r.av - r.halfW / 2) < 1.0 && r.u > 2 && r.depth - r.u > 2;
		if (tombRow && r.iu % 4 != 0) {
			r.put(r.feet, BLACK_BRICKS);
			if (r.iu % 4 == 2) {
				r.put(r.feet + 1, candles(1 + r.roll(4) % 3, true));
			}
		} else if (tombRow) {
			r.put(r.feet, Blocks.SKELETON_SKULL.defaultBlockState());
		} else if (r.outer && r.iv == 0) {
			r.sign(r.feet + 2, r.in, DyeColor.WHITE, true, "HERE LIE", "CHAMPIONS OF", "THE CROWN", "who fought on");
		} else if (r.outer && r.av < 2.5) {
			r.put(r.feet, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			r.put(r.feet + 1, Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/** Barracks: rows of cots with blankets, foot lockers, a weapon rack and the duty board. */
	static void barracks(Room r) {
		r.put(r.floor(), Blocks.SPRUCE_PLANKS.defaultBlockState());
		if (r.doorway) {
			return;
		}
		boolean cot = r.halfW - r.av < 3.0 && r.halfW - r.av >= 1.0 && r.iu % 3 != 0 && r.u > 2 && r.depth - r.u > 2;
		if (cot) {
			r.put(r.feet, Blocks.WOOL.white().defaultBlockState());
			r.put(r.feet + 1, (r.iu % 3 == 1 ? Blocks.CARPET.red() : Blocks.CARPET.blue()).defaultBlockState());
		} else if (r.side && r.iu % 3 == 0) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
		} else if (r.outer && r.iv % 2 == 0) {
			r.put(r.feet, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			r.put(r.feet + 1, Blocks.IRON_BARS.defaultBlockState());
		} else if (r.inner && r.iv == 3) {
			r.sign(r.feet + 2, r.out, DyeColor.BLACK, false, "DUTY: watch the", "stands at night.", "Report any", "knocking.");
		}
	}

	/** A map room: the colosseum itself laid out in the floor, cartography tables round it, banners. */
	static void mapRoom(Room r) {
		double mx = r.u - r.depth / 2;
		double rr = Math.hypot(mx, r.v) * 1.6;
		BlockState mosaic = rr < 1 ? Blocks.GOLD_BLOCK.defaultBlockState() : rr < 2.5 ? Blocks.CONCRETE.purple().defaultBlockState()
			: rr < 3.5 ? Blocks.CONCRETE.black().defaultBlockState() : rr < 6 ? Blocks.CONCRETE.gray().defaultBlockState()
			: rr < 7 ? Blocks.CONCRETE.lightBlue().defaultBlockState() : rr < 8 ? Blocks.CONCRETE.pink().defaultBlockState()
			: Blocks.DARK_OAK_PLANKS.defaultBlockState();
		r.put(r.floor(), mosaic);
		if (r.doorway) {
			return;
		}
		if (r.side && r.iu % 3 == 1) {
			r.put(r.feet, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		} else if (r.side && r.iu % 3 == 2) {
			r.put(r.feet, Blocks.LOOM.defaultBlockState());
		} else if (r.outer && r.iv % 2 == 1) {
			r.put(r.feet + 3, Blocks.WALL_BANNER.lightBlue().defaultBlockState().setValue(WallBannerBlock.FACING, r.in));
		} else if (r.outer && r.iv == 0) {
			r.sign(r.feet + 2, r.in, DyeColor.WHITE, true, "THE BOWL", "AS BUILT", "Year 4 of", "the Crown");
		}
	}

	/** A kitchen: ovens and smokers, a long counter, cauldrons, sacks and barrels. */
	static void kitchen(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? Blocks.BRICKS.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState());
		if (r.doorway) {
			return;
		}
		if (r.outer) {
			r.put(r.feet, r.iv % 3 == 0 ? Blocks.BLAST_FURNACE.defaultBlockState() : r.iv % 3 == 1 ? Blocks.SMOKER.defaultBlockState()
				: Blocks.FURNACE.defaultBlockState());
		} else if (r.av < 0.5 && r.u > 3 && r.depth - r.u > 3) {
			r.put(r.feet, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			if (r.iu % 4 == 0) {
				r.put(r.feet + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
			}
		} else if (r.side) {
			r.put(r.feet, r.iu % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
		}
	}

	/** Blocks that need their neighbours (placed after everything solid). */
	static boolean attached(Block b) {
		return b instanceof CandleBlock || b instanceof net.minecraft.world.level.block.CarpetBlock || b instanceof net.minecraft.world.level.block.SignBlock
			|| b instanceof net.minecraft.world.level.block.TorchBlock || b instanceof net.minecraft.world.level.block.FlowerPotBlock
			|| b == Blocks.SPORE_BLOSSOM || b == Blocks.BELL || b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA || b == Blocks.COBWEB;
	}
}

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
		double sectorDeg = ((deg % SECTOR) + SECTOR) % SECTOR;
		double v = (sectorDeg - SECTOR / 2) * Math.PI / 180 * d;
		double halfW = SECTOR / 2 * Math.PI / 180 * d;
		int corridor = band(CORRIDORS, d);
		int rooms = band(ROOMS, d);
		int sector = sector(deg);
		boolean radialWall = rooms >= 0 && halfW - Math.abs(v) < 0.5;
		boolean stair = rooms >= 0 && StairHall.is(rooms, sector);
		double ru = rooms >= 0 ? d - ROOMS[rooms][0] : 0;
		StairHall.Part part = stair ? StairHall.part(rooms, ru) : null;
		for (int k = 0; storey(d, k); k++) {
			int feet = F + STOREY * k;
			int ceiling = storey(d, k + 1) ? feet + STOREY - 1 : u;
			if (rooms >= 0 && k > TOP[rooms]) {
				// Above the last level the corridors reach: solid, so no hall is ever shut in.
				fill(c, feet - 1, u - 1, TILES);
				break;
			}
			boolean inStair = stair && !radialWall && k <= StairHall.top(rooms);
			if (inStair && part == StairHall.Part.CORE) {
				if (k == 0) {
					StairHall.core(c, dx, dz, rooms, sector, ru, v);
				}
				continue;
			}
			if (inStair && part == StairHall.Part.BEYOND && rooms != 1) {
				if (k == 0) {
					// Solid stone behind the stair (with the secret vaults in it).
					fill(c, F, F + STOREY * (StairHall.top(rooms) + 1) - 2, BRICKS);
					Vaults.build(c, dx, dz, rooms, sector, ru, v, halfW - 0.5);
				}
				continue;
			}
			if (k > 0) {
				put(c, feet - 1, k % 2 == 0 ? TILES : POLISHED);
			}
			if (corridor < 0 && rooms < 0) {
				// A ring wall: a door at the middle of each sector where there is floor on both sides (and the hall
				// beyond wants one), windows either side of it.
				fill(c, feet, ceiling - 1, BRICKS);
				int outside = band(ROOMS, d + 1);
				int inside = band(ROOMS, d - 1);
				boolean open = storey(d - 1, k) && storey(d + 1, k) && (outside < 0 || door(outside, sector, k, true))
					&& (inside < 0 || door(inside, sector, k, false));
				if (Math.abs(v) <= 1.0 && open) {
					fill(c, feet, feet + 2, AIR);
					put(c, feet + 3, CHISELED);
				} else if (open && Math.abs(v) >= 2.2 && Math.abs(v) <= 3.2 && ceiling - feet > 4) {
					fill(c, feet + 1, feet + 3, PURPLE_GLASS);
				}
			} else if (radialWall) {
				// The walls between halls: solid, so each hall opens only onto its corridors.
				fill(c, feet, ceiling - 1, BRICKS);
			} else if (corridor >= 0) {
				corridor(c, dx, dz, d, deg, corridor, feet, ceiling, k, sector, v);
			} else if (inStair && part == StairHall.Part.LOBBY) {
				StairHall.lobby(new Room(c, dx, dz, ru, ROOMS[rooms][1] - ROOMS[rooms][0], v, halfW - 0.5, feet, ceiling, sector, rooms, k), rooms);
			} else if (inStair && part == StairHall.Part.END) {
				StairHall.end(c, feet, ceiling);
				Vaults.door(c, rooms, sector, k, v);
			} else if (inStair) {
				// The annex behind the Second Gallery's stair, opening onto the Outer Ring.
				double depth = ROOMS[rooms][1] - ROOMS[rooms][0] - ANNEX;
				RoomStyles.room(new Room(c, dx, dz, ru - ANNEX, depth, v, halfW - 0.5, feet, ceiling, sector, rooms, k), true);
			} else {
				Room r = new Room(c, dx, dz, ru, ROOMS[rooms][1] - ROOMS[rooms][0], v, halfW - 0.5, feet, ceiling, sector, rooms, k);
				int landmark = Landmarks.at(rooms, sector, k);
				if (landmark >= 0) {
					Landmarks.room(r, landmark);
				} else {
					RoomStyles.room(r, false);
				}
			}
		}
	}

	/** The top level of each Gallery: the last one its corridors reach (the Outer Ring stops at level 9). */
	static final int[] TOP = {3, 8, 8};
	/** Where the annex behind the Second Gallery's stair begins, from the Gallery's inner wall. */
	static final double ANNEX = 12;

	/** Whether the hall in that Gallery, sector and storey has a door on its inner (or outer) side. */
	static boolean door(int band, int sector, int k, boolean innerSide) {
		if (StairHall.is(band, sector)) {
			return StairHall.door(band, k, innerSide);
		}
		// (The Inner Ring has only a ground floor: above it, a sliver of ledge at most. And the Cells open only
		// onto the Middle Ring, so you come in facing the stair down, not hemmed in behind its well.)
		return !(band == 0 && innerSide && (k > 0 || Landmarks.at(band, sector, k) == Landmarks.CELLS));
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
						ring == 0 ? "beyond" : ring == 1 ? "I and II" : "II and III", "stairs: diagonals");
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
		if (Math.abs(v) < 0.5 && beyond >= 0 && storey(there, k) && storey(d, k) && k <= TOP[beyond] && door(beyond, sector, k, outerEdge)) {
			String[] name = name(beyond, sector, k, outerEdge);
			wallSign(c, dx, feet + 3, dz, away, DyeColor.YELLOW, true, "Sector " + (sector + 1), name[0], name[1],
				"Gallery " + ColosseumLore.ROMAN[beyond] + ", L" + (k + 1));
		}
		// Halfway between doors in the Middle and Outer Rings: the way to the nearest stair hall.
		double halfW = SECTOR / 2 * Math.PI / 180 * d;
		if (ring > 0 && outerEdge && v > 0 && halfW - v < 0.6 && halfW - v >= 0 && ceiling - feet > 3) {
			int best = 0;
			int sunwise = 0;
			for (int s : StairHall.SECTORS) {
				int ahead = Math.floorMod(s - sector, 48);
				int behind = Math.floorMod(sector - s, 48);
				if (best == 0 || Math.min(ahead, behind) < best) {
					best = Math.max(1, Math.min(ahead, behind));
					sunwise = ahead <= behind ? s : -s - 1;
				}
			}
			int target = sunwise >= 0 ? sunwise : -sunwise - 1;
			// Read facing the outer wall, sunwise is to the right.
			String arrow = sunwise >= 0 ? "STAIRS \u2192" : "\u2190 STAIRS";
			if (target == sector) {
				arrow = "STAIRS HERE";
			}
			wallSign(c, dx, feet + 2, dz, away, DyeColor.WHITE, true, arrow, StairHall.name(target)[0], best <= 1 ? "next sector" : best + " sectors", "");
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
			this.doorway = av <= 1.6 && (u < 2.0 || depth - u < 2.0);
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

	/** A hall's name on two lines, as on the plaque over its door on that side (a landmark's or a stair's own name). */
	static String[] name(int band, int sector, int k, boolean innerSide) {
		int landmark = Landmarks.at(band, sector, k);
		if (landmark >= 0) {
			return Landmarks.NAMES[landmark];
		}
		boolean stair = StairHall.is(band, sector) && k <= StairHall.top(band);
		if (stair && (band != 1 || innerSide)) {
			return StairHall.name(sector);
		}
		return RoomStyles.name(band, sector, k, stair);
	}

	/** Blocks that need their neighbours (placed after everything solid). */
	static boolean attached(Block b) {
		return b instanceof net.minecraft.world.level.block.AbstractCandleBlock || b instanceof net.minecraft.world.level.block.CarpetBlock
			|| b instanceof net.minecraft.world.level.block.SignBlock || b instanceof net.minecraft.world.level.block.BaseTorchBlock
			|| b instanceof net.minecraft.world.level.block.FlowerPotBlock || b instanceof net.minecraft.world.level.block.VegetationBlock
			|| b instanceof net.minecraft.world.level.block.LadderBlock || b instanceof net.minecraft.world.level.block.VineBlock
			|| b instanceof net.minecraft.world.level.block.MultifaceBlock || b instanceof net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
			|| b instanceof net.minecraft.world.level.block.ChainBlock || b instanceof net.minecraft.world.level.block.CakeBlock
			|| b instanceof net.minecraft.world.level.block.TripWireHookBlock || b instanceof net.minecraft.world.level.block.HangingRootsBlock
			|| b instanceof net.minecraft.world.level.block.BasePressurePlateBlock || b instanceof net.minecraft.world.level.block.AmethystClusterBlock
			|| b instanceof net.minecraft.world.level.block.RodBlock || b instanceof net.minecraft.world.level.block.GrowingPlantBlock
			|| b == Blocks.SPORE_BLOSSOM || b == Blocks.BELL || b == Blocks.COBWEB;
	}
}

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
 * <p>Three ring corridors run all the way round at ground level (the Inner Ring behind the podium, the Middle and
 * Outer Rings under the concourses), and between them lie the three Galleries, each of eight great halls (see
 * {@link GrandHalls}): twenty-four rooms in all, each a single great space with its own story. Each gate's passage
 * opens, under the first two tiers, into a great atrium where the Grand Stairs climb in two flights and a bridge to
 * the first concourse.
 */
final class ColosseumInterior {
	/** Old storey height (still used to size the space under the seats). */
	static final int STOREY = 8;
	/** Sectors, in degrees (48 of them), for whatever still counts in them. */
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
		if (band(ROOMS, d) >= 0) {
			GrandHalls.build(c, dx, dz, d, deg, u);
			return;
		}
		int ring = band(CORRIDORS, d);
		if (ring >= 0) {
			corridor(c, dx, dz, d, deg, ring, u);
			return;
		}
		ringWall(c, dx, dz, d, deg, u);
	}

	/** The corridors' ceiling (above it, solid up to the seats). */
	static final int CORRIDOR_TOP = F + 12;

	/**
	 * A ring wall between a corridor and a Gallery: solid, with each hall's two great doors in it (a third and two thirds
	 * along the hall), purple windows between them.
	 */
	private static void ringWall(BlockState[] c, int dx, int dz, double d, double deg, int u) {
		fill(c, F, u - 1, BRICKS);
		int hall = GrandHalls.at(band(ROOMS, d + 1) >= 0 ? d + 1 : d - 1, deg);
		boolean corridorBeyond = band(CORRIDORS, d + 1) >= 0 || band(CORRIDORS, d - 1) >= 0;
		if (hall < 0 || !corridorBeyond) {
			return;
		}
		double off = GrandHalls.doorOffset(hall, d, deg);
		int top = Math.min(u - 2, F + 5);
		if (off <= 1.6) {
			fill(c, F, top - 1, AIR);
			put(c, top, CHISELED);
			put(c, top + 1, GILDED);
		} else if (off >= 3.5 && off <= 5.5 && u - F > 7) {
			fill(c, F + 1, F + 4, PURPLE_GLASS);
		}
	}

	/**
	 * A ring corridor: one grand promenade at ground level all the way round (twelve high under the outer stands, as high
	 * as the seats allow under the first), lit down the middle, with benches, banners and torches along its walls and
	 * the name of the hall beyond over each of its doors.
	 */
	private static void corridor(BlockState[] c, int dx, int dz, double d, double deg, int ring, int u) {
		double[] band = CORRIDORS[ring];
		double mid = (band[0] + band[1]) / 2;
		int top = Math.min(u - 1, CORRIDOR_TOP);
		if (top < u - 1) {
			fill(c, top + 1, u - 1, TILES);
		}
		long arc = Math.round(Math.toRadians(deg) * d);
		boolean centre = Math.abs(d - mid) < 0.5;
		put(c, F - 1, centre && Math.floorMod(arc, 4) == 0 ? CHISELED : Math.floorMod(arc, 2) == 0 ? POLISHED : TILES);
		put(c, top + 1, Math.abs(d - mid) < 1.5 && Math.floorMod(arc, 3) == 0 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : TILES);
		if (centre && Math.floorMod(arc, 8) == 0) {
			if (top - F > 8) {
				fill(c, F + 7, top, CHAIN);
				put(c, F + 6, LANTERN.setValue(LanternBlock.HANGING, true));
			} else {
				put(c, top, LANTERN.setValue(LanternBlock.HANGING, true));
			}
		}
		if (centre && Math.floorMod(arc, 8) == 4) {
			put(c, F - 1, SEA_LANTERN);
		}
		boolean innerEdge = d - band[0] < 1.0;
		boolean outerEdge = band[1] - d < 1.0;
		if (!innerEdge && !outerEdge) {
			return;
		}
		Direction away = innerEdge ? outward(dx, dz) : outward(-dx, -dz);
		double beyond = innerEdge ? band[0] - 2 : band[1] + 2;
		int hall = GrandHalls.at(beyond, deg);
		double off = hall >= 0 ? GrandHalls.doorOffset(hall, beyond, deg) : 99;
		if (hall >= 0 && off < 0.5) {
			String[] name = GrandHalls.name(hall);
			wallSign(c, dx, Math.min(top - 1, F + 6), dz, away, DyeColor.YELLOW, true, name[0], name[1], "Gallery " + ColosseumLore.ROMAN[hall / 8], "");
			return;
		}
		if (off < 2.5) {
			return;
		}
		int m = Math.floorMod((int) arc, 12);
		if (m == 6) {
			Block banner = Math.floorMod(arc / 12, 2) == 0 ? Blocks.WALL_BANNER.purple() : Blocks.WALL_BANNER.cyan();
			put(c, F + 4, banner.defaultBlockState().setValue(WallBannerBlock.FACING, away));
		} else if (m >= 9 && m <= 10) {
			put(c, F, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, away.getOpposite()));
		} else if (m == 2) {
			put(c, F + 2, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, away));
		} else if (m == 0 && top - F > 6) {
			put(c, F + 5, SEA_LANTERN);
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

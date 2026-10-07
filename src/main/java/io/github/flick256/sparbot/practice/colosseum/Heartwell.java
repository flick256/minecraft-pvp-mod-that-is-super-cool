package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_WALL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHAIN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CRYING;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.GILDED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.HANGING_SOUL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.POLISHED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPUR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.S;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.TILES;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.deep;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.offStep;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The end of the trail, dug out of the rock under the field:
 * <ul>
 * <li><b>The Lower Door</b>: a wide stair in the floor of the Cells, going down under the Inner Ring and the
 * podium. It is lit and bannered all the way, with words on the walls for whoever goes down.</li>
 * <li><b>The Hall of the Fallen</b>: a long hall with plaques for those who went down and lost, the Roll of the
 * Fallen on a lectern, the champions' plaque across from it, and braziers either side of the great arch.</li>
 * <li>Through the great arch: the Brink, over the Deep (see {@link TheDeep}).</li>
 * </ul>
 */
final class Heartwell {
	/** The way down runs along this bearing, through the Cells (see {@link GrandHalls}). */
	static final double BEARING = Math.toRadians(251.25);
	static final double COS = Math.cos(BEARING);
	static final double SIN = Math.sin(BEARING);
	/** The arena's floor block, and the height people stand at down there. */
	static final int FLOOR = S - 28;
	static final int FEET = FLOOR + 1;
	/** The arena's radius (inside its wall), and the wall's outer edge. */
	static final double RADIUS = 22;
	static final double SHELL = 25;
	/** The stair runs from the Cells' floor (along 70) down to the hall (along 42), a block down for each along. */
	static final int STAIR_TOP = 70;
	static final int STAIR_FOOT = STAIR_TOP - (F - FEET);
	/** The great arch at the end of the Hall of the Fallen (along 26 to 30); the hall runs on to the stair. */
	static final int ARCH = 26;
	static final int HALL = ARCH + 4;
	/** Where the way into the arena is sealed during a fight (along), and the throne (along, across the arena). */
	static final int SEAL = 24;
	static final double THRONE = 20;

	private Heartwell() {
	}

	/** Whether the column could hold any of it. */
	static boolean near(double d) {
		return d <= STAIR_TOP + 4;
	}

	/** The top of the arena's air at distance {@code d} from the middle: walls twelve high, a dome rising to twenty. */
	static int dome(double d) {
		double t = Math.min(1, d / (RADIUS + 0.5));
		return FEET + 11 + (int) Math.round(8 * Math.sqrt(1 - t * t));
	}

	/** The height people stand at on the stair, along {@code fa}. */
	static int stairFeet(int fa) {
		return FEET + (fa - STAIR_FOOT);
	}

	static void build(BlockState[] c, int dx, int dz, double d, double deg) {
		double a = dx * COS + dz * SIN;
		double lat = -dx * SIN + dz * COS;
		if (a > RADIUS - 1 && a < STAIR_TOP + 1 && Math.abs(lat) <= 5.5) {
			way(c, dx, dz, a, lat);
		}
	}

	private static BlockState stair(Block b, Direction f) {
		return b.defaultBlockState().setValue(StairBlock.FACING, f);
	}

	private static final String[][] WALL_WORDS = {{"THE LOWER", "DOOR", "to the", "Heartwell"}, {"TWO HUNDRED", "WENT DOWN", "THIS STAIR.", ""},
		{"CHECK YOUR", "GEAR. EAT.", "BREATHE.", ""}, {"HE IS NOT A", "MAN ANY MORE.", "WATCH HIS", "BLADE."}, {"THE HALL OF", "THE FALLEN", "is ahead", ""}};

	/** The stair down, the Hall of the Fallen, the arch and the way through the arena's wall. */
	private static void way(BlockState[] c, int dx, int dz, double a, double lat) {
		int fa = (int) Math.floor(a);
		double p = Math.abs(lat);
		Direction up = outward(COS, SIN);
		Direction toLine = lat > 0 ? outward(SIN, -COS) : outward(-SIN, COS);
		if (fa >= STAIR_FOOT && fa < STAIR_TOP) {
			if (p <= 3.5) {
				stairway(c, dx, dz, fa, p, lat, toLine, up);
			}
		} else if (fa >= HALL && fa < STAIR_FOOT) {
			hall(c, dx, dz, fa, p, lat, toLine, up);
		} else if (fa >= ARCH && fa < HALL) {
			arch(c, fa, p);
		} else if (fa >= (int) RADIUS && fa < ARCH && p < 2.5) {
			// Through the arena's wall.
			deep(c, FEET, FEET + 5, AIR);
			put(c, FLOOR, p < 1 ? PURPUR : BLACK_BRICKS);
			put(c, FEET + 6, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
		}
	}

	/** One column of the stair: steps with a purpur runner, lit ceiling, walls with a gilded course, and a rail round its well in the Cells. */
	private static void stairway(BlockState[] c, int dx, int dz, int fa, double p, double lat, Direction toLine, Direction up) {
		int feet = stairFeet(fa);
		boolean open = feet + 4 >= S;
		boolean edge = !open && stairFeet(fa + 1) + 4 >= S;
		int roof = open ? S : feet + 5;
		if (p < 2.5) {
			deep(c, feet, roof - 1, AIR);
			if (open) {
				put(c, S, AIR);
			}
			put(c, feet - 1, fa > STAIR_FOOT ? stair(p < 1 ? Blocks.PURPUR_STAIRS : Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, up) : p < 1 ? PURPUR : BLACK_BRICKS);
			put(c, feet - 2, BLACK_BRICKS);
			if (!open) {
				put(c, roof, Math.floorMod(fa, 3) == 0 && p < 0.5 ? SEA_LANTERN : TILES);
				if (p < 0.5 && Math.floorMod(fa, 6) == 4) {
					put(c, roof - 1, HANGING_SOUL);
				}
			}
			if (edge) {
				// A rail in the Cells across the low end of the well.
				put(c, S + 1, BLACK_WALL);
			}
			int say = fa == STAIR_TOP - 7 ? 0 : fa == STAIR_TOP - 12 ? 1 : fa == STAIR_TOP - 17 ? 2 : fa == STAIR_TOP - 22 ? 3 : fa == STAIR_FOOT + 2 ? 4 : -1;
			if (say >= 0 && lat > 1.5) {
				ColosseumInterior.wallSign(c, dx, feet + 1, dz, toLine, DyeColor.WHITE, true, WALL_WORDS[say]);
			} else if (p >= 1.5 && Math.floorMod(fa, 6) == 1) {
				put(c, feet + 2, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, toLine));
			}
			return;
		}
		deep(c, feet - 2, open ? S - 1 : roof, BRICKS);
		put(c, feet + 3, Math.floorMod(fa, 4) == 1 ? GILDED : TILES);
		if (open) {
			// A rail round the stairwell in the Cells' floor.
			put(c, S, POLISHED);
			put(c, S + 1, BLACK_WALL);
		}
	}

	private static final String[][] FALLEN = {{"AUREL", "THE SWIFT", "412 - 38", "lost below"}, {"BRENNA", "IRONHAND", "377 - 51", "lost below"},
		{"DORN OF", "EMBERS", "290 - 12", "lost below"}, {"ILSA", "MOONWARD", "515 - 60", "lost below"}, {"KAEL", "TWO-BLADES", "333 - 41", "lost below"},
		{"MIRA THE", "BRIGHT", "268 - 9", "lost below"}, {"OLD MARR", "", "701 - 140", "lost below"}, {"SABLE", "NIGHT", "199 - 3", "lost below"},
		{"TOBIN", "FARSHOT", "251 - 30", "lost below"}, {"YSOLDE", "OF THE ROSE", "450 - 22", "lost below"}};

	/** The Hall of the Fallen: plaques down both walls, banners, soul light, a runner to the arch, the Roll on a lectern, the champions' plaque. */
	private static void hall(BlockState[] c, int dx, int dz, int fa, double p, double lat, Direction toLine, Direction up) {
		int top = FEET + 7;
		if (p > 5.5) {
			return;
		}
		if (p > 4.5) {
			deep(c, FLOOR - 1, top + 1, BRICKS);
			if (Math.floorMod(fa, 3) == 0) {
				deep(c, FEET, top, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			}
			return;
		}
		deep(c, FEET, top, AIR);
		put(c, FLOOR, p < 1 ? PURPUR : Math.floorMod(fa, 2) == 0 ? BLACK_BRICKS : POLISHED);
		put(c, FLOOR - 1, BRICKS);
		put(c, top + 1, p < 0.5 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : Math.floorMod(fa, 3) == 0 ? BLACK_BRICKS : TILES);
		if (p < 1) {
			put(c, FEET, Blocks.CARPET.purple().defaultBlockState());
		}
		if (p < 0.5 && Math.floorMod(fa, 4) == 1) {
			put(c, top, CHAIN);
			put(c, top - 1, HANGING_SOUL);
		}
		if (fa == HALL && p < 0.5) {
			// The name over the arch, on its crown.
			ColosseumInterior.wallSign(c, dx, FEET + 6, dz, up, DyeColor.PURPLE, true, "THE BRINK", "", "leap into the", "Well");
		}
		if (p <= 3.5) {
			return;
		}
		int i = fa - HALL;
		if (fa == STAIR_FOOT - 2 && lat < 0) {
			ColosseumLore.lectern(c, dx, FEET, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toLine)
				.setValue(LecternBlock.HAS_BOOK, true), ColosseumLore.FALLEN);
		} else if (fa == STAIR_FOOT - 2 && lat > 0) {
			// Across from the Roll: the champions who came back up (none yet, until someone does).
			ColosseumLore.champions(c, dx, FEET + 2, dz, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, toLine));
			put(c, FEET + 4, Blocks.WALL_BANNER.yellow().defaultBlockState().setValue(WallBannerBlock.FACING, toLine));
		} else if (Math.floorMod(i, 3) == 1) {
			int n = Math.floorMod(i / 3 * 2 + (lat > 0 ? 1 : 0), FALLEN.length);
			ColosseumInterior.wallSign(c, dx, FEET + 2, dz, toLine, DyeColor.WHITE, true, FALLEN[n]);
			put(c, FEET + 4, Blocks.WALL_BANNER.white().defaultBlockState().setValue(WallBannerBlock.FACING, toLine));
		} else if (Math.floorMod(i, 3) == 2) {
			put(c, FEET, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			put(c, FEET + 1, Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/** The great arch at the end of the hall: braziers either side, gold in its crown. */
	private static void arch(BlockState[] c, int fa, double p) {
		if (p > 5.5) {
			return;
		}
		int top = FEET + 7;
		if (p < 2.5) {
			deep(c, FEET, FEET + 5, AIR);
			put(c, FLOOR, p < 1 ? PURPUR : GILDED);
			put(c, FLOOR - 1, BRICKS);
			deep(c, FEET + 6, top + 1, p < 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			return;
		}
		boolean brazier = fa == HALL - 1 && p < 4.5;
		deep(c, FLOOR - 1, top + 1, BLACK_BRICKS);
		if (brazier) {
			put(c, FEET, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
			deep(c, FEET + 1, top, AIR);
		} else if (fa == HALL - 1) {
			put(c, FEET + 3, AMETHYST);
		}
	}
}

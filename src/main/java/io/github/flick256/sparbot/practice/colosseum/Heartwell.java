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
 * <li><b>The Heartwell</b>: a round arena forty-four across under a dome, right under the compass rose. It has a
 * ring of eight pillars to fight round, the heart of the star hung over a seal in the middle, and Vaelor's
 * throne on a dais across from the arch. Stepping in starts the fight (see {@link VaelorFight}).</li>
 * </ul>
 */
final class Heartwell {
	/** The way down runs along this bearing, from the middle of the Cells. */
	static final double BEARING = Math.toRadians(Landmarks.centre(Landmarks.CELLS));
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
		if (d <= SHELL) {
			arena(c, dx, dz, d, deg, a, lat);
		}
		if (a > RADIUS - 1 && a < STAIR_TOP + 1 && Math.abs(lat) <= 5.5) {
			way(c, dx, dz, a, lat);
		}
	}

	private static BlockState stair(Block b, Direction f) {
		return b.defaultBlockState().setValue(StairBlock.FACING, f);
	}

	private static final String[][] WALL_WORDS = {{"THE LOWER", "DOOR", "to the", "Heartwell"}, {"TWO HUNDRED", "WENT DOWN", "THIS STAIR.", ""},
		{"CHECK YOUR", "GEAR. EAT.", "BREATHE.", ""}, {"HE FIGHTS", "FAIR. SO", "DO YOU.", ""}, {"THE HALL OF", "THE FALLEN", "is ahead", ""}};

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
			ColosseumInterior.wallSign(c, dx, FEET + 6, dz, up, DyeColor.PURPLE, true, "THE HEARTWELL", "", "Vaelor the", "Unbroken");
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

	/**
	 * The arena: a patterned floor round a raised seal, eight pillars to fight round, the throne on its dais,
	 * walls with pilasters, banners and lights, and a ribbed dome with the heart of the star hung in the middle.
	 */
	private static void arena(BlockState[] c, int dx, int dz, double d, double deg, double a, double lat) {
		if (d > RADIUS) {
			// The wall: lights set in its face between pilasters.
			deep(c, FLOOR - 1, dome(RADIUS) + 2, BRICKS);
			if (d <= RADIUS + 1) {
				boolean pilaster = offStep(deg, 15, d) < 0.7;
				boolean light = offStep(deg + 7.5, 15, d) < 0.6;
				if (pilaster) {
					deep(c, FEET, dome(RADIUS), PURPUR);
					put(c, FEET + 5, Blocks.PURPUR_PILLAR.defaultBlockState());
				} else if (light) {
					put(c, FEET + 3, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
					put(c, FEET + 8, SEA_LANTERN);
				}
			}
			return;
		}
		int top = dome(d);
		deep(c, FEET, top, AIR);
		deep(c, FLOOR - 2, FLOOR - 1, BRICKS);
		deep(c, top + 1, top + 2, TILES);
		// The dome: ribs of amethyst, set with lights and crystals.
		boolean rib = offStep(deg, 30, d) < 0.6 && d > 3;
		int roll = PracticeLayout.scatter(dx, dz, 61) % 100;
		put(c, top + 1, rib ? AMETHYST : roll < 5 ? SEA_LANTERN : TILES);
		if (!rib && roll >= 5 && roll < 8 && d > 4) {
			put(c, top, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
		}
		// The floor: the seal, a gold ring, rays, bands, a ring of lights, and a dark walk round the edge.
		BlockState floor = d < 1.5 ? CRYING : d < 2.5 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : d < 3.5 ? Blocks.GOLD_BLOCK.defaultBlockState()
			: offStep(deg, 45, d) < 0.6 && d < 18 ? GILDED : Math.abs(d - 12) < 0.5 ? PURPUR
			: Math.abs(d - 18.5) < 0.5 && offStep(deg, 15, d) < 0.6 ? SEA_LANTERN : d >= 19 ? BLACK_BRICKS
			: Math.floorMod((int) Math.floor(d / 3), 2) == 0 ? POLISHED : TILES;
		put(c, FLOOR, floor);
		// The seal, raised a step, and the heart of the star hung over it.
		if (d < 2.5) {
			put(c, FEET, d < 1.5 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
			if (d < 0.6) {
				put(c, FEET + 1, Blocks.END_ROD.defaultBlockState());
				deep(c, FEET + 11, top, CHAIN);
				put(c, FEET + 10, AMETHYST);
				put(c, FEET + 9, Blocks.BUDDING_AMETHYST.defaultBlockState());
				put(c, FEET + 8, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
			} else if (d < 1.6) {
				put(c, FEET + 9, Blocks.STAINED_GLASS.purple().defaultBlockState());
			}
		}
		// Eight pillars round the middle (none in the way from the arch to the throne).
		for (int i = 0; i < 8; i++) {
			double ang = BEARING + Math.toRadians(22.5 + 45 * i);
			double r = Math.hypot(dx - Math.cos(ang) * 13, dz - Math.sin(ang) * 13);
			if (r < 1.6) {
				deep(c, FEET, top, r < 0.8 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : TILES);
				put(c, FEET, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
				if (r >= 0.8) {
					put(c, FEET + 4, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
					put(c, FEET + 8, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
				}
				return;
			}
		}
		// The throne's dais, across from the arch: two steps up, the throne, gold and lanterns behind it.
		double ta = -a;
		double tl = Math.abs(lat);
		if (ta > 14.5 && tl < 4.5) {
			Direction toThrone = outward(-COS, -SIN);
			if (ta < 15.5) {
				put(c, FEET, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, toThrone));
			} else if (ta < 16.5) {
				put(c, FEET, BLACK_BRICKS);
				put(c, FEET + 1, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, toThrone));
			} else {
				put(c, FEET, BLACK_BRICKS);
				put(c, FEET + 1, tl < 1 ? PURPUR : GILDED);
				if (ta >= THRONE - 0.5 && ta < THRONE + 0.5 && tl < 0.5) {
					put(c, FEET + 2, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, toThrone));
				} else if (ta >= THRONE + 0.5 && tl < 1.5) {
					deep(c, FEET + 2, FEET + 4, Blocks.GOLD_BLOCK.defaultBlockState());
					put(c, FEET + 5, tl < 0.5 ? CRYING : AMETHYST);
				} else if (ta >= THRONE - 0.5 && tl >= 1.5 && tl < 2.5) {
					put(c, FEET + 2, Blocks.SOUL_LANTERN.defaultBlockState());
				}
			}
		}
	}

	/** Where Vaelor stands at the start (blueprint dx, feet y, dz): on his dais before the throne. */
	static double[] throne() {
		double t = THRONE - 2.5;
		return new double[] {-COS * t, FEET + 2, -SIN * t};
	}

	/** Where a challenger stands when the way in is sealed behind them. */
	static double[] gate() {
		double t = RADIUS - 3;
		return new double[] {COS * t, FEET, SIN * t};
	}
}

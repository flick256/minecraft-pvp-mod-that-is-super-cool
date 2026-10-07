package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACKSTONE;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_WALL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHAIN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CRYING;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.GILDED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.HANGING_SOUL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.POLISHED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPUR;
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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Vaelor's prison, under the Celestial Colosseum: the Deep.
 * <ul>
 * <li><b>The Brink</b>: where the Hall of the Fallen comes out, on a ledge round the top of a shaft that drops into the
 * cavern. A bridge, the Oath Bridge, runs out to a round platform in the middle with a hole in it, the Well, under the
 * Heart of the Star hung from the dome. Going down the Well (or off any edge) is the Leap: you float down eighty
 * blocks into the arena, and the fight begins.</li>
 * <li><b>The Cavern</b>: two hundred blocks across and eighty high, its dome set with stars (froglights and sea
 * lanterns in the rock), stalactites and stalagmites, amethyst spires, lava pools under lava falls at the walls.</li>
 * <li><b>The Deep Colosseum</b>: a second colosseum, the first one, under the field: an arena seventy blocks across with
 * six broken columns to hide behind, its wall, Vaelor's throne, and the stands rising round it with the skulls of
 * the crowd that watched, inside a four-storey arcade lit from within. A lava moat goes round it.</li>
 * <li><b>The Triumphal Way</b>, for whoever wins: the Gate of Triumph across from the throne, the Avenue through the
 * stands lined with braziers, the bridge over the moat, the Hall of Triumph with the rewards on their pedestals,
 * the Gallery of Witness climbing the cavern wall, the Stair of Stars over the dome (with glass in its floor to look
 * down through), the Laurel Door onto the Brink, and the shaft up through the field to the crowd above.</li>
 * </ul>
 * The gates and doors on that way are closed here; {@link DeepEncounter} opens them for a champion, and the colosseum's
 * reset closes them again.
 */
final class TheDeep {
	/** The Cells' bearing: the Hall of the Fallen, the Oath Bridge, the Gate of Triumph and the Hall of Triumph are on it. */
	static final double C = Heartwell.BEARING;
	static final double COS = Math.cos(C);
	static final double SIN = Math.sin(C);
	/** How far round (counter-clockwise) the Gallery of Witness climbs before the Stair of Stars turns inward. */
	static final double TURN = Math.toRadians(68);
	static final double GALLERY_FROM = Math.toRadians(8.5);
	/** Where the Gallery ends: at the side of the Stair of Stars' landing. */
	static final double GALLERY_END = TURN - 0.03;
	static final double COS2 = Math.cos(C + TURN);
	static final double SIN2 = Math.sin(C + TURN);

	/** The arena's floor block and where people stand on it. */
	static final int FLOOR = -48;
	static final int FEET = FLOOR + 1;
	static final double ARENA = 34;
	static final double WALL = 36.5;
	static final double STANDS = 62;
	static final double AMBULATORY = 66;
	static final double FACADE = 70;
	static final double MOAT = 77.5;
	/** The rough floor of the cavern, round the colosseum. */
	static final int GROUND = -51;
	static final int LAVA_TOP = -52;
	/** The Brink: the shaft's radius, the ledge, the platform and the Well. */
	static final int BRINK = Heartwell.FLOOR;
	static final int BRINK_FEET = BRINK + 1;
	static final double SHAFT = 16;
	static final double LEDGE = 24.5;
	static final double PLATFORM = 4.5;
	static final double WELL = 1.5;
	/** The Heart of the Star, hung over the Well: its middle, and its size. */
	static final int HEART_Y = 44;
	static final int HEART_R = 3;
	/** The top of the shaft up to the field (the field itself, from HATCH up, is the hatch the encounter opens). */
	static final int HATCH = CelestialColosseum.S - 4;
	/** The Hall of Triumph, along the Cells' bearing. */
	static final double HALL_FROM = 80;
	static final double HALL_APSE = 98;
	static final double HALL_HALF = 9;
	/** Vaelor's throne (along the bearing opposite the Cells') and where he sits. */
	static final double THRONE = 31;
	static final int DAIS_TOP = FEET + 3;
	/** The Gallery climbs from the arena's level to here, where the Stair of Stars begins. */
	static final int GALLERY_TOP = 0;
	/** How far out anything of the Deep goes. */
	static final double REACH = 112;

	private TheDeep() {
	}

	static boolean near(double d) {
		return d <= REACH;
	}

	// --- Shapes ---

	/** The cavern wall's radius at bearing {@code ang}, smooth (for the Gallery's road). */
	static double wall(double ang) {
		return 97 + 3.2 * Math.sin(3 * ang + 1.1) + 1.6 * Math.sin(5 * ang + 0.4);
	}

	private static double wallRough(double ang, int dx, int dz) {
		return wall(ang) + 0.9 * Math.sin(11 * ang + 2) + (PracticeLayout.scatter(dx, dz, 91) % 100) / 100.0 * 0.8;
	}

	/** The dome's height at {@code r} from the middle, without its roughness. */
	static double dome(double r) {
		double t = Math.max(0, r - SHAFT);
		return 30 - 0.0055 * t * t;
	}

	/** The top of the cavern's air in this column. */
	static int ceiling(int dx, int dz, double d) {
		if (d < SHAFT) {
			return BRINK - 1;
		}
		double n = 1.2 * Math.sin(dx * 0.13 + 0.7) * Math.sin(dz * 0.11 + 1.9) + 0.8 * Math.sin((dx + dz) * 0.07);
		// Smooth near the Stair of Stars, so its floor always has rock under it.
		double l2 = Math.abs(-dx * SIN2 + dz * COS2);
		if (l2 < 8 && dx * COS2 + dz * SIN2 > 0) {
			n = Math.min(n, 0);
		}
		return (int) Math.floor(dome(d) + n);
	}

	/** The top block of the cavern's floor in this column (outside the colosseum). */
	static int ground(int dx, int dz, double d, double ang) {
		double n = 1.3 * Math.sin(dx * 0.09 + 0.3) * Math.cos(dz * 0.08 + 1.2) + 0.7 * Math.sin(dx * 0.21 - dz * 0.17);
		double edge = Math.max(0, d - (wall(ang) - 10)) * 0.5;
		return GROUND + (int) Math.round(n + edge);
	}

	/** The height people stand at on the Gallery of Witness, {@code turn} radians round from the Cells' bearing. */
	static double galleryFeet(double turn) {
		double t = (turn - GALLERY_FROM) / (GALLERY_END - GALLERY_FROM);
		return FEET + Math.max(0, Math.min(1, t)) * (GALLERY_TOP + 1 - FEET);
	}

	/** The floor block of the Stair of Stars at {@code r} from the middle (one block lower per block inward at most). */
	static int stairFloor(double r) {
		double landing = wall(C + TURN) - 4;
		double rise = r >= landing ? 0 : landing - r;
		return (int) Math.max(GALLERY_TOP, Math.min(BRINK, Math.floor(Math.min(dome(r) + 6, GALLERY_TOP + rise))));
	}

	/** The seats' height at {@code d}: a step up for about each block out. */
	static int seat(double d) {
		return -39 + (int) Math.floor((d - WALL) * 0.95);
	}

	/** Angle between bearings, -pi..pi. */
	static double diff(double a, double b) {
		double x = (a - b) % (2 * Math.PI);
		if (x > Math.PI) {
			x -= 2 * Math.PI;
		}
		if (x < -Math.PI) {
			x += 2 * Math.PI;
		}
		return x;
	}

	private static BlockState stair(Block b, Direction up) {
		return b.defaultBlockState().setValue(StairBlock.FACING, up);
	}

	private static BlockState campfire(boolean soul) {
		return (soul ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState().setValue(CampfireBlock.LIT, false)
			.setValue(CampfireBlock.SIGNAL_FIRE, false);
	}

	// --- The column ---

	static void build(BlockState[] c, int dx, int dz, double d, double deg) {
		double ang = Math.atan2(dz, dx);
		double a = dx * COS + dz * SIN;
		double lat = -dx * SIN + dz * COS;
		double rw = wallRough(ang, dx, dz);
		if (d < rw) {
			cavern(c, dx, dz, d, deg, ang, rw);
		}
		if (d <= FACADE + 1) {
			colosseum(c, dx, dz, d, deg, a, lat);
		} else if (d <= MOAT + 1) {
			moat(c, d, a, lat);
		}
		if (a > WALL - 1 && a < FACADE + 1 && Math.abs(lat) <= 4.5) {
			avenue(c, dx, dz, a, lat);
		}
		if (a >= FACADE && a < HALL_FROM && Math.abs(lat) <= 3.5) {
			bridge(c, a, lat);
		}
		if (a >= HALL_FROM - 1 && a < HALL_APSE + 11 && Math.abs(lat) <= HALL_HALF + 2) {
			hall(c, dx, dz, a, lat);
		}
		if (a >= 92.5 && a < 96.5 && lat > HALL_HALF + 1.5 && lat < 16) {
			// From the hall's side door out to the foot of the Gallery.
			deep(c, GROUND - 2, FLOOR - 1, BLACK_BRICKS);
			put(c, FLOOR, lat < 13 ? PURPUR : BLACK_BRICKS);
			deep(c, FEET, FEET + 4, AIR);
		}
		gallery(c, dx, dz, d, ang);
		double a2 = dx * COS2 + dz * SIN2;
		double l2 = -dx * SIN2 + dz * COS2;
		if (a2 > LEDGE - 1 && a2 < wall(C + TURN) + 1.5 && Math.abs(l2) <= 3.5) {
			stairOfStars(c, dx, dz, a2, l2);
		}
		if (d <= LEDGE + 1) {
			brink(c, dx, dz, d, deg, a, lat);
		}
	}

	// --- The cavern ---

	private static void cavern(BlockState[] c, int dx, int dz, double d, double deg, double ang, double rw) {
		int top = ceiling(dx, dz, d);
		int floor = d <= FACADE + 1 ? GROUND - 2 : ground(dx, dz, d, ang);
		deep(c, floor + 1, top, AIR);
		int roll = PracticeLayout.scatter(dx, dz, 7);
		if (d >= SHAFT) {
			// The dome is full of stars: froglights and sea lanterns set in the rock.
			if (roll < 38) {
				put(c, top + 1, roll < 20 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : roll < 30 ? SEA_LANTERN
					: roll < 34 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.VERDANT_FROGLIGHT.defaultBlockState());
			} else if (roll < 48 && d > 30) {
				put(c, top, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
			}
		}
		if (d > 24 && d < rw - 8) {
			stalactite(c, dx, dz, top);
		}
		if (d > MOAT + 2) {
			ground(c, dx, dz, d, ang, rw, floor);
		}
	}

	/** Stalactites: dripstone cones hung from the dome, tipped with pointed dripstone. */
	private static void stalactite(BlockState[] c, int dx, int dz, int top) {
		int cell = 11;
		int cx = Math.floorDiv(dx, cell);
		int cz = Math.floorDiv(dz, cell);
		for (int i = cx - 1; i <= cx + 1; i++) {
			for (int j = cz - 1; j <= cz + 1; j++) {
				int h = PracticeLayout.scatter(i, j, 33);
				if (h >= 450) {
					continue;
				}
				double ox = i * cell + 2 + h % 7;
				double oz = j * cell + 2 + (h / 7) % 7;
				double rad = 1.4 + (h % 13) / 7.0;
				double dist = Math.hypot(dx - ox, dz - oz);
				if (dist >= rad) {
					continue;
				}
				int len = 4 + h % 11;
				int hang = (int) Math.round(len * (1 - dist / rad));
				if (hang <= 0) {
					continue;
				}
				deep(c, top - hang + 1, top, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
				if (dist < 0.75) {
					hangTip(c, top - hang, 3, Direction.DOWN);
				}
				return;
			}
		}
	}

	/** Pointed dripstone, {@code n} long, from {@code from} (next to what holds it) in direction {@code dir}: base, frustum, tip. */
	private static void hangTip(BlockState[] c, int from, int n, Direction dir) {
		for (int i = 0; i < n; i++) {
			int y = dir == Direction.DOWN ? from - i : from + i;
			SpeleothemThickness t = i == n - 1 ? SpeleothemThickness.TIP : i == 0 && n >= 3 ? SpeleothemThickness.BASE : SpeleothemThickness.FRUSTUM;
			put(c, y, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, dir)
				.setValue(PointedDripstoneBlock.THICKNESS, t));
		}
	}

	/** The cavern's floor: rock and tuff, stalagmites, amethyst spires, lava pools under lava falls. */
	private static void ground(BlockState[] c, int dx, int dz, double d, double ang, double rw, int floor) {
		int roll = PracticeLayout.scatter(dx, dz, 19) % 100;
		put(c, floor, roll < 20 ? Blocks.TUFF.defaultBlockState() : roll < 26 ? Blocks.SMOOTH_BASALT.defaultBlockState()
			: roll < 29 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState());
		if (onPath(dx, dz, d, ang)) {
			return;
		}
		// Lava pools at the foot of the walls, each under a fall.
		for (int i = 0; i < POOLS.length; i++) {
			double off = diff(ang, POOLS[i]);
			double depth = rw - d;
			if (Math.abs(off) * d < 9 && depth < 13) {
				double edge = Math.abs(off) * d / 9 + Math.max(0, depth - 9) / 4;
				if (edge < 0.85) {
					deep(c, LAVA_TOP - 2, LAVA_TOP, Blocks.LAVA.defaultBlockState());
					deep(c, LAVA_TOP + 1, floor, AIR);
					put(c, LAVA_TOP - 3, Blocks.BASALT.defaultBlockState());
				} else {
					deep(c, LAVA_TOP + 1, floor, Blocks.BLACKSTONE.defaultBlockState());
				}
				if (Math.abs(off) * d < 0.5 && depth < 1.0 && depth >= 0) {
					// The fall: a source on the wall's face, high up; it runs down into the pool.
					put(c, -18, Blocks.LAVA.defaultBlockState());
				}
				return;
			}
		}
		// Amethyst spires.
		int cell = 19;
		int ci = Math.floorDiv(dx, cell);
		int cj = Math.floorDiv(dz, cell);
		int h = PracticeLayout.scatter(ci, cj, 57);
		if (h < 500) {
			double ox = ci * cell + 4 + h % 11;
			double oz = cj * cell + 4 + (h / 11) % 11;
			double dist = Math.hypot(dx - ox, dz - oz);
			double od = Math.hypot(ox, oz);
			if (od > MOAT + 5 && od < wall(Math.atan2(oz, ox)) - 5 && !onPath((int) ox, (int) oz, od, Math.atan2(oz, ox))) {
				int height = 5 + h % 9;
				if (dist < 1.3) {
					deep(c, floor + 1, floor + height, AMETHYST);
					put(c, floor + height + 1, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
					return;
				}
				if (dist < 2.4) {
					deep(c, floor + 1, floor + (int) Math.round((2.4 - dist) * height / 2.5), Blocks.CALCITE.defaultBlockState());
					return;
				}
			}
		}
		// Stalagmites.
		int k = PracticeLayout.scatter(Math.floorDiv(dx, 9), Math.floorDiv(dz, 9), 71);
		if (k < 300) {
			double ox = Math.floorDiv(dx, 9) * 9 + 2 + k % 5;
			double oz = Math.floorDiv(dz, 9) * 9 + 2 + (k / 5) % 5;
			double dist = Math.hypot(dx - ox, dz - oz);
			double rad = 1.2 + (k % 3) * 0.5;
			if (dist < rad) {
				int rise = (int) Math.round((3 + k % 6) * (1 - dist / rad));
				deep(c, floor + 1, floor + rise, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
				if (dist < 0.75) {
					hangTip(c, floor + rise + 1, 2, Direction.UP);
				}
			}
		}
	}

	/** Bearings of the lava pools (away from the paths). */
	static final double[] POOLS = {C + Math.toRadians(140), C + Math.toRadians(205), C + Math.toRadians(265), C + Math.toRadians(-40)};

	/** Whether the column is on (or by) the Triumphal Way, where nothing grows. */
	private static boolean onPath(int dx, int dz, double d, double ang) {
		double a = dx * COS + dz * SIN;
		double lat = -dx * SIN + dz * COS;
		if (a > FACADE && a < HALL_APSE + 14 && Math.abs(lat) < HALL_HALF + 6) {
			return true;
		}
		double turn = diff(ang, C);
		return turn > 0 && turn < TURN + 0.1 && d > wall(ang) - 12;
	}

	// --- The Deep Colosseum ---

	private static void colosseum(BlockState[] c, int dx, int dz, double d, double deg, double a, double lat) {
		if (d <= ARENA) {
			arena(c, dx, dz, d, deg, a, lat);
		} else if (d <= WALL) {
			arenaWall(c, dx, dz, d, deg, a, lat);
		} else if (d <= STANDS) {
			stands(c, dx, dz, d, deg);
		} else if (d <= AMBULATORY) {
			int f = seat(STANDS);
			deep(c, GROUND - 2, f, BRICKS);
			put(c, f, Math.floorMod((int) Math.floor(deg / 4), 2) == 0 ? POLISHED : TILES);
			if (d <= STANDS + 1) {
				put(c, f + 1, BLACK_WALL);
				if (offStep(deg, 10, d) < 0.5) {
					put(c, f + 2, Blocks.SOUL_LANTERN.defaultBlockState());
				}
			}
		} else {
			facade(c, dx, dz, d, deg);
		}
	}

	/** The arena's floor: a star where the Leap lands, rings and rays, a circle of runes, lights round the edge, broken columns. */
	private static void arena(BlockState[] c, int dx, int dz, double d, double deg, double a, double lat) {
		deep(c, GROUND - 2, FLOOR - 1, BLACK_BRICKS);
		double ang = Math.atan2(dz, dx);
		double star = 2.2 + 3.2 * Math.pow(Math.abs(Math.cos(2 * (ang - C))), 6);
		BlockState floor = d < 1.2 ? CRYING : d <= star ? Blocks.GOLD_BLOCK.defaultBlockState() : d < 6.5 ? PURPUR
			: Math.abs(d - 12) < 0.55 ? (offStep(deg, 15, d) < 0.6 ? CRYING : GILDED)
			: offStep(deg - Math.toDegrees(C), 45, d) < 0.55 && d < 30 ? GILDED
			: Math.abs(d - 30.5) < 0.55 && offStep(deg, 10, d) < 0.6 ? SEA_LANTERN
			: Math.abs(d - 15.5) < 0.55 && offStep(deg, 15, d) < 0.6 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()
			: Math.abs(d - 26) < 0.55 && offStep(deg + 7.5, 15, d) < 0.6 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState()
			: d > 31 ? BLACK_BRICKS : Math.floorMod((int) Math.floor(d / 3), 2) == 0 ? POLISHED : TILES;
		put(c, FLOOR, floor);
		// Six broken columns round the middle, to get behind.
		for (int i = 0; i < 6; i++) {
			double ca = C + Math.toRadians(30 + 60 * i);
			double r = Math.hypot(dx - Math.cos(ca) * 21, dz - Math.sin(ca) * 21);
			if (r < 1.9) {
				int h = 4 + PracticeLayout.scatter(i, 3, 5) % 4 + (r < 1.0 ? 1 : 0) - (PracticeLayout.scatter(dx, dz, 2) % 3 == 0 ? 1 : 0);
				deep(c, FEET, FEET + h, r < 1.0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : BLACK_BRICKS);
				put(c, FEET, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
				if (r >= 1.0 && PracticeLayout.scatter(dx, dz, 3) % 4 == 0) {
					put(c, FEET + 2, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				}
				if (r >= 1.0 && h >= 4 && PracticeLayout.scatter(dx, dz, 4) % 3 == 0) {
					put(c, FEET + 3, Blocks.GILDED_BLACKSTONE.defaultBlockState());
				}
				return;
			}
			if (r < 2.6) {
				// Light round its foot, shining up it.
				put(c, FLOOR, Blocks.SHROOMLIGHT.defaultBlockState());
				return;
			}
			if (r < 3.4 && PracticeLayout.scatter(dx, dz, 8) % 5 == 0) {
				// Rubble round its foot.
				put(c, FEET, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
				return;
			}
		}
		// Braziers round the edge, every thirty degrees (not before the throne or the Gate).
		if (d > 31.8 && d < 33.2 && offStep(deg - Math.toDegrees(C) + 15, 30, d) < 0.7) {
			put(c, FEET, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			put(c, FEET + 1, BLACK_WALL);
			put(c, FEET + 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
			return;
		}
		// Vaelor's dais and throne, across from the Gate: three steps up.
		double ta = -a;
		double tl = Math.abs(lat);
		Direction toThrone = outward(-COS, -SIN);
		if (ta > 25.5 && tl < 6.5) {
			int steps = ta > 29.5 && tl < 4.5 ? 3 : ta > 27.5 && tl < 5.5 ? 2 : 1;
			deep(c, FEET, FEET + steps - 1, BLACK_BRICKS);
			boolean front = steps == 1 ? ta < 26.5 : steps == 2 ? ta < 28.5 : ta < 30.5;
			if (front) {
				put(c, FEET + steps - 1, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, toThrone));
			} else if (steps == 3) {
				put(c, FEET + 2, tl < 1 ? PURPUR : GILDED);
				throne(c, ta, tl);
			} else if (steps == 2 && tl >= 4.5 && ta > 30.5 && ta < 31.5) {
				put(c, FEET + 2, Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
	}

	/** The throne: a high back crowned in gold like his helm, arms, a cushion of purple. */
	private static void throne(BlockState[] c, double ta, double tl) {
		int base = DAIS_TOP;
		if (ta >= 32.5 && tl < 3.5) {
			int top = base + 8 + (tl < 0.5 ? 2 : 0);
			deep(c, base, top, tl < 1.5 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : BLACK_BRICKS);
			put(c, base + 4, tl < 0.5 ? CRYING : tl < 1.5 ? GILDED : BLACK_BRICKS);
			if (tl >= 2.5 || tl < 0.5 || tl >= 1.5 && tl < 2.5) {
				put(c, top + 1, Blocks.GOLD_BLOCK.defaultBlockState());
				put(c, top + 2, tl < 0.5 ? AMETHYST : Blocks.GOLD_BLOCK.defaultBlockState());
			}
		} else if (ta >= 30.5 && tl >= 2.5 && tl < 3.5) {
			deep(c, base, base + 1, BLACK_BRICKS);
			put(c, base + 2, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
		} else if (ta >= 31.5 && tl < 2.5) {
			put(c, base, Blocks.CARPET.purple().defaultBlockState());
		}
	}

	/** Where Vaelor sits (blueprint dx, feet y, dz), facing the Gate. */
	static double[] throneSeat() {
		return new double[] {-COS * THRONE, DAIS_TOP, -SIN * THRONE};
	}

	/** The arena's wall: lights between pilasters, banners, and the Gate of Triumph (closed until someone wins). */
	private static void arenaWall(BlockState[] c, int dx, int dz, double d, double deg, double a, double lat) {
		int top = FEET + 9;
		deep(c, GROUND - 2, top, BLACK_BRICKS);
		if (d > WALL - 1) {
			put(c, top + 1, offStep(deg, 12, d) < 0.6 ? Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
				.setValue(CampfireBlock.SIGNAL_FIRE, false) : BLACK_WALL);
		}
		if (gate(dx, dz)) {
			for (int y = FEET; y <= FEET + 5; y++) {
				put(c, y, gateBlock(y, lat));
			}
			put(c, FLOOR, PURPUR);
			return;
		}
		if (a > ARENA - 0.5 && Math.abs(lat) >= 2.5 && Math.abs(lat) < 3.5) {
			deep(c, FEET, FEET + 6, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			return;
		}
		if (a > ARENA - 0.5 && Math.abs(lat) < 2.5) {
			deep(c, FEET + 6, FEET + 7, Blocks.GOLD_BLOCK.defaultBlockState());
			return;
		}
		if (d <= ARENA + 1) {
			boolean pilaster = offStep(deg, 12, d) < 0.7;
			if (pilaster) {
				deep(c, FEET, top, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
				put(c, top, GILDED);
			} else if (offStep(deg + 6, 12, d) < 0.6) {
				put(c, FEET + 2, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
				put(c, FEET + 6, SEA_LANTERN);
			} else if (offStep(deg + 3, 6, d) < 0.5) {
				put(c, FEET + 4, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
			}
		}
	}

	/** The Gate of Triumph's leaves: blackstone and gold, a star where they meet. */
	private static BlockState gateBlock(int y, double lat) {
		if (Math.abs(lat) < 0.5 && (y == FEET + 2 || y == FEET + 3)) {
			return Blocks.GOLD_BLOCK.defaultBlockState();
		}
		if (y == FEET + 5) {
			return GILDED;
		}
		return Math.abs(lat) < 1.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	}

	/** Whether (dx, dz) is in the Gate of Triumph (its leaves fill FEET to FEET + 5). */
	static boolean gate(int dx, int dz) {
		double a = dx * COS + dz * SIN;
		double lat = -dx * SIN + dz * COS;
		return a > ARENA - 0.5 && a <= WALL + 0.5 && Math.abs(lat) < 2.5 && Math.hypot(dx, dz) > ARENA;
	}

	/** The stands: rows of seats rising outward, aisles every thirty degrees, the skulls of the crowd that watched. */
	private static void stands(BlockState[] c, int dx, int dz, double d, double deg) {
		int s = seat(d);
		deep(c, GROUND - 2, s, BRICKS);
		Direction out = outward(dx, dz);
		boolean aisle = offStep(deg, 30, d) < 1.0;
		if (aisle) {
			if (offStep(deg, 30, d) < 0.5 && Math.floorMod(s, 3) == 0) {
				put(c, s - 1, Blocks.SHROOMLIGHT.defaultBlockState());
			}
			put(c, s, stair(Blocks.DEEPSLATE_BRICK_STAIRS, out));
			if (offStep(deg, 30, d) >= 0.5 && Math.floorMod((int) Math.floor(d), 6) == 0) {
				put(c, s + 1, Blocks.SOUL_LANTERN.defaultBlockState());
			}
			return;
		}
		boolean edge = seat(d + 1) > s;
		if (!edge && offStep(deg + 15, 30, d) < 0.5 && Math.floorMod(s, 5) == 0) {
			put(c, s, POLISHED);
			put(c, s + 1, Blocks.SOUL_LANTERN.defaultBlockState());
			return;
		}
		put(c, s, edge ? stair(Blocks.DEEPSLATE_TILE_STAIRS, outward(-dx, -dz)).setValue(StairBlock.HALF,
			net.minecraft.world.level.block.state.properties.Half.TOP) : Math.floorMod(s, 2) == 0 ? POLISHED : TILES);
		int roll = PracticeLayout.scatter(dx, dz, 41);
		if (!edge && roll < 26) {
			float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
			put(c, s + 1, (roll < 3 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState()
				.setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(yaw)));
		} else if (!edge && roll < 60) {
			put(c, s + 1, Blocks.DYED_CANDLE.purple().defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + roll % 3)
				.setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
		}
	}

	/** The outer wall: four storeys of arches, each lit from within, cornices between, the foundation down in the moat. */
	private static void facade(BlockState[] c, int dx, int dz, double d, double deg) {
		int top = -3;
		deep(c, LAVA_TOP - 4, top, BLACK_BRICKS);
		int[][] storeys = {{-49, -42}, {-38, -31}, {-27, -20}, {-16, -9}};
		boolean pier = offStep(deg, 6, d) < 1.1;
		if (d > FACADE - 2) {
			for (int[] s : storeys) {
				if (!pier) {
					deep(c, s[0], s[1], AIR);
					put(c, s[1], Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
				} else {
					put(c, s[0] + 3, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
				}
				put(c, s[1] + 1, GILDED);
				put(c, s[1] + 2, TILES);
			}
		}
		if (d > FACADE - 2.5 && d <= FACADE - 1.5 && !pier) {
			// The back of each arch, lit.
			for (int[] s : storeys) {
				deep(c, s[0], s[1], BLACK_BRICKS);
				put(c, s[0] + 2, Math.floorMod((int) Math.floor(deg / 6), 2) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.OCHRE_FROGLIGHT
					.defaultBlockState());
			}
		}
		put(c, top + 1, offStep(deg, 6, d) < 1.1 ? Blocks.GOLD_BLOCK.defaultBlockState() : BLACK_WALL);
	}

	/** The moat: lava round the colosseum, a basalt rim. */
	private static void moat(BlockState[] c, double d, double a, double lat) {
		if (d <= MOAT) {
			deep(c, LAVA_TOP - 3, LAVA_TOP - 3, Blocks.BASALT.defaultBlockState());
			deep(c, LAVA_TOP - 2, LAVA_TOP, Blocks.LAVA.defaultBlockState());
			deep(c, LAVA_TOP + 1, GROUND + 3, AIR);
		} else {
			deep(c, LAVA_TOP - 3, GROUND, Blocks.POLISHED_BASALT.defaultBlockState());
		}
	}

	/** The Avenue: from the Gate of Triumph out through the stands and the outer wall, braziers either side. */
	private static void avenue(BlockState[] c, int dx, int dz, double a, double lat) {
		double p = Math.abs(lat);
		if (a <= WALL - 0.5) {
			return;
		}
		int fa = (int) Math.floor(a);
		if (p > 3.5) {
			deep(c, FLOOR - 1, FEET + 7, BRICKS);
			if (Math.floorMod(fa, 4) == 0) {
				deep(c, FEET, FEET + 6, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			} else if (Math.floorMod(fa, 4) == 2) {
				put(c, FEET + 5, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
			}
			return;
		}
		int vault = FEET + 6 + (p < 2.5 ? 1 : 0);
		deep(c, FEET, vault, AIR);
		put(c, vault + 1, p < 0.5 && Math.floorMod(fa, 5) == 0 ? SEA_LANTERN : TILES);
		put(c, FLOOR, p < 1 ? PURPUR : BLACK_BRICKS);
		put(c, FLOOR - 1, BRICKS);
		Direction in = lat > 0 ? outward(SIN, -COS) : outward(-SIN, COS);
		if (p >= 2.5) {
			if (Math.floorMod(fa, 6) == 5 && a >= FACADE - 4) {
				put(c, FEET + 4, SEA_LANTERN);
			}
			if (Math.floorMod(fa, 6) == 2) {
				put(c, FEET, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				put(c, FEET + 1, campfire(false));
			} else if (Math.floorMod(fa, 6) == 5 && a < FACADE - 4) {
				put(c, FEET + 3, Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, in));
			}
		}
		if (fa == (int) WALL + 3 && lat > 2.5) {
			ColosseumInterior.wallSign(c, dx, FEET + 2, dz, in, DyeColor.YELLOW, true, "THE AVENUE", "OF TRIUMPH", "", "walk tall");
		}
	}

	/** The bridge over the moat: parapets, lamp posts, piers down into the lava. */
	private static void bridge(BlockState[] c, double a, double lat) {
		double p = Math.abs(lat);
		int fa = (int) Math.floor(a);
		put(c, FLOOR, p < 1 ? PURPUR : p > 2.5 ? GILDED : BLACK_BRICKS);
		put(c, FLOOR - 1, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		if (fa == 73 || fa == 76) {
			deep(c, LAVA_TOP - 3, FLOOR - 1, BLACK_BRICKS);
		}
		if (p > 2.5) {
			put(c, FEET, BLACK_WALL);
			if (Math.floorMod(fa, 3) == 0) {
				put(c, FEET + 1, BLACK_WALL);
				put(c, FEET + 2, Blocks.LANTERN.defaultBlockState());
			}
		}
	}

	// --- The Hall of Triumph ---

	/** The pedestals' places: on an arc round the apse, from left to right as you face it. */
	static int[][] pedestals() {
		int[][] out = new int[7][];
		for (int i = 0; i < 7; i++) {
			double phi = Math.toRadians(-67.5 + 22.5 * i);
			double pa = HALL_APSE + 5.5 * Math.cos(phi);
			double pl = 5.5 * Math.sin(phi);
			out[i] = new int[] {(int) Math.floor(pa * COS - pl * SIN), (int) Math.floor(pa * SIN + pl * COS)};
		}
		return out;
	}

	private static int vault(double lat) {
		double t = Math.min(1, Math.abs(lat) / (HALL_HALF + 0.5));
		return FEET + 9 + (int) Math.round(7 * Math.sqrt(1 - t * t));
	}

	/** The Hall of Triumph: a portico on the cavern floor, a barrel-vaulted hall, the apse with the pedestals. */
	private static void hall(BlockState[] c, int dx, int dz, double a, double lat) {
		double p = Math.abs(lat);
		int fa = (int) Math.floor(a);
		double ra = Math.hypot(a - HALL_APSE, lat);
		boolean apse = a >= HALL_APSE;
		double edge = apse ? ra : p;
		if (apse && ra > HALL_HALF + 1.5 || !apse && p > HALL_HALF + 1.5) {
			return;
		}
		deep(c, GROUND - 2, FLOOR - 1, BLACK_BRICKS);
		Direction in = lat > 0 ? outward(SIN, -COS) : outward(-SIN, COS);
		int v = vault(edge);
		if (a < HALL_FROM) {
			// The steps up from the bridge's end.
			put(c, FLOOR, p < 1 ? PURPUR : BLACK_BRICKS);
			return;
		}
		if (a < HALL_FROM + 3) {
			// The portico: columns, a roof, steps.
			put(c, FLOOR, p < 1 ? PURPUR : BLACK_BRICKS);
			boolean column = Math.floorMod(fa, 3) == 1 && (Math.abs(p - 2.5) < 0.6 || Math.abs(p - 6) < 0.6 || Math.abs(p - 9.5) < 0.6);
			if (column) {
				put(c, FEET, Blocks.GOLD_BLOCK.defaultBlockState());
				deep(c, FEET + 1, FEET + 8, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				put(c, FEET + 9, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			} else {
				deep(c, FEET, FEET + 9, AIR);
			}
			deep(c, FEET + 10, FEET + 11, BLACK_BRICKS);
			put(c, FEET + 12, p < 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : TILES);
			if (fa == (int) HALL_FROM && p < 0.5) {
				ColosseumInterior.wallSign(c, dx, FEET + 10, dz, outward(-COS, -SIN), DyeColor.YELLOW, true, "THE HALL", "OF TRIUMPH", "", "");
			}
			return;
		}
		boolean wall = edge > HALL_HALF;
		if (!apse && lat > HALL_HALF + 1.5) {
			return;
		}
		if (wall) {
			deep(c, FLOOR, v + 2, BLACK_BRICKS);
			boolean door = !apse && lat > 0 && fa >= 93 && fa <= 95;
			if (door) {
				deep(c, FEET, FEET + 3, AIR);
				put(c, FLOOR, PURPUR);
				put(c, FEET + 4, Blocks.GOLD_BLOCK.defaultBlockState());
				return;
			}
			if (!apse && Math.floorMod(fa, 4) == 0) {
				deep(c, FEET, v, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			} else if (edge < HALL_HALF + 0.9) {
				put(c, FEET + 6, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
				if (Math.floorMod(fa, 4) == 2) {
					put(c, FEET + 2, SEA_LANTERN);
				}
			}
			return;
		}
		// Inside.
		deep(c, FEET, v, AIR);
		put(c, v + 1, edge < 0.6 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : TILES);
		put(c, v + 2, TILES);
		put(c, v + 3, edge < 0.6 ? Blocks.GOLD_BLOCK.defaultBlockState() : TILES);
		put(c, FLOOR, edge < 1 && !apse ? PURPUR : Math.floorMod(fa + (int) Math.floor(lat), 2) == 0 ? POLISHED : BLACK_BRICKS);
		if (!apse && edge < 1 && Math.floorMod(fa, 4) == 2) {
			put(c, FEET, Blocks.CARPET.yellow().defaultBlockState());
		}
		if (!apse && edge >= HALL_HALF - 1) {
			// Banners and lights down the walls, braziers at their feet.
			if (Math.floorMod(fa, 4) == 2) {
				put(c, FEET + 4, (Math.floorMod(fa, 8) == 2 ? Blocks.WALL_BANNER.yellow() : Blocks.WALL_BANNER.purple()).defaultBlockState()
					.setValue(WallBannerBlock.FACING, in));
				put(c, FEET, campfire(false));
			}
			if (fa == 90 && lat < 0) {
				ColosseumLore.champions(c, dx, FEET + 2, dz, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
					.setValue(net.minecraft.world.level.block.WallSignBlock.FACING, in));
			}
			if (fa == 87 && lat < 0) {
				ColosseumInterior.wallSign(c, dx, FEET + 2, dz, in, DyeColor.YELLOW, true, "HE KEPT THEM", "HERE FOR THE", "ONE WHO", "WON. TAKE THEM.");
			}
		}
		if (!apse && Math.floorMod(fa, 6) == 3 && edge < 0.6) {
			// Chandeliers on chains.
			deep(c, v - 2, v, CHAIN);
			put(c, v - 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		if (apse) {
			for (int[] ped : pedestals()) {
				if (ped[0] == dx && ped[1] == dz) {
					put(c, FEET, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
					put(c, FEET + 1, Blocks.GOLD_BLOCK.defaultBlockState());
					return;
				}
			}
			if (ra > HALL_HALF - 1 && ra <= HALL_HALF) {
				put(c, FEET + 3, Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(lat, a - HALL_APSE)) / 22.5), 2) == 0
					? Blocks.STAINED_GLASS.purple().defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState());
			}
			if (ra < 1.5) {
				put(c, FLOOR, Blocks.GOLD_BLOCK.defaultBlockState());
			}
		}
	}

	/** Where each reward floats (blueprint dx, y, dz), over its pedestal. */
	static double[] pedestalTop(int i) {
		int[] p = pedestals()[i];
		return new double[] {p[0] + 0.5, FEET + 2.4, p[1] + 0.5};
	}

	// --- The Gallery of Witness ---

	/** The road up the cavern wall: carved into the rock, a parapet on the drop, buttresses under it, braziers by it. */
	private static void gallery(BlockState[] c, int dx, int dz, double d, double ang) {
		double turn = diff(ang, C);
		if (turn < GALLERY_FROM || turn > GALLERY_END + 0.012) {
			return;
		}
		double centre = wall(ang) - 0.5;
		double off = d - centre;
		if (off < -3.5 || off > 3.5) {
			return;
		}
		double feet = galleryFeet(turn);
		int f = (int) Math.floor(feet) - 1;
		double back = galleryFeet(turn - 1.0 / Math.max(1, d));
		boolean rise = (int) Math.floor(feet) > (int) Math.floor(back);
		Direction up = outward(-Math.sin(ang), Math.cos(ang));
		Direction toWall = outward(Math.cos(ang), Math.sin(ang));
		int arc = (int) Math.floor(turn * centre);
		if (off < -2.5) {
			// The parapet on the drop, on a lip of stone.
			deep(c, f - 1, f, BLACK_BRICKS);
			put(c, f + 1, BLACK_WALL);
			if (Math.floorMod(arc, 5) == 0) {
				put(c, f + 2, Blocks.LANTERN.defaultBlockState());
			}
			if (Math.floorMod(arc, 14) == 0) {
				deep(c, ground(dx, dz, d, ang), f, BRICKS);
			}
			return;
		}
		if (off > 2.5) {
			// The rock wall behind, faced with brick; braziers in it; the story on it.
			deep(c, f, f + 5, BRICKS);
			if (Math.floorMod(arc, 8) == 4) {
				deep(c, f + 1, f + 2, AIR);
				put(c, f + 1, campfire(true));
			}
			int story = Math.floorMod(arc, 17) == 9 ? arc / 17 : -1;
			if (story >= 0 && story < GALLERY_WORDS.length) {
				ColosseumInterior.wallSign(c, dx, f + 2, dz, toWall.getOpposite(), DyeColor.WHITE, true, GALLERY_WORDS[story]);
			}
			return;
		}
		deep(c, f - 1, f - 1, BRICKS);
		put(c, f, rise ? stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, up) : Math.abs(off) < 0.7 ? PURPUR : BLACK_BRICKS);
		deep(c, f + 1, f + 5, AIR);
		if (d > wall(ang)) {
			put(c, f + 6, TILES);
		}
		if (Math.floorMod(arc, 14) == 0) {
			deep(c, ground(dx, dz, d, ang), f - 1, BRICKS);
		}
	}

	static final String[][] GALLERY_WORDS = {
		{"THE GALLERY", "OF WITNESS", "the champion's", "road up"},
		{"HE WAS THE", "SEVENTH OF", "THE SEVEN,", "and the best."},
		{"WHEN THE STAR", "FELL, HE", "CAUGHT IT", "IN HIS CHEST."},
		{"IT MADE HIM", "UNBREAKABLE.", "IT MADE HIM", "UNABLE TO DIE."},
		{"THE SIX SEALED", "HIM DOWN HERE", "AND BUILT A", "COLOSSEUM ON TOP."},
		{"TWO HUNDRED", "CAME DOWN", "TO FREE HIM.", "YOU DID."},
	};

	// --- The Stair of Stars ---

	private static void stairOfStars(BlockState[] c, int dx, int dz, double a2, double l2) {
		double p = Math.abs(l2);
		int fa = (int) Math.floor(a2);
		int f = stairFloor(fa + 0.5);
		boolean rise = stairFloor(fa + 1.5) < f;
		int top = f + 5;
		if (p > 2.5 && p <= 3.0 && Math.floorMod(fa, 5) == 0) {
			deep(c, f - 1, top + 1, BRICKS);
			put(c, f + 3, SEA_LANTERN);
			return;
		}
		if (p > 2.5) {
			if (l2 < 0 && a2 > wall(C + TURN) - 4) {
				// Where the Gallery comes in from the side.
				deep(c, f + 1, top, AIR);
				put(c, f, BLACK_BRICKS);
				put(c, top + 1, TILES);
				return;
			}
			deep(c, f - 1, top + 1, BRICKS);
			return;
		}
		Direction in = outward(-COS2, -SIN2);
		Direction toLine = l2 > 0 ? outward(SIN2, -COS2) : outward(-SIN2, COS2);
		deep(c, f + 1, top, AIR);
		put(c, top + 1, p < 0.6 && Math.floorMod(fa, 3) == 0 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : TILES);
		put(c, f - 1, BRICKS);
		boolean oculus = Math.floorMod(fa, 9) == 4 && p < 1.5 && fa > 30 && !rise;
		if (oculus) {
			// Glass in the floor, the rock cut away under it: the cavern far below.
			put(c, f, Blocks.STAINED_GLASS.purple().defaultBlockState());
			deep(c, ceiling(dx, dz, Math.hypot(dx, dz)) + 1, f - 1, AIR);
		} else {
			put(c, f, rise ? stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, in) : p < 1 ? PURPUR : BLACK_BRICKS);
		}
		if (p >= 1.5) {
			if (Math.floorMod(fa, 10) == 7 && !rise) {
				put(c, f + 1, campfire(true));
			}
			int story = Math.floorMod(fa, 13) == 6 && l2 > 0 ? (fa - 30) / 13 : -1;
			if (story >= 0 && story < STAIR_WORDS.length) {
				ColosseumInterior.wallSign(c, dx, f + 2, dz, toLine, DyeColor.WHITE, true, STAIR_WORDS[story]);
			}
		} else if (p < 0.5 && Math.floorMod(fa, 6) == 0) {
			put(c, top, CHAIN);
			put(c, top - 1, HANGING_SOUL);
		}
		if (laurelDoor(dx, dz)) {
			for (int y = BRINK_FEET; y <= BRINK_FEET + 3; y++) {
				put(c, y, y == BRINK_FEET + 3 ? GILDED : Math.abs(l2) < 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS
					.defaultBlockState());
			}
		}
		if (fa == (int) LEDGE + 3 && l2 > 1.5) {
			ColosseumInterior.wallSign(c, dx, BRINK_FEET + 1, dz, toLine, DyeColor.YELLOW, true, "THE LAUREL", "DOOR", "it opens for", "champions");
		}
	}

	static final String[][] STAIR_WORDS = {
		{"THE STAIR", "OF STARS", "look down", "through the glass"},
		{"THE COLOSSEUM", "UP THERE WAS", "BUILT ON HIS", "PRISON."},
		{"EVERY CHEER", "UP THERE", "ECHOED", "DOWN HERE."},
		{"NOW THEY", "CHEER FOR", "YOU.", ""},
	};

	/** Whether (dx, dz) is in the Laurel Door (it fills the Brink's feet height to three above). */
	static boolean laurelDoor(int dx, int dz) {
		double a2 = dx * COS2 + dz * SIN2;
		double l2 = -dx * SIN2 + dz * COS2;
		return a2 > LEDGE && a2 <= LEDGE + 1.5 && Math.abs(l2) <= 2.5;
	}

	// --- The Brink ---

	/** The ledge round the shaft, the Oath Bridge, the platform with the Well, the Heart of the Star, the shaft to the field. */
	private static void brink(BlockState[] c, int dx, int dz, double d, double deg, double a, double lat) {
		int top = Heartwell.dome(d);
		double a2 = dx * COS2 + dz * SIN2;
		double l2 = -dx * SIN2 + dz * COS2;
		if (d > LEDGE && (a2 > 0 && Math.abs(l2) <= 3.5 || a > 0 && Math.abs(lat) < 2.5)) {
			// Openings: the Laurel Door's passage, and the way in from the Hall of the Fallen (Heartwell builds that).
			return;
		}
		if (d > LEDGE) {
			// The wall round it all, with pilasters and lights.
			deep(c, BRINK - 1, Heartwell.dome(LEDGE) + 2, BRICKS);
			if (d <= LEDGE + 1) {
				if (offStep(deg, 15, d) < 0.7) {
					deep(c, BRINK_FEET, Heartwell.dome(LEDGE), PURPUR);
					put(c, BRINK_FEET + 5, Blocks.PURPUR_PILLAR.defaultBlockState());
				} else if (offStep(deg + 7.5, 15, d) < 0.6) {
					put(c, BRINK_FEET + 3, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
					put(c, BRINK_FEET + 8, SEA_LANTERN);
				}
			}
			return;
		}
		deep(c, BRINK_FEET, top, AIR);
		deep(c, top + 1, top + 2, TILES);
		boolean rib = offStep(deg, 30, d) < 0.6 && d > 3;
		int roll = PracticeLayout.scatter(dx, dz, 61) % 100;
		put(c, top + 1, rib ? AMETHYST : roll < 6 ? SEA_LANTERN : TILES);
		if (d <= WELL) {
			// The shaft up to the field, over the Heart (closed at the top by the field: the hatch).
			deep(c, top + 1, HATCH - 1, AIR);
		}
		heart(c, dx, dz, d);
		boolean bridge = a > 0 && Math.abs(lat) <= 2.5 && d >= PLATFORM - 0.5 && d < SHAFT + 1;
		if (d >= SHAFT + 1) {
			// The ledge.
			deep(c, BRINK - 4, BRINK - 1, BRICKS);
			put(c, BRINK, Math.abs(d - 21) < 0.5 ? PURPUR : offStep(deg, 15, d) < 0.6 ? GILDED : Math.floorMod((int) Math.floor(d), 2) == 0 ? POLISHED
				: BLACK_BRICKS);
			if (d > LEDGE - 1 && offStep(deg + 7.5, 30, d) < 0.5) {
				put(c, BRINK_FEET, Blocks.SOUL_LANTERN.defaultBlockState());
			}
			return;
		}
		if (d >= SHAFT) {
			// The rail round the shaft (open where the bridge leaves).
			put(c, BRINK, BLACK_BRICKS);
			put(c, BRINK - 1, BRICKS);
			if (!bridge) {
				put(c, BRINK_FEET, BLACK_WALL);
			}
			if (!bridge && offStep(deg, 20, d) < 0.5) {
				put(c, BRINK_FEET + 1, Blocks.SOUL_LANTERN.defaultBlockState());
			}
			return;
		}
		deep(c, ceiling(dx, dz, d) + 1, BRINK, AIR);
		if (bridge) {
			put(c, BRINK, Math.abs(lat) < 0.6 ? PURPUR : BLACK_BRICKS);
			put(c, BRINK - 1, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			if (Math.abs(lat) > 1.6) {
				put(c, BRINK_FEET, BLACK_WALL);
				if (Math.floorMod((int) Math.floor(a), 4) == 1) {
					put(c, BRINK_FEET + 1, Blocks.LANTERN.defaultBlockState());
				}
			}
			return;
		}
		if (d < PLATFORM) {
			// The platform, its rim, the Well in its middle, and the cone of stone under it.
			if (d >= WELL) {
				put(c, BRINK, d < 2.5 ? Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(dz, dx)) / 45), 2) == 0
					? Blocks.GOLD_BLOCK.defaultBlockState() : GILDED : d < 3.5 ? PURPUR : POLISHED);
				int under = BRINK - (int) Math.round((PLATFORM - d) * 1.8);
				deep(c, under, BRINK - 1, BLACK_BRICKS);
				put(c, under - 1, CRYING);
				if (d >= PLATFORM - 1 && !(a > 0 && Math.abs(lat) < 1.7)) {
					put(c, BRINK_FEET, BLACK_WALL);
				}
			}
		}
	}

	/** The Heart of the Star: a great crystal hung over the Well on a chain. */
	private static void heart(BlockState[] c, int dx, int dz, double d) {
		int m = Math.abs(dx) + Math.abs(dz);
		if (m <= HEART_R) {
			int span = HEART_R - m;
			for (int y = HEART_Y - span; y <= HEART_Y + span; y++) {
				int k = m + Math.abs(y - HEART_Y);
				put(c, y, k <= 1 ? CRYING : k == 2 ? AMETHYST : Blocks.STAINED_GLASS.purple().defaultBlockState());
			}
			if (m == HEART_R) {
				put(c, HEART_Y - 1, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
			}
		}
		if (dx == 0 && dz == 0) {
			deep(c, HEART_Y + HEART_R + 1, Heartwell.dome(0), CHAIN);
			put(c, HEART_Y - HEART_R - 1, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
		}
	}

	/** Whether (dx, y, dz) is part of the Heart (what shatters when a champion rises through it). */
	static boolean inHeart(int dx, int y, int dz) {
		int m = Math.abs(dx) + Math.abs(dz) + Math.abs(y - HEART_Y);
		return m <= HEART_R || dx == 0 && dz == 0 && y >= HEART_Y - HEART_R - 1 && y <= Heartwell.dome(0);
	}

	/** Whether (x, y, z) from the centre is down in the Deep (the cavern, the colosseum or the Way), not on the Brink. */
	static boolean inDeep(double dx, double y, double dz) {
		double d = Math.hypot(dx, dz);
		return d < REACH && y < BRINK - 1 && y > GROUND - 8;
	}

	/** Whether someone at (x, y, z) from the centre is falling down the shaft: the Leap. */
	static boolean leaping(double dx, double y, double dz) {
		return Math.hypot(dx, dz) < SHAFT && y < BRINK - 1.5 && y > BRINK - 25;
	}

	/** Whether (x, z) from the centre is inside the arena, and how far. */
	static boolean inArena(double dx, double y, double dz, double margin) {
		return Math.hypot(dx, dz) < ARENA + margin && y > FEET - 2 && y < FEET + 14;
	}
}

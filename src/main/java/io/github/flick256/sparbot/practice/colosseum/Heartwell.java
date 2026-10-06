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
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPLE_GLASS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.S;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.TILES;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.fill;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.offStep;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The end of the trail: the lower door in the Cells, a stair down under the Inner Ring and the podium, a long
 * tunnel beneath the field, and at the end, right under the heart of the compass rose, the Heartwell. It is a
 * round vault of calcite and amethyst lit through purple windows. In its middle is the seal on its pedestal,
 * with a beam rising to the star above, ringed by eight wards. Across from the door stands an empty throne,
 * with the Seven's confession before it.
 */
final class Heartwell {
	/** The way down runs along this bearing, from the middle of the Cells. */
	private static final double BEARING = Math.toRadians(Landmarks.centre(Landmarks.CELLS));
	private static final double COS = Math.cos(BEARING);
	private static final double SIN = Math.sin(BEARING);
	/** Feet height down below, and the vault's floor, ceiling and radius. */
	static final int DEEP = F - 10;
	private static final double RADIUS = 14.5;
	/** The stair runs from the Cells' floor (along 70) down to the tunnel (along 60). */
	private static final int STAIR_TOP = 70;
	private static final int STAIR_FOOT = 60;

	private Heartwell() {
	}

	/** Whether the column could hold any of it (the vault, or the way down). */
	static boolean near(double d) {
		return d <= STAIR_TOP + 3;
	}

	static void build(BlockState[] c, int dx, int dz, double d, double deg) {
		double a = dx * COS + dz * SIN;
		double lat = -dx * SIN + dz * COS;
		if (d <= RADIUS) {
			vault(c, dx, dz, d, deg);
		}
		if (a >= 12.0 && a < STAIR_TOP + 1 && Math.abs(lat) <= 2.5) {
			way(c, dx, dz, a, lat);
		}
	}

	private static BlockState candles(int n) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
	}

	/** The stair and tunnel: lined with brick, lit by soul lanterns, a few cobwebs, and warnings on the walls. */
	private static void way(BlockState[] c, int dx, int dz, double a, double lat) {
		int fa = (int) Math.floor(a);
		double p = Math.abs(lat);
		boolean stair = fa >= STAIR_FOOT && fa < STAIR_TOP;
		int feet = stair ? F - (STAIR_TOP - fa) : DEEP;
		if (fa >= STAIR_TOP) {
			return;
		}
		boolean open = feet + 4 > S;
		Direction toLine = lat > 0 ? outward(SIN, -COS) : outward(-SIN, COS);
		if (p <= 1.5) {
			if (open) {
				fill(c, feet, S, AIR);
			} else {
				fill(c, feet, feet + 3, AIR);
				fill(c, feet + 4, S - 1, TILES);
			}
			if (stair) {
				put(c, feet - 1, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, outward(COS, SIN)));
			} else {
				put(c, feet - 1, Math.floorMod(fa, 3) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : BLACK_BRICKS);
				if (p < 0.5 && Math.floorMod(fa, 6) == 0) {
					put(c, feet + 3, HANGING_SOUL);
				}
				if (p > 0.5 && Math.floorMod(fa + (lat > 0 ? 0 : 4), 9) == 4) {
					put(c, feet + 3, Blocks.COBWEB.defaultBlockState());
				}
				if (p > 0.5 && lat > 0) {
					String[] say = fa == 57 ? new String[] {"THE LOWER", "DOOR", "", "mind the dark"} : fa == 35 ? new String[] {"TURN BACK", "OR", "FIGHT ON", ""}
						: fa == 16 ? new String[] {"THE", "HEARTWELL", "", "speak softly"} : null;
					if (say != null) {
						ColosseumInterior.wallSign(c, dx, feet + 1, dz, toLine, DyeColor.WHITE, true, say);
					}
				}
			}
			if (!open && fa == STAIR_TOP - 5) {
				// A rail across the head of the drop, where the floor above ends.
				put(c, S + 1, BLACK_WALL);
			}
		} else {
			fill(c, feet - 1, open ? S - 1 : feet + 4, BRICKS);
			if (open) {
				put(c, S + 1, BLACK_WALL);
			}
		}
	}

	/** The vault: rings and rays in the floor, a lit well in the ceiling, windows round the wall, the seal, the wards and the throne. */
	private static void vault(BlockState[] c, int dx, int dz, double d, double deg) {
		int floor = DEEP - 1;
		int ceiling = DEEP + 6;
		if (d > 12.5) {
			fill(c, floor, ceiling, Blocks.CALCITE.defaultBlockState());
			boolean pilaster = offStep(deg, 30, d) < 0.7;
			boolean window = offStep(deg + 15, 30, d) < 1.3;
			if (pilaster) {
				fill(c, DEEP, ceiling - 1, AMETHYST);
			} else if (window) {
				fill(c, DEEP + 1, DEEP + 4, d < 13.5 ? PURPLE_GLASS : SEA_LANTERN);
			}
			return;
		}
		fill(c, DEEP, ceiling - 1, AIR);
		BlockState floorBlock = d < 1.5 ? CRYING : d < 3 ? AMETHYST : offStep(deg, 45, d) < 0.6 ? GILDED
			: Math.floorMod((int) Math.floor(d / 2), 2) == 0 ? BLACK_BRICKS : Blocks.CALCITE.defaultBlockState();
		put(c, floor, floorBlock);
		put(c, ceiling, d < 1.5 ? SEA_LANTERN : Math.abs(d - 6) < 0.5 ? AMETHYST : offStep(deg, 45, d) < 0.6 ? POLISHED : TILES);
		if (d < 1.5) {
			// The seal, and a beam of light from it to the star.
			put(c, DEEP, CRYING);
			put(c, DEEP + 1, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			put(c, DEEP + 2, AMETHYST);
			fill(c, DEEP + 3, ceiling - 1, Blocks.END_ROD.defaultBlockState());
			return;
		}
		String[] plaque = dx == 0 && dz == -2 ? new String[] {"HERE SLEEPS", "VAELOR", "THE", "UNBROKEN"}
			: dx == 2 && dz == 0 ? new String[] {"BLADE TO", "SHIELD,", "BLOW FOR", "BLOW"}
			: dx == 0 && dz == 2 ? new String[] {"THE CROWD", "IS THE", "KEEPER", ""}
			: dx == -2 && dz == 0 ? new String[] {"YOU ARE", "THE KEY", "", ""} : null;
		if (plaque != null) {
			ColosseumInterior.wallSign(c, dx, DEEP + 1, dz, outward(dx, dz), DyeColor.WHITE, true, plaque);
			return;
		}
		if (Math.abs(d - 6) < 0.5 && offStep(deg, 45, d) < 0.5) {
			// The eight wards round the seal, eyes set.
			put(c, DEEP, Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, outward(-dx, -dz))
				.setValue(EndPortalFrameBlock.HAS_EYE, true));
		} else if (Math.abs(d - 9.5) < 0.5 && offStep(deg + 15, 30, d) < 0.5) {
			put(c, DEEP, candles(4));
		} else if (Math.abs(d - 8) < 0.5 && offStep(deg + 22.5, 45, d) < 0.5) {
			put(c, ceiling - 1, CHAIN);
			put(c, ceiling - 2, HANGING_SOUL);
		}
		// The empty throne, south, facing the seal; the confession before it.
		if (dx == 0 && dz == 10) {
			put(c, DEEP, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		} else if (dx == 0 && dz == 11) {
			fill(c, DEEP, DEEP + 2, Blocks.GOLD_BLOCK.defaultBlockState());
			put(c, DEEP + 3, CRYING);
		} else if (Math.abs(dx) == 1 && dz == 10) {
			put(c, DEEP, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		} else if (Math.abs(dx) == 1 && dz == 11) {
			fill(c, DEEP, DEEP + 1, Blocks.GOLD_BLOCK.defaultBlockState());
		} else if (dx == 0 && dz == 8) {
			ColosseumLore.lectern(c, dx, DEEP, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH)
				.setValue(LecternBlock.HAS_BOOK, true), ColosseumLore.CONFESSION);
		}
	}
}

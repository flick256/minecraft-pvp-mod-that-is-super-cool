package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Four secret vaults, hidden in the piers between the great halls (see {@link GrandHalls}). Each is behind a patch of
 * cracked brick in the pier's face: crouch beside it for a second and it gives way (see {@link HollowCrown}).
 * <ol>
 * <li>Tell's Hidden Study, in the pier beside the Archive: the page Tell left out of the Chronicle.</li>
 * <li>The Masons' Vault, in the pier beside the Forge of Star-Iron: their mark, their tools.</li>
 * <li>The Seventh Seat, in the pier beside the Council of the Seven: one chair, always empty.</li>
 * <li>Vaelor's Armoury, up on the balcony of the Armoury of the Seven: his oath, and the stand his plate hung on.</li>
 * </ol>
 */
final class Vaults {
	static final String[][] NAMES = {{"TELL'S", "HIDDEN STUDY"}, {"THE MASONS'", "VAULT"}, {"THE SEVENTH", "SEAT"}, {"VAELOR'S", "ARMOURY"}};
	/** Each vault's Gallery, the pier it is in (the one at 45 + 90 times this, in degrees), and which side opens (+1: sunwise). */
	private static final int[] GALLERY = {0, 1, 2, 0};
	private static final int[] PIER = {0, 0, 3, 0};
	private static final int[] SIDE = {1, -1, -1, -1};
	/** How far out the vault is from its Gallery's inner wall, and its floor's height above the hall's. */
	private static final double[] AT = {7, 22.5, 0.5, 17};
	private static final int[] UP = {0, 0, 0, GrandHalls.B1};
	/** The vault's length (out from the Middle) and width (across the pier). */
	static final double LONG = 5;
	static final double WIDE = 2.5;

	private Vaults() {
	}

	private static double angle(int q) {
		return Math.toRadians(45 + 90 * PIER[q]);
	}

	/** The vault's floor-level feet height. */
	static int feet(int q) {
		return F + UP[q];
	}

	private static double radius(int q) {
		return ColosseumInterior.ROOMS[GALLERY[q]][0] + AT[q];
	}

	/** The middle of the way in (blueprint dx, the lower block's y, dz): on the hall's side of the pier. */
	static int[] entrance(int q) {
		double r = radius(q) + LONG / 2;
		double a = angle(q) + SIDE[q] * (GrandHalls.PIER - 0.5) / r;
		return new int[] {(int) Math.floor(Math.cos(a) * r + 0.5), feet(q), (int) Math.floor(Math.sin(a) * r + 0.5)};
	}

	/** The middle of the vault (blueprint dx, feet y, dz). */
	static double[] centre(int q) {
		double r = radius(q) + LONG / 2;
		return new double[] {Math.cos(angle(q)) * r, feet(q), Math.sin(angle(q)) * r};
	}

	/** One column of a pier: the vault in it, if any, and its cracked way in. */
	static void build(BlockState[] c, int dx, int dz, double d, int gallery, int pierQuarter) {
		for (int q = 0; q < 4; q++) {
			if (GALLERY[q] != gallery || PIER[q] != pierQuarter) {
				continue;
			}
			double along = d - radius(q);
			double side = (Math.atan2(dz, dx) - angle(q));
			side = Math.atan2(Math.sin(side), Math.cos(side)) * d * SIDE[q];
			if (along < -0.5 || along > LONG + 0.5) {
				continue;
			}
			int y0 = feet(q);
			if (side > WIDE && side <= GrandHalls.PIER && Math.abs(along - LONG / 2) < 1.5) {
				// The way in: cracked brick, two high.
				put(c, y0, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
				put(c, y0 + 1, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
				continue;
			}
			if (Math.abs(side) > WIDE || along < 0 || along > LONG) {
				continue;
			}
			for (int y = y0; y <= y0 + 3; y++) {
				put(c, y, AIR);
			}
			put(c, y0 - 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			Direction toDoor = SIDE[q] > 0 ? outward(-Math.sin(angle(q)), Math.cos(angle(q))) : outward(Math.sin(angle(q)), -Math.cos(angle(q)));
			furnish(c, dx, dz, q, along, side, y0, toDoor, outward(dx, dz));
		}
	}

	private static void furnish(BlockState[] c, int dx, int dz, int q, double along, double side, int y0, Direction toDoor, Direction out) {
		boolean back = side < -WIDE + 1;
		boolean middle = Math.abs(along - LONG / 2) < 0.5;
		if (middle && Math.abs(side) < 0.5) {
			put(c, y0 + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		switch (q) {
			case 0 -> {
				if (back && middle) {
					ColosseumLore.lectern(c, dx, y0, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toDoor).setValue(LecternBlock.HAS_BOOK,
						true), ColosseumLore.TELL_PAGE);
				} else if (back || along < 0.5 || along > LONG - 0.5) {
					put(c, y0, Blocks.BOOKSHELF.defaultBlockState());
					put(c, y0 + 1, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
				} else if (Math.abs(side) < 0.5 && along > LONG - 1.5) {
					put(c, y0, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
				}
			}
			case 1 -> {
				if (back && middle) {
					ColosseumLore.lectern(c, dx, y0, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toDoor).setValue(LecternBlock.HAS_BOOK,
						true), ColosseumLore.MASONS_MARK);
				} else if (back) {
					put(c, y0, Blocks.STONECUTTER.defaultBlockState());
				} else if (along < 0.5 || along > LONG - 0.5) {
					put(c, y0, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
					put(c, y0 + 1, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
				}
			}
			case 2 -> {
				if (back && middle) {
					put(c, y0, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toDoor.getOpposite()));
					put(c, y0 + 1, Blocks.GOLD_BLOCK.defaultBlockState());
				} else if (Math.abs(side) < 0.5 && Math.abs(along - LONG / 2 - 1) < 0.5) {
					ColosseumLore.lectern(c, dx, y0, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toDoor).setValue(LecternBlock.HAS_BOOK,
						true), ColosseumLore.SEVENTH_SEAT);
				} else if (back) {
					put(c, y0, Blocks.DYED_CANDLE.purple().defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
				}
			}
			default -> {
				if (back && middle) {
					ColosseumLore.lectern(c, dx, y0, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toDoor).setValue(LecternBlock.HAS_BOOK,
						true), ColosseumLore.VAELOR_OATH);
				} else if (back && Math.abs(along - LONG / 2) < 1.6) {
					put(c, y0, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					put(c, y0 + 1, Blocks.GILDED_BLACKSTONE.defaultBlockState());
				} else if (along < 0.5 || along > LONG - 0.5) {
					put(c, y0, Blocks.ANVIL.defaultBlockState());
				}
			}
		}
	}
}

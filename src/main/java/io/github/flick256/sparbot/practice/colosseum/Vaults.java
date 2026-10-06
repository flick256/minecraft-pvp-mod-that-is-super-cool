package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.fill;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.candles;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.face;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.hanging;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.slab;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.st;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.stair;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The four secret vaults, sealed in the solid stone behind the Third Gallery's stair halls. Each is behind a
 * patch of cracked brick in the wall at the back of one landing, with a sign beside it ("the stone here sounds
 * hollow / kneel and listen"). A player who crouches before it opens it (see {@link HollowCrown}). It closes
 * again when the colosseum is put back after everyone has left.
 *
 * <ol>
 * <li>Tell's Hidden Study (south-east stair, landing 2): where the Archivist hid the truth.</li>
 * <li>The Masons' Vault (south-west, landing 4): the builders' model of the bowl, and their mark.</li>
 * <li>The Seventh Seat (north-west, landing 6): seven chairs, one of them always empty.</li>
 * <li>Vaelor's Armoury (north-east, landing 8): the Unbroken's own gear, waiting.</li>
 * </ol>
 */
final class Vaults {
	/** The landing (storey) each vault opens from, by quarter. */
	static final int[] LEVEL = {1, 3, 5, 7};
	static final String[][] NAMES = {{"TELL'S", "HIDDEN STUDY"}, {"THE MASONS'", "VAULT"}, {"THE SEVENTH", "SEAT"}, {"VAELOR'S", "ARMOURY"}};
	/** Where the vault starts behind the wall, and how deep it is, from the Gallery's inner wall. */
	static final double START = 12;
	static final double DEPTH = 9.5;

	private Vaults() {
	}

	/** The vault's floor block (level with the landing it opens from). */
	static int floorY(int q) {
		return F + ColosseumInterior.STOREY * LEVEL[q] + 3;
	}

	static double angle(int q) {
		return Math.toRadians((StairHall.SECTORS[q] + 0.5) * ColosseumInterior.SECTOR);
	}

	/** The middle of the way in (blueprint dx, the lower block's y, dz). */
	static int[] entrance(int q) {
		double d = ColosseumInterior.ROOMS[2][0] + 11.5;
		return new int[] {(int) Math.floor(Math.cos(angle(q)) * d + 0.5), floorY(q) + 1, (int) Math.floor(Math.sin(angle(q)) * d + 0.5)};
	}

	/** The middle of the vault (blueprint dx, floor y, dz). */
	static double[] centre(int q) {
		double d = ColosseumInterior.ROOMS[2][0] + START + DEPTH / 2;
		return new double[] {Math.cos(angle(q)) * d, floorY(q), Math.sin(angle(q)) * d};
	}

	/** One storey of the wall behind a Third Gallery stair: the cracked patch of the way in, where there is one. */
	static void door(BlockState[] c, int band, int sector, int k, double v) {
		if (band != 2) {
			return;
		}
		int q = StairHall.quarter(sector);
		if (k == LEVEL[q] && Math.abs(v) < 1.0) {
			fill(c, floorY(q) + 1, floorY(q) + 2, st(Blocks.CRACKED_DEEPSLATE_BRICKS));
		}
	}

	/** The sign on the landing beside the way in (a landing column of the stair's core). */
	static void hint(BlockState[] c, int dx, int dz, int band, int sector, double v) {
		if (band != 2 || v > -1.0 || v < -2.0) {
			return;
		}
		int q = StairHall.quarter(sector);
		ColosseumInterior.wallSign(c, dx, floorY(q) + 2, dz, outward(-dx, -dz), DyeColor.GRAY, false, "THE STONE", "HERE SOUNDS", "HOLLOW", "(kneel, listen)");
	}

	/** One column of the solid stone behind a stair: the vault, if the column is in one. */
	static void build(BlockState[] c, int dx, int dz, int band, int sector, double u, double v, double hw) {
		if (band != 2) {
			return;
		}
		int q = StairHall.quarter(sector);
		double vu = u - START;
		double av = Math.abs(v);
		double half = hw - 1.0;
		if (vu < 0 || vu >= DEPTH || av >= half) {
			return;
		}
		int y0 = floorY(q);
		fill(c, y0 + 1, y0 + 6, AIR);
		Direction in = outward(-dx, -dz);
		Direction out = in.getOpposite();
		Direction plus = outward(-dz, dx);
		Direction minus = plus.getOpposite();
		Direction toSide = v > 0 ? plus : minus;
		Direction fromSide = toSide.getOpposite();
		boolean side = half - av < 1.0;
		boolean back = DEPTH - vu < 1.0;
		int iu = (int) Math.floor(vu);
		int iv = (int) Math.round(v);
		// Shared: lanterns hung in two rows, the name over the way in.
		if (Math.floorMod(iu, 4) == 2 && Math.abs(av - half / 2) < 0.5) {
			put(c, y0 + 6, hanging(q == 3 ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
		}
		if (vu < 1 && av >= 1 && av < 2 && v > 0) {
			ColosseumInterior.wallSign(c, dx, y0 + 3, dz, out, DyeColor.YELLOW, true, NAMES[q][0], NAMES[q][1], "", "found at last");
		}
		switch (q) {
			case 0 -> study(c, dx, dz, y0, vu, v, av, half, side, back, iu, iv, in, out, fromSide);
			case 1 -> masons(c, dx, dz, y0, vu, v, av, half, side, back, iu, iv, in, out, fromSide);
			case 2 -> seats(c, dx, dz, y0, vu, v, av, half, side, back, iu, in, out);
			default -> armoury(c, dx, dz, y0, vu, v, av, half, side, back, iu, iv, in, out, fromSide);
		}
		// The Fragment of Aster on its pedestal in the middle of each (its shard is the reward for finding it).
		if (Math.abs(vu - DEPTH / 2) < 0.5 && av < 0.5) {
			put(c, y0 + 1, st(Blocks.CHISELED_QUARTZ_BLOCK));
			put(c, y0 + 2, st(Blocks.BUDDING_AMETHYST));
			put(c, y0 + 3, face(Blocks.AMETHYST_CLUSTER, Direction.UP));
		}
	}

	private static BlockState lectern(Direction f) {
		return st(Blocks.LECTERN).setValue(LecternBlock.FACING, f).setValue(LecternBlock.HAS_BOOK, true);
	}

	/** Tell's study: shelves to the ceiling, a desk under a star map, the last page of the Chronicle, candles everywhere. */
	private static void study(BlockState[] c, int dx, int dz, int y0, double vu, double v, double av, double half, boolean side, boolean back, int iu,
		int iv, Direction in, Direction out, Direction fromSide) {
		put(c, y0, Math.floorMod(iu + iv, 2) == 0 ? st(Blocks.DARK_OAK_PLANKS) : st(Blocks.STRIPPED_DARK_OAK_LOG));
		if (side || back) {
			for (int y = y0 + 1; y <= y0 + 6; y++) {
				put(c, y, (y + iu + iv) % 3 == 0 ? st(Blocks.CHISELED_BOOKSHELF) : st(Blocks.BOOKSHELF));
			}
			return;
		}
		if (vu >= DEPTH - 2.5 && av < 0.5) {
			ColosseumLore.lectern(c, dx, y0 + 1, dz, lectern(in), ColosseumLore.TELL_PAGE);
		} else if (vu >= DEPTH - 2.5 && av < 2.5) {
			put(c, y0 + 1, slab(Blocks.DARK_OAK_SLAB, true));
			put(c, y0 + 2, candles(Blocks.CANDLE, 1 + Math.floorMod(iv, 4)));
		} else if (vu < 3 && av >= 2 && Math.floorMod(iu, 2) == 0) {
			put(c, y0 + 1, candles(Blocks.DYED_CANDLE.pick(DyeColor.PURPLE), 3));
		}
		// The star map on the ceiling.
		if ((iu * 7 + iv * 3) % 5 == 0) {
			put(c, y0 + 7, st(Blocks.GLOWSTONE));
		} else {
			put(c, y0 + 7, st(Blocks.LAPIS_BLOCK));
		}
	}

	/** The masons' vault: their model of the bowl on a great table, tools hung on the walls, the blueprint banners, their mark. */
	private static void masons(BlockState[] c, int dx, int dz, int y0, double vu, double v, double av, double half, boolean side, boolean back, int iu,
		int iv, Direction in, Direction out, Direction fromSide) {
		put(c, y0, Math.floorMod(iu + iv, 2) == 0 ? st(Blocks.POLISHED_TUFF) : st(Blocks.TUFF_BRICKS));
		if (side) {
			int i = Math.floorMod(iu, 3);
			put(c, y0 + 1, i == 0 ? st(Blocks.STONECUTTER) : i == 1 ? face(Blocks.GRINDSTONE, fromSide) : st(Blocks.SMITHING_TABLE));
			put(c, y0 + 3, i == 1 ? face(Blocks.WALL_BANNER.pick(DyeColor.BLUE), fromSide) : face(Blocks.TRIPWIRE_HOOK, fromSide));
			return;
		}
		if (back) {
			put(c, y0 + 3, face(Blocks.WALL_BANNER.pick(DyeColor.LIGHT_BLUE), in));
			put(c, y0 + 1, face(Blocks.CHEST, in));
			return;
		}
		if (vu >= DEPTH - 2.5 && av < 0.5) {
			ColosseumLore.lectern(c, dx, y0 + 1, dz, lectern(in), ColosseumLore.MASONS_MARK);
			return;
		}
		// The model: rings of the bowl on a table round the pedestal, stools round it.
		double mu = vu - DEPTH / 2;
		double r = Math.hypot(mu, v);
		if (r >= 0.5 && r < 3.1) {
			put(c, y0 + 1, slab(Blocks.SPRUCE_SLAB, true));
			put(c, y0 + 2, r < 1.5 ? st(Blocks.CARPET.pick(DyeColor.LIME)) : r < 2.3 ? st(Blocks.DEEPSLATE_TILE_SLAB) : st(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
		} else if (r >= 3.1 && r < 4.1 && Math.floorMod(iu + iv, 2) == 0) {
			Direction away = Math.abs(mu) > av ? (mu > 0 ? out : in) : (v > 0 ? outward(-dz, dx) : outward(dz, -dx));
			put(c, y0 + 1, stair(Blocks.SPRUCE_STAIRS, away));
		}
	}

	/** The Seventh Seat: seven chairs round the pedestal, gold under each, banners of the Seven, and one seat cracked and empty. */
	private static void seats(BlockState[] c, int dx, int dz, int y0, double vu, double v, double av, double half, boolean side, boolean back, int iu,
		Direction in, Direction out) {
		put(c, y0, av < 1.0 ? st(Blocks.GILDED_BLACKSTONE) : st(Blocks.POLISHED_BLACKSTONE));
		double mu = vu - DEPTH / 2;
		double r = Math.hypot(mu, v);
		double ang = Math.toDegrees(Math.atan2(v, mu));
		if (side || back) {
			put(c, y0 + 3, face(Blocks.WALL_BANNER.pick(DyeColor.byId(Math.floorMod(iu * 3 + (v > 0 ? 1 : 0), 16))), side ? (v > 0 ? outward(dz, -dx) : outward(-dz, dx)) : in));
			return;
		}
		if (Math.abs(r - 3.2) < 0.6) {
			// Seven seats, every fifty-one degrees or so, facing the pedestal.
			int seat = (int) Math.floor((ang + 180) / (360.0 / 7) + 0.5) % 7;
			double at = seat * 360.0 / 7 - 180;
			double off = Math.abs(((ang - at + 540) % 360) - 180);
			if (off < 12) {
				Direction toward = Math.abs(mu) > av ? (mu > 0 ? in : out) : (v > 0 ? outward(dz, -dx) : outward(-dz, dx));
				put(c, y0 + 1, seat == 6 ? st(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : stair(Blocks.POLISHED_BLACKSTONE_STAIRS, toward.getOpposite()));
				if (seat != 6) {
					put(c, y0, st(Blocks.GOLD_BLOCK));
				}
			}
		} else if (vu >= DEPTH - 2.5 && av < 0.5) {
			ColosseumLore.lectern(c, dx, y0 + 1, dz, lectern(in), ColosseumLore.SEVENTH_SEAT);
		} else if (r < 1.6 && r >= 0.5) {
			put(c, y0 + 1, candles(Blocks.DYED_CANDLE.pick(DyeColor.PURPLE), 4));
		}
	}

	/** Vaelor's armoury: his racks, his black banner, a pedestal of netherite, and his oath on a lectern. */
	private static void armoury(BlockState[] c, int dx, int dz, int y0, double vu, double v, double av, double half, boolean side, boolean back,
		int iu, int iv, Direction in, Direction out, Direction fromSide) {
		put(c, y0, Math.floorMod(iu + iv, 2) == 0 ? st(Blocks.RED_NETHER_BRICKS) : st(Blocks.POLISHED_BLACKSTONE_BRICKS));
		if (side) {
			int i = Math.floorMod(iu, 3);
			if (i == 0) {
				put(c, y0 + 1, st(Blocks.POLISHED_BLACKSTONE_WALL));
				put(c, y0 + 2, st(Blocks.NETHERITE_BLOCK));
				put(c, y0 + 3, st(Blocks.WITHER_SKELETON_SKULL));
			} else {
				put(c, y0 + 1, face(Blocks.BARREL, Direction.UP));
				put(c, y0 + 2, face(Blocks.END_ROD, Direction.UP));
				put(c, y0 + 4, face(Blocks.WALL_BANNER.pick(DyeColor.BLACK), fromSide));
			}
			return;
		}
		if (back) {
			put(c, y0 + 2, face(Blocks.WALL_BANNER.pick(av < 0.5 ? DyeColor.PURPLE : DyeColor.BLACK), in));
			put(c, y0 + 1, av < 0.5 ? st(Blocks.CRYING_OBSIDIAN) : st(Blocks.ANVIL));
			return;
		}
		if (vu >= DEPTH - 2.5 && av < 0.5) {
			ColosseumLore.lectern(c, dx, y0 + 1, dz, lectern(in), ColosseumLore.VAELOR_OATH);
		} else if (Math.floorMod(iu, 3) == 1 && Math.abs(av - half / 2) < 0.5) {
			put(c, y0 + 1, st(Blocks.SMITHING_TABLE));
			put(c, y0 + 2, candles(Blocks.DYED_CANDLE.pick(DyeColor.RED), 2));
		}
	}

}

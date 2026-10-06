package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_WALL;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHISELED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.POLISHED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPUR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.TILES;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.fill;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;
import static io.github.flick256.sparbot.practice.colosseum.ColosseumInterior.STOREY;

import io.github.flick256.sparbot.practice.colosseum.ColosseumInterior.Room;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stair halls: four sectors in each Gallery (at the four diagonals) given over to a switchback stair from
 * the ground to the Gallery's top level. They are the only way up inside the stands, so they never stand in a
 * corridor's way.
 *
 * <p>Each storey has a lobby by its door. Beyond the lobby the stair's core runs straight away from the door.
 * The up flight climbs four steps in one lane, then comes a landing across the whole hall, then the next flight
 * climbs four steps back in the other lane. It comes out on the next floor beside the foot of the next flight up,
 * so the two lanes take turns. A low wall between them carries lights, and a rail closes the well on the top
 * floor.
 *
 * <p>Where the lobby is, by Gallery:
 * <ul>
 * <li>First Gallery: at the outer end, off the Middle Ring (the Inner Ring has only a ground floor). Up to level 4.</li>
 * <li>Second Gallery: at the inner end, off the Middle Ring, up to level 5. Behind the stair, an annex hall opens
 * onto the Outer Ring.</li>
 * <li>Third Gallery: at the inner end, off the Outer Ring, up to level 9 (the Outer Ring's top). The solid stone
 * behind it hides the secret vaults (see {@link Vaults}).</li>
 * </ul>
 */
final class StairHall {
	static final int[] SECTORS = {6, 18, 30, 42};
	private static final String[] QUARTERS = {"SOUTH-EAST", "SOUTH-WEST", "NORTH-WEST", "NORTH-EAST"};
	/** The core's length, from the arrival end: four steps, then a three-deep landing. */
	static final int LENGTH = 7;

	private StairHall() {
	}

	static boolean is(int band, int sector) {
		for (int s : SECTORS) {
			if (s == sector) {
				return band >= 0;
			}
		}
		return false;
	}

	/** Which of the four quarters (0-3) a stair sector is in. */
	static int quarter(int sector) {
		return Math.floorMod(sector / 12, 4);
	}

	/** The top storey the stair reaches in each Gallery. */
	static int top(int band) {
		return band == 0 ? 3 : band == 1 ? 4 : 8;
	}

	/** Whether the lobby is at the inner end (Second and Third Galleries) or the outer end (First). */
	static boolean lobbyInner(int band) {
		return band != 0;
	}

	/** The core's coordinate from its arrival end (by the lobby), 0 to {@link #LENGTH}, for a column {@code u} into the band. */
	static double w(int band, double u) {
		return band == 0 ? 17 - u : u - 4;
	}

	/** Where in the hall a column is: the lobby, the core, the wall behind it, or what lies beyond. */
	enum Part { LOBBY, CORE, END, BEYOND }

	static Part part(int band, double u) {
		double w = w(band, u);
		if (w < 0) {
			return Part.LOBBY;
		}
		if (w < LENGTH) {
			return Part.CORE;
		}
		if (band != 0 && w < LENGTH + 1) {
			return Part.END;
		}
		return Part.BEYOND;
	}

	/** Whether the hall wants a door on that side at storey {@code k} (the annex behind the Second Gallery's stair opens outward). */
	static boolean door(int band, int k, boolean innerSide) {
		if (band == 0) {
			return !innerSide && k <= top(0);
		}
		if (band == 1) {
			return !innerSide || k <= top(1);
		}
		return innerSide && k <= top(2);
	}

	/** The name over the hall's door. */
	static String[] name(int sector) {
		return new String[] {"THE " + QUARTERS[quarter(sector)], "STAIR"};
	}

	private static BlockState step(Block block, Direction facing) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	/**
	 * The whole height of one column of the core, every storey at once (the flights cross the floors). {@code v}
	 * is across the hall from its middle line.
	 */
	static void core(BlockState[] c, int dx, int dz, int band, int sector, double u, double v) {
		int top = top(band);
		double w = w(band, u);
		int iw = Math.min(LENGTH - 1, (int) Math.floor(w));
		double av = Math.abs(v);
		Direction out = outward(dx, dz);
		Direction away = lobbyInner(band) ? out : out.getOpposite();
		Direction toward = away.getOpposite();
		int topFeet = F + STOREY * top;
		Block flight = band == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : band == 1 ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
		if (iw >= 4) {
			// The landings, across the hall, each with a lantern over it.
			for (int k = 0; k < top; k++) {
				int f = F + STOREY * k;
				fill(c, f - 1, f + 3, k % 2 == 0 ? BRICKS : TILES);
				put(c, f + 3, av < 1.0 ? PURPUR : iw == 6 ? CHISELED : POLISHED);
				if (av < 0.5 && iw == 5) {
					put(c, f + 6, LANTERN.setValue(LanternBlock.HANGING, true));
				}
			}
			if (iw == 6) {
				Vaults.hint(c, dx, dz, band, sector, v);
			}
			put(c, topFeet - 1, POLISHED);
			if (v > 0.75 && iw == 4) {
				put(c, topFeet, BLACK_WALL);
			}
			return;
		}
		if (av <= 0.75) {
			// The wall between the flights, with lights set in it; on the top floor, a rail round the well.
			fill(c, F, topFeet - 1, BLACK_BRICKS);
			for (int k = 0; k < top; k++) {
				if (iw == 2) {
					put(c, F + STOREY * k + 5, SEA_LANTERN);
				} else if (iw == 1) {
					put(c, F + STOREY * k + 1, SEA_LANTERN);
				} else if (iw == 3) {
					put(c, F + STOREY * k + 3, Blocks.GILDED_BLACKSTONE.defaultBlockState());
				}
			}
			if (iw == 0) {
				fill(c, topFeet, topFeet + 2, BLACK_BRICKS);
				put(c, topFeet + 3, LANTERN);
			} else {
				put(c, topFeet, BLACK_WALL);
			}
			return;
		}
		if (v < 0) {
			// The up flights, climbing away from the lobby, solid underneath.
			for (int k = 0; k < top; k++) {
				int f = F + STOREY * k;
				fill(c, f - 1, f + iw - 1, BRICKS);
				put(c, f + iw, step(av < 1.75 ? Blocks.PURPUR_STAIRS : flight, away));
				if (iw == 2 && Math.abs(av - 2.25) < 0.5) {
					// A lantern hung under the flight above.
					put(c, f + 6, LANTERN.setValue(LanternBlock.HANGING, true));
				}
			}
			put(c, topFeet - 1, POLISHED);
			return;
		}
		// The flights back, climbing toward the lobby and coming out on the next floor (the well left open below them).
		int j = 3 - iw;
		for (int k = 0; k < top; k++) {
			int f = F + STOREY * k;
			if (j == 3) {
				put(c, f + 7, POLISHED);
			} else {
				put(c, f + 3 + j, BRICKS);
				put(c, f + 4 + j, step(av < 1.75 ? Blocks.PURPUR_STAIRS : flight, toward));
				if (j == 2 && k + 1 < top && Math.abs(av - 2.25) < 0.5) {
					put(c, f + 12, LANTERN.setValue(LanternBlock.HANGING, true));
				}
			}
		}
		if (j < 3 && av > 1.75) {
			// Stores tucked under the first flight.
			put(c, F, j == 1 ? Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, toward)
				: Blocks.BARREL.defaultBlockState());
		}
	}

	/** One storey of the wall behind the core (Second and Third Galleries): solid, with a light at each landing. */
	static void end(BlockState[] c, int feet, int ceiling) {
		fill(c, feet - 1, ceiling - 1, BRICKS);
	}

	/**
	 * One column of a lobby: a patterned floor, the floor's number on the wall between the flights, banners in
	 * the hall's colours, benches along the walls and lanterns.
	 */
	static void lobby(Room r, int band) {
		int quarter = quarter(r.sector);
		BlockState[] colours = {Blocks.WALL_BANNER.orange().defaultBlockState(), Blocks.WALL_BANNER.lightBlue().defaultBlockState(),
			Blocks.WALL_BANNER.purple().defaultBlockState(), Blocks.WALL_BANNER.lime().defaultBlockState()};
		BlockState[] glazed = {Blocks.GLAZED_TERRACOTTA.orange().defaultBlockState(), Blocks.GLAZED_TERRACOTTA.lightBlue().defaultBlockState(),
			Blocks.GLAZED_TERRACOTTA.purple().defaultBlockState(), Blocks.GLAZED_TERRACOTTA.lime().defaultBlockState()};
		double w = w(band, r.u);
		r.put(r.floor(), r.av < 0.75 ? glazed[quarter] : Math.floorMod(r.iu + r.iv, 2) == 0 ? POLISHED : TILES);
		if (r.doorway) {
			return;
		}
		Direction toLobby = lobbyInner(band) ? r.in : r.out;
		int top = top(band);
		if (w >= -1 && r.av < 0.5) {
			// On the wall between the flights: which floor this is.
			String here = "LEVEL " + (r.k + 1);
			String up = r.k < top ? "up: level " + (r.k + 2) : "the top";
			String down = r.k > 0 ? "down: level " + r.k : "the ground";
			r.sign(r.k < top ? r.feet + 2 : r.feet + 1, toLobby, DyeColor.YELLOW, true, here, name(r.sector)[0] + " STAIR", up, down);
		} else if (r.side && w >= -1) {
			r.put(r.feet + 3, colours[quarter].setValue(WallBannerBlock.FACING, r.awayFromSide));
		} else if (r.side && Math.floorMod(r.iu, 2) == 0) {
			r.put(r.feet, step(Blocks.DARK_OAK_STAIRS, r.towardSide));
			r.put(r.feet + 2, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, r.awayFromSide));
		} else if (r.side) {
			r.put(r.feet, Blocks.POTTED_FERN.defaultBlockState());
		} else if (r.av >= 0.5 && r.av < 1.5 && w >= -1) {
			// Lamp posts either side of the way up.
			r.put(r.feet, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			r.put(r.feet + 1, LANTERN);
		}
		if (band == 2 && r.k == Vaults.LEVEL[quarter] && r.side && r.v > 0 && w >= -3 && w < -2) {
			// The rumour that leads to the vault on the landing above.
			r.sign(r.feet + 2, r.awayFromSide, DyeColor.GRAY, false, "THEY SAY THE", "NEXT LANDING", "UP SOUNDS", "HOLLOW");
		}
		if (r.av < 0.5 && w < -1.5 && w >= -2.5) {
			ColosseumInterior.hang(r.c, r.feet, r.ceiling, LANTERN.setValue(LanternBlock.HANGING, true));
		}
	}
}

package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACKSTONE;
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
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.S;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.TILES;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.fill;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The inside of the Grand Bowl's stands, under the seats: what visitors walk through to reach them.
 *
 * <p>The space under each tier is split into storeys every {@value #STOREY} blocks. Three ring corridors run
 * all the way round (behind the podium, under each concourse), and between them the stands are divided into
 * forty-eight sectors of rooms, each a different kind: feast halls, armouries, libraries, shrines, training
 * rooms, treasuries, alchemists' rooms, lounges and storerooms, all lit so nothing spawns. Doors join every
 * room to the corridors and to its neighbours; spiral staircases in the corridors climb from the ground to
 * the two concourses, where steps lead on to the seats. Each gate passage is a vaulted hall with doors into
 * the corridors it crosses.
 */
final class ColosseumInterior {
	/** Storey height: floor to floor. */
	static final int STOREY = 8;
	/** Room sectors, in degrees (48 of them). */
	static final double SECTOR = 7.5;
	/** The ring corridors and the room bands, by distance from the centre (between them, one-block walls). */
	static final double[][] CORRIDORS = {{50.5, 56.5}, {80.5, 86.5}, {116.5, 122.5}};
	static final double[][] ROOMS = {{57.5, 79.5}, {87.5, 115.5}, {123.5, 146.5}};
	/** Spiral staircases: in the two outer corridors, up to the concourse floors above them. */
	static final double[] STAIR_RING = {83.5, 119.0};
	static final int[] STAIR_TOP = {F + 42, F + 76};
	static final double STAIR_R = 2.4;

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

	private static int band(double[][] bands, double d) {
		for (int i = 0; i < bands.length; i++) {
			if (d > bands[i][0] && d <= bands[i][1]) {
				return i;
			}
		}
		return -1;
	}

	static void build(BlockState[] c, int dx, int dz, double d, double deg) {
		int u = underside(d);
		if (u < 0) {
			return;
		}
		fill(c, F, u - 1, AIR);
		CelestialColosseum.Gate gate = CelestialColosseum.gate(dx, dz, 6.5);
		if (gate != null) {
			passage(c, d, gate, u);
			return;
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
		double sectorDeg = Math.floorMod((long) Math.floor((deg + 360) * 1000), (long) (SECTOR * 1000)) / 1000.0;
		double v = (sectorDeg - SECTOR / 2) * Math.PI / 180 * d;
		double halfW = SECTOR / 2 * Math.PI / 180 * d;
		int corridor = band(CORRIDORS, d);
		int rooms = band(ROOMS, d);
		int sector = (int) Math.floor(((deg % 360) + 360) % 360 / SECTOR);
		boolean radialWall = rooms >= 0 && halfW - Math.abs(v) < 0.5;
		for (int k = 0; storey(d, k); k++) {
			int feet = F + STOREY * k;
			int ceiling = storey(d, k + 1) ? feet + STOREY - 1 : u;
			if (k > 0) {
				put(c, feet - 1, k % 2 == 0 ? TILES : POLISHED);
			}
			if (corridor < 0 && rooms < 0) {
				// A ring wall, with a door at the middle of each sector where there is floor on both sides.
				fill(c, feet, ceiling - 1, BRICKS);
				if (Math.abs(v) <= 1.0 && storey(d - 1, k) && storey(d + 1, k)) {
					fill(c, feet, feet + 2, AIR);
					put(c, feet + 3, CHISELED);
				}
			} else if (radialWall) {
				fill(c, feet, ceiling - 1, BRICKS);
				double mid = (ROOMS[rooms][0] + ROOMS[rooms][1]) / 2;
				if (Math.abs(d - mid) <= 1.0) {
					fill(c, feet, feet + 2, AIR);
					put(c, feet + 3, CHISELED);
				}
			} else if (corridor >= 0) {
				corridor(c, dx, dz, d, deg, CORRIDORS[corridor], feet, ceiling, k);
			} else {
				double inner = ROOMS[rooms][0];
				double depth = ROOMS[rooms][1] - inner;
				int kind = PracticeLayout.scatter(sector * 7 + rooms, k * 13 + 5, 77) % KINDS;
				room(c, dx, dz, kind, d - inner, depth, v, halfW - 0.5, feet, ceiling, sector, k);
			}
		}
	}

	// --- Gate passages ---

	/** A gate's passage: a vaulted hall twelve high, lit down the middle, walled off with doors into the corridors it crosses. */
	private static void passage(BlockState[] c, double d, CelestialColosseum.Gate gate, int u) {
		int top = Math.min(F + 11, u - 1);
		if (gate.perp() <= 5.5) {
			fill(c, top + 1, u - 1, BRICKS);
			put(c, top + 1, gate.perp() < 1.5 ? POLISHED : TILES);
			put(c, S, gate.perp() < 0.5 ? CHISELED : POLISHED);
			if (gate.perp() < 0.5 && Math.floorMod(Math.round(gate.along()), 6) == 0) {
				put(c, top, HANGING_SOUL);
			}
		} else {
			fill(c, F, u - 1, TILES);
			if (band(CORRIDORS, d) >= 0 && band(CORRIDORS, d - 1) >= 0 && band(CORRIDORS, d + 1) >= 0) {
				fill(c, F, F + 2, AIR);
				put(c, F + 3, CHISELED);
			} else if (Math.floorMod(Math.round(gate.along()), 6) == 3) {
				put(c, F + 3, SEA_LANTERN);
			}
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
			put(c, F + STOREY * k + s, (k + s) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : POLISHED);
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

	private static void corridor(BlockState[] c, int dx, int dz, double d, double deg, double[] band, int feet, int ceiling, int k) {
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
		// Banners on the walls, purple and cyan in turn; benches between them on the ground floor.
		boolean innerEdge = d - band[0] < 1.0;
		boolean outerEdge = band[1] - d < 1.0;
		if ((innerEdge || outerEdge) && Math.floorMod(arc, 12) == 6) {
			Direction facing = innerEdge ? outward(dx, dz) : outward(-dx, -dz);
			Block banner = Math.floorMod(arc / 12, 2) == 0 ? Blocks.WALL_BANNER.purple() : Blocks.WALL_BANNER.cyan();
			put(c, feet + 3, banner.defaultBlockState().setValue(WallBannerBlock.FACING, facing));
		} else if (k == 0 && (innerEdge || outerEdge) && Math.floorMod(arc, 12) >= 9 && Math.floorMod(arc, 12) <= 10) {
			put(c, feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, innerEdge ? outward(-dx, -dz) : outward(dx, dz)));
		}
	}

	/** A lantern hung from the ceiling: straight under it, or on a chain where the room is tall. */
	private static void hang(BlockState[] c, int feet, int ceiling, BlockState lantern) {
		if (ceiling - feet > STOREY) {
			fill(c, feet + 6, ceiling - 1, CHAIN);
			put(c, feet + 5, lantern);
		} else {
			put(c, ceiling - 1, lantern);
		}
	}

	// --- Rooms ---

	static final int KINDS = 9;

	/**
	 * One column of a room: {@code u} from its inner wall (0 to {@code depth}), {@code v} across it from its
	 * middle ({@code halfW} to each side wall), standing at {@code feet} under {@code ceiling}.
	 */
	private static void room(BlockState[] c, int dx, int dz, int kind, double u, double depth, double v, double halfW, int feet, int ceiling,
		int sector, int k) {
		int floor = feet - 1;
		int iu = (int) Math.floor(u);
		int iv = (int) Math.round(v);
		double av = Math.abs(v);
		boolean outer = depth - u < 1.0;
		boolean inner = u < 1.0;
		boolean side = halfW - av < 1.0;
		// Keep the doors clear: the middle of the inner and outer walls, and the middle of the side walls.
		boolean doorway = av <= 1.6 && (u < 2.0 || depth - u < 2.0) || Math.abs(u - depth / 2) <= 1.6 && halfW - av < 2.0;
		Direction in = outward(-dx, -dz);
		Direction out = outward(dx, dz);
		Direction acrossPlus = outward(-dz, dx);
		Direction acrossMinus = outward(dz, -dx);
		Direction towardSide = v > 0 ? acrossPlus : acrossMinus;
		Direction awayFromSide = v > 0 ? acrossMinus : acrossPlus;
		// Light: lanterns in two rows down the room.
		if (Math.floorMod(iu, 5) == 2 && Math.abs(av - Math.max(1.5, halfW / 2)) < 0.5) {
			hang(c, feet, ceiling, LANTERN.setValue(LanternBlock.HANGING, true));
		}
		switch (kind) {
			case 0 -> {
				// A feast hall: a long table down the middle with candles, benches either side, barrels and banners.
				put(c, floor, Math.floorMod(iu + iv, 2) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
				if (doorway) {
					return;
				}
				if (av < 0.5 && u >= 3 && depth - u >= 3) {
					put(c, feet, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
					if (iu % 3 == 0) {
						put(c, feet + 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
					}
				} else if (av >= 0.5 && av < 1.5 && u >= 3 && depth - u >= 3) {
					put(c, feet, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, towardSide));
				} else if (side && iu % 4 == 1) {
					put(c, feet, Blocks.BARREL.defaultBlockState());
					put(c, feet + 1, Blocks.BARREL.defaultBlockState());
				} else if (outer && iv % 3 == 0) {
					put(c, feet + 3, Blocks.WALL_BANNER.red().defaultBlockState().setValue(WallBannerBlock.FACING, in));
				}
			}
			case 1 -> {
				// An armoury: anvils, grindstones and smithing tables along the far wall, weapon barrels at the sides.
				put(c, floor, av < 1.0 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				if (doorway) {
					return;
				}
				if (outer) {
					BlockState[] row = {Blocks.ANVIL.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState(),
						Blocks.GRINDSTONE.defaultBlockState(), Blocks.BLAST_FURNACE.defaultBlockState()};
					put(c, feet, row[Math.floorMod(iv, 4)]);
				} else if (side && iu % 2 == 0) {
					put(c, feet, Blocks.BARREL.defaultBlockState());
					put(c, feet + 1, Blocks.IRON_BARS.defaultBlockState());
				} else if (iu % 6 == 3 && Math.abs(av - halfW / 2) < 0.5) {
					// A weapon rack: a post with a chain-hung shield of iron.
					put(c, feet, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
					put(c, feet + 1, Blocks.IRON_BLOCK.defaultBlockState());
				}
			}
			case 2 -> {
				// A library: bookshelves floor to ceiling along the walls, a red runner, reading lecterns, an enchanting table.
				put(c, floor, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				if (av < 1.0 && !doorway) {
					put(c, feet, Blocks.CARPET.red().defaultBlockState());
				}
				if (doorway) {
					return;
				}
				if (side || outer) {
					for (int y = feet; y < Math.min(ceiling, feet + 6); y++) {
						put(c, y, (y + iu) % 4 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
					}
				} else if (iu % 5 == 3 && Math.abs(av - 2.5) < 0.5) {
					put(c, feet, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, awayFromSide));
				} else if (Math.abs(u - depth / 2) < 0.5 && av < 0.5) {
					put(c, feet, Blocks.ENCHANTING_TABLE.defaultBlockState());
				}
			}
			case 3 -> {
				// A shrine: calcite and amethyst, an altar of crying obsidian with candles, crystals growing by the walls.
				put(c, floor, av < 1.0 ? AMETHYST : Blocks.CALCITE.defaultBlockState());
				if (doorway) {
					return;
				}
				if (depth - u < 3.0 && av < 2.5) {
					put(c, feet, av < 0.5 ? CRYING : AMETHYST);
					if (av >= 0.5) {
						put(c, feet + 1, Blocks.DYED_CANDLE.purple().defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
					} else {
						put(c, feet + 1, Blocks.END_ROD.defaultBlockState());
					}
				} else if (side && iu % 3 == 0) {
					put(c, feet, Blocks.AMETHYST_CLUSTER.defaultBlockState());
				} else if (side && iu % 3 == 1) {
					put(c, feet, Blocks.DYED_CANDLE.magenta().defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
				}
			}
			case 4 -> {
				// A training room: targets on the far wall, straw dummies in rows.
				put(c, floor, Math.floorMod(iu + iv, 2) == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
				if (doorway) {
					return;
				}
				if (outer && iv % 3 == 0) {
					put(c, feet, Blocks.HAY_BLOCK.defaultBlockState());
					put(c, feet + 1, Blocks.TARGET.defaultBlockState());
				} else if (iu % 4 == 2 && iv % 4 == 0 && av < halfW - 1.5 && u > 2.5 && depth - u > 2.5) {
					put(c, feet, Blocks.HAY_BLOCK.defaultBlockState());
					put(c, feet + 1, Blocks.HAY_BLOCK.defaultBlockState());
					// (A skull, not a pumpkin: a pumpkin on placement looks for an iron golem to make.)
					put(c, feet + 2, Blocks.SKELETON_SKULL.defaultBlockState());
				}
			}
			case 5 -> {
				// A treasury: gilded floor, heaps of gold, decorated pots.
				put(c, floor, Math.floorMod(iu + iv, 2) == 0 ? GILDED : BLACKSTONE);
				if (doorway) {
					return;
				}
				int heap = PracticeLayout.scatter(sector * 31 + iu, iv * 17 + k, 5) % 10;
				if (outer || side && heap < 5) {
					put(c, feet, heap < 3 ? Blocks.GOLD_BLOCK.defaultBlockState() : heap < 6 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState()
						: Blocks.BARREL.defaultBlockState());
					if (heap < 2 && outer) {
						put(c, feet + 1, Blocks.GOLD_BLOCK.defaultBlockState());
					}
				} else if (iu % 3 == 1 && Math.abs(av - halfW / 2) < 0.5) {
					put(c, feet, Blocks.DECORATED_POT.defaultBlockState());
				}
			}
			case 6 -> {
				// An alchemist's room: brewing stands on tables, cauldrons of water, pots.
				put(c, floor, Math.floorMod(iu, 3) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
				if (doorway) {
					return;
				}
				if (side) {
					put(c, feet, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
					if (iu % 3 == 0) {
						put(c, feet + 1, Blocks.BREWING_STAND.defaultBlockState());
					} else if (iu % 3 == 1) {
						put(c, feet + 1, Blocks.POTTED_CRIMSON_FUNGUS.defaultBlockState());
					}
				} else if (iu % 4 == 2 && av < 0.5) {
					put(c, feet, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				} else if (outer && iv % 2 == 0) {
					put(c, feet, Blocks.DECORATED_POT.defaultBlockState());
				}
			}
			case 7 -> {
				// A lounge: carpets, sofas along the walls, potted azaleas, a jukebox.
				put(c, floor, Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (doorway) {
					return;
				}
				if (side && iu % 5 != 0) {
					put(c, feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, towardSide));
				} else if (side) {
					put(c, feet, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
				} else if (Math.abs(u - depth / 2) < 0.5 && av < 0.5) {
					put(c, feet, Blocks.JUKEBOX.defaultBlockState());
				} else {
					put(c, feet, Math.floorMod(iu / 2 + iv / 2, 2) == 0 ? Blocks.CARPET.purple().defaultBlockState()
						: Blocks.CARPET.magenta().defaultBlockState());
				}
			}
			default -> {
				// A storeroom: barrels and hay along the walls, crates in the middle, furnaces and smokers on the far wall.
				put(c, floor, Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (doorway) {
					return;
				}
				if (outer) {
					put(c, feet, iv % 2 == 0 ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
				} else if (side) {
					put(c, feet, iu % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
					if (iu % 2 == 0) {
						put(c, feet + 1, Blocks.BARREL.defaultBlockState());
					}
				} else if (iu % 6 == 3 && Math.floorMod(iv, 5) <= 1 && av < halfW - 2) {
					// Crates stacked in the middle, two high, a sack of hay on some.
					put(c, feet, Blocks.BARREL.defaultBlockState());
					put(c, feet + 1, Math.floorMod(iv, 5) == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
		}
	}

	/** Blocks that need their neighbours (placed after everything solid). */
	static boolean attached(Block b) {
		return b instanceof CandleBlock || b == Blocks.POTTED_CRIMSON_FUNGUS || b == Blocks.POTTED_FLOWERING_AZALEA
			|| b instanceof net.minecraft.world.level.block.CarpetBlock;
	}
}

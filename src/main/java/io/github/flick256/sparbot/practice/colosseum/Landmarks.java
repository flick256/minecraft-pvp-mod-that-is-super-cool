package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACKSTONE;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.BLACK_BRICKS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CHAIN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.CRYING;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.GILDED;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPUR;

import io.github.flick256.sparbot.practice.colosseum.ColosseumInterior.Room;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The six halls of the colosseum's story (see {@link ColosseumLore}), each one of a kind, on the ground floor of
 * its sector: the Archive of the Founders, the Warden's Hall, the Chapel of the Fallen Star, the Cells (where
 * the lower door is), the Treasury of Tithes and the Hall of Champions.
 */
final class Landmarks {
	static final int ARCHIVE = 0;
	static final int WARDEN = 1;
	static final int CHAPEL = 2;
	static final int CELLS = 3;
	static final int TREASURY = 4;
	static final int CHAMPIONS = 5;
	/** Each landmark's gallery (0-2) and sector (0-47, sunwise from the east gate). */
	private static final int[][] WHERE = {{1, 2}, {0, 37}, {1, 25}, {0, 33}, {2, 10}, {2, 46}};
	static final String[][] NAMES = {{"ARCHIVE OF", "THE FOUNDERS"}, {"THE WARDEN'S", "HALL"}, {"CHAPEL OF THE", "FALLEN STAR"}, {"THE CELLS", ""},
		{"TREASURY OF", "THE CROWN"}, {"HALL OF", "CHAMPIONS"}};

	private Landmarks() {
	}

	/** The landmark in that gallery, sector and storey, or -1. */
	static int at(int band, int sector, int k) {
		if (k != 0) {
			return -1;
		}
		for (int i = 0; i < WHERE.length; i++) {
			if (WHERE[i][0] == band && WHERE[i][1] == sector) {
				return i;
			}
		}
		return -1;
	}

	/** The middle of a landmark's sector, in degrees (where the Cells' stair runs down from). */
	static double centre(int landmark) {
		return (WHERE[landmark][1] + 0.5) * ColosseumInterior.SECTOR;
	}

	static void room(Room r, int landmark) {
		// Light first, in two rows (furnishings may stand in for a lantern here and there).
		if (Math.floorMod(r.iu, 4) == 2 && Math.abs(r.av - Math.max(1.5, r.halfW / 2)) < 0.5) {
			ColosseumInterior.hang(r.c, r.feet, r.ceiling, (landmark == CELLS || landmark == CHAPEL ? Blocks.SOUL_LANTERN : Blocks.LANTERN)
				.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		switch (landmark) {
			case ARCHIVE -> archive(r);
			case WARDEN -> warden(r);
			case CHAPEL -> chapel(r);
			case CELLS -> cells(r);
			case TREASURY -> treasury(r);
			default -> champions(r);
		}
	}

	private static BlockState candles(int n) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
	}

	/**
	 * The Archive of the Founders: books from floor to ceiling, a long reading table with candles and lecterns,
	 * a globe by the door, and at the far end the Chronicle of the Falling Star.
	 */
	private static void archive(Room r) {
		r.put(r.floor(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
		if (r.av < 1.0 && !r.doorway && !(r.u >= 6 && r.u < r.depth - 7)) {
			r.put(r.feet, Blocks.CARPET.red().defaultBlockState());
		}
		if (r.doorway) {
			return;
		}
		if (r.side || r.outer) {
			for (int y = r.feet; y < r.ceiling; y++) {
				r.put(y, (y + r.iu + r.iv) % 5 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
			}
		} else if (r.av < 0.5 && r.u >= 6 && r.u < r.depth - 7) {
			r.put(r.feet, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			if (r.iu % 3 == 0) {
				r.put(r.feet + 1, candles(3));
			}
		} else if (r.av >= 1.0 && r.av < 2.0 && r.u >= 6 && r.u < r.depth - 7 && r.iu % 4 == 2) {
			r.put(r.feet, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, r.towardSide));
		} else if (r.av < 0.5 && r.iu == 3) {
			// A globe of the known world on a stand.
			r.put(r.feet, Blocks.DARK_OAK_FENCE.defaultBlockState());
			r.put(r.feet + 1, Blocks.GLAZED_TERRACOTTA.lightBlue().defaultBlockState());
		} else if (r.av < 0.5 && r.iu == (int) Math.floor(r.depth - 3.5)) {
			r.lectern(r.in, ColosseumLore.CHRONICLE);
		} else if (r.iv == 1 && r.depth - r.u < 2.0 && r.depth - r.u >= 1.0) {
			r.sign(r.feet + 2, r.in, DyeColor.YELLOW, true, "THE CHRONICLE", "OF THE CROWN", "by Archivist", "Tell");
		} else if (r.iv == 3 && r.u >= 2.0 && r.u < 3.0) {
			r.put(r.feet, Blocks.BOOKSHELF.defaultBlockState());
			r.put(r.feet + 1, Blocks.POTTED_FERN.defaultBlockState());
		}
	}

	/** The Warden's Hall: a narrow watch hall, weapon racks, black banners, and the Warden's desk with his log. */
	private static void warden(Room r) {
		r.put(r.floor(), r.av < 1.0 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : BLACK_BRICKS);
		if (r.doorway) {
			return;
		}
		double back = r.depth - r.u;
		if (r.side && r.iu % 3 == 0) {
			r.put(r.feet, Blocks.BARREL.defaultBlockState());
			r.put(r.feet + 1, Blocks.IRON_BARS.defaultBlockState());
		} else if (r.side && r.iu % 3 == 1) {
			r.put(r.feet + 3, Blocks.WALL_BANNER.black().defaultBlockState().setValue(WallBannerBlock.FACING, r.awayFromSide));
		} else if (back >= 2.0 && back < 3.0 && r.av < 0.5) {
			r.lectern(r.in, ColosseumLore.WARDEN);
		} else if (back >= 2.0 && back < 3.0 && r.av < 2.0) {
			r.put(r.feet, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			r.put(r.feet + 1, r.iv > 0 ? candles(2) : Blocks.LANTERN.defaultBlockState());
		} else if (back < 2.0 && r.av < 0.5) {
			r.put(r.feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.out));
		} else if (r.outer && r.iv == 1) {
			r.sign(r.feet + 2, r.in, DyeColor.YELLOW, true, "THE WARDEN'S", "HALL", "the watch is", "never over");
		} else if (r.outer && r.iv == -1) {
			r.sign(r.feet + 2, r.in, DyeColor.RED, false, "WENT DOWN: 214", "CAME BACK", "BEATEN: 61", "WON: 0");
		}
	}

	/**
	 * The Chapel of the Fallen Star: pews facing an amethyst altar under a great window of glass, candles, and
	 * the Litany of the Lock on a lectern by the altar.
	 */
	private static void chapel(Room r) {
		r.put(r.floor(), r.av < 1.0 ? PURPUR : Blocks.CALCITE.defaultBlockState());
		double back = r.depth - r.u;
		if (r.outer && !r.doorway) {
			// The great window behind the altar.
			r.fill(r.feet + 1, r.ceiling - 2, Math.floorMod(r.iv, 2) == 0 ? Blocks.STAINED_GLASS.magenta().defaultBlockState()
				: Blocks.STAINED_GLASS.purple().defaultBlockState());
			r.put(r.ceiling - 1, AMETHYST);
		}
		if (r.doorway) {
			return;
		}
		if (back < 3.5 && back >= 1.0 && r.av < 2.0) {
			r.put(r.feet, r.av < 0.5 ? CRYING : AMETHYST);
			r.put(r.feet + 1, r.av < 0.5 ? Blocks.END_ROD.defaultBlockState()
				: Blocks.DYED_CANDLE.purple().defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
		} else if (back >= 3.5 && back < 4.5 && r.iv == -2) {
			r.lectern(r.in, ColosseumLore.LITANY);
		} else if (r.side) {
			r.fill(r.feet + 1, Math.min(r.ceiling - 1, r.feet + 4), r.iu % 2 == 0 ? Blocks.STAINED_GLASS.magenta().defaultBlockState()
				: Blocks.STAINED_GLASS.purple().defaultBlockState());
			r.put(r.feet, r.iu % 2 == 0 ? Blocks.AMETHYST_CLUSTER.defaultBlockState()
				: Blocks.DYED_CANDLE.magenta().defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
		} else if (r.iu % 3 == 0 && r.av >= 1.5 && r.u >= 3 && back >= 5.5) {
			r.put(r.feet, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.in));
		} else if (r.inner && r.iv == 3) {
			r.sign(r.feet + 2, r.out, DyeColor.WHITE, true, "GO DOWN", "BRAVELY.", "COME BACK UP.", "- Sister Imre");
		}
	}

	private static final String[][] SCRATCHED = {{"I LOST TO HIM", "AND I'D GO", "AGAIN", ""}, {"1000 - 0", "1000 - 0", "1000 - 0", ""},
		{"HE FIGHTS", "FAIR. DON'T", "EXPECT MERCY", ""}, {"THE MUSIC", "MEANS HE", "HEARD YOU", ""}, {"TRAIN FIRST.", "THEN GO", "DOWN", ""},
		{"BRING", "TOTEMS", "", "- Kael"}};

	/**
	 * The Cells: barred cells down both sides with chains and cots, scratchings on the walls, and in the middle
	 * the lower door, a stair down under the field (see {@link Heartwell}).
	 */
	private static void cells(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 3) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
		if (r.doorway || r.u < 14) {
			// (the near half is the stairwell's: see Heartwell)
			return;
		}
		double fromWall = r.halfW - r.av;
		if (fromWall >= 1.5 && fromWall < 2.5) {
			if (r.iu % 5 != 2) {
				r.fill(r.feet, r.feet + 2, Blocks.IRON_BARS.defaultBlockState());
			}
		} else if (fromWall < 1.5) {
			if (r.iu % 5 == 0 && r.flatCeiling()) {
				r.fill(r.ceiling - 3, r.ceiling - 1, CHAIN);
			} else if (r.iu % 5 == 3) {
				r.put(r.feet, Blocks.WOOL.lightGray().defaultBlockState());
			} else if (r.iu % 5 == 4 && r.side) {
				r.sign(r.feet + 2, r.awayFromSide, DyeColor.RED, false, SCRATCHED[Math.floorMod(r.iu / 5 + (r.v > 0 ? 3 : 0), SCRATCHED.length)]);
			}
			if (r.flatCeiling() && r.iu % 5 == 1 && r.side) {
				r.put(r.ceiling - 1, Blocks.COBWEB.defaultBlockState());
			}
		} else if (r.iv == 3 && r.u >= 13 && r.u < 14) {
			r.put(r.feet, Blocks.POLISHED_ANDESITE.defaultBlockState());
		}
		if (r.outer && r.iv == 2) {
			r.sign(r.feet + 2, r.in, DyeColor.RED, false, "THE LOWER", "DOOR IS", "NEVER", "LOCKED");
		}
	}

	/** The Treasury of Tithes: heaps of gold, counting tables, scales, a barred vault, and the Tithe Ledger by the door. */
	private static void treasury(Room r) {
		r.put(r.floor(), Math.floorMod(r.iu + r.iv, 2) == 0 ? GILDED : BLACKSTONE);
		if (r.doorway) {
			return;
		}
		double back = r.depth - r.u;
		int heap = r.roll(11) % 10;
		if (back < 4.0 && back >= 3.0 && r.av >= 1.0) {
			r.fill(r.feet, r.feet + 2, Blocks.IRON_BARS.defaultBlockState());
		} else if (back < 3.0) {
			r.put(r.feet, heap < 4 ? Blocks.GOLD_BLOCK.defaultBlockState() : heap < 7 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState()
				: Blocks.BARREL.defaultBlockState());
			if (heap < 3) {
				r.put(r.feet + 1, Blocks.GOLD_BLOCK.defaultBlockState());
			}
		} else if (r.av < 0.5 && r.u >= 3 && r.u < 4) {
			r.lectern(r.in, ColosseumLore.LEDGER);
		} else if (r.iv == 2 && r.inner) {
			r.sign(r.feet + 2, r.out, DyeColor.YELLOW, true, "THE CHAMPION'S", "PURSE", "unclaimed", "since Year 13");
		} else if (r.iu % 4 == 2 && r.av >= 2.0 && r.av < 4.0 && r.u > 5) {
			r.put(r.feet, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			r.put(r.feet + 1, r.iv % 2 == 0 ? candles(2) : Blocks.GOLD_BLOCK.defaultBlockState());
		} else if (r.av < 0.5 && r.iu % 6 == 0 && r.u > 5) {
			r.put(r.feet, Blocks.ANVIL.defaultBlockState());
		} else if (r.side && r.iu % 2 == 0) {
			r.put(r.feet, Blocks.DECORATED_POT.defaultBlockState());
		}
	}

	private static final String[][] CHAMPION_NAMES = {{"AUREL THE", "SWIFT", "412 - 38", ""}, {"BRENNA", "IRONHAND", "377 - 51", ""},
		{"DORN OF", "EMBERS", "290 - 12", ""}, {"ILSA", "MOONWARD", "515 - 60", ""}, {"KAEL", "TWO-BLADES", "333 - 41", ""},
		{"MIRA THE", "BRIGHT", "268 - 9", ""}, {"OLD MARR", "", "701 - 140", "fought 40 years"}, {"SABLE", "NIGHT", "199 - 3", ""},
		{"TOBIN", "FARSHOT", "251 - 30", "master of bows"}, {"YSOLDE", "OF THE ROSE", "450 - 22", ""}};

	/**
	 * The Hall of Champions: the greats of the arena on pedestals down both sides, each with their record, and
	 * at the far end an empty pedestal whose plaque has been struck through.
	 */
	private static void champions(Room r) {
		r.put(r.floor(), r.av < 1.0 ? GILDED : BLACK_BRICKS);
		if (r.doorway) {
			return;
		}
		double fromWall = r.halfW - r.av;
		double back = r.depth - r.u;
		boolean row = r.iu % 4 == 2 && r.u >= 3 && back >= 4;
		if (row && fromWall >= 1.0 && fromWall < 2.0) {
			r.put(r.feet, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			r.put(r.feet + 1, Blocks.GOLD_BLOCK.defaultBlockState());
			BlockState[] heads = {Blocks.SKELETON_SKULL.defaultBlockState(), Blocks.ZOMBIE_HEAD.defaultBlockState(), Blocks.CREEPER_HEAD.defaultBlockState(),
				Blocks.PIGLIN_HEAD.defaultBlockState()};
			r.put(r.feet + 2, heads[Math.floorMod(r.iu / 4 + (r.v > 0 ? 1 : 0), heads.length)]);
		} else if (row && fromWall < 1.0) {
			r.sign(r.feet + 2, r.awayFromSide, DyeColor.YELLOW, true,
				CHAMPION_NAMES[Math.floorMod((r.iu / 4) * 2 + (r.v > 0 ? 1 : 0), CHAMPION_NAMES.length)]);
		} else if (back >= 2.0 && back < 3.0 && r.av < 0.5) {
			// Vaelor's pedestal: cracked, and empty.
			r.put(r.feet, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		} else if (r.outer && r.iv == 0) {
			r.sign(r.feet + 2, r.in, DyeColor.PURPLE, true, "VAELOR THE", "UNBROKEN", "1000 - 0", "(still below)");
		} else if (r.outer && r.iv == -2) {
			// The newest plaque: whoever beats Vaelor (see HollowCrown).
			ColosseumLore.champions(r.c, r.dx, r.feet + 2, r.dz, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallSignBlock.FACING, r.in));
		} else if (r.outer && r.iv % 2 == 1) {
			r.put(r.feet + 3, (r.iv > 0 ? Blocks.WALL_BANNER.yellow() : Blocks.WALL_BANNER.black()).defaultBlockState()
				.setValue(WallBannerBlock.FACING, r.in));
		} else if (r.inner && r.iv == 3) {
			r.sign(r.feet + 2, r.out, DyeColor.YELLOW, true, "HALL OF", "CHAMPIONS", "of the", "Glass Crown");
		}
	}
}

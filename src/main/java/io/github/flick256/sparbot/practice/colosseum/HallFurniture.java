package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.GrandHalls.b;

import io.github.flick256.sparbot.practice.colosseum.GrandHalls.Spot;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What makes each of the {@link GrandHalls} itself: its centrepiece, its furniture, its story. Every method dresses one
 * column of its hall, after the common shell is built; they keep to free floor (not the doors, the stairs or the
 * colonnades) and below the balconies.
 */
final class HallFurniture {
	private HallFurniture() {
	}

	// --- Helpers ---

	private static BlockState face(Block block, Direction d) {
		return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
	}

	private static BlockState stair(Block block, Direction back) {
		return block.defaultBlockState().setValue(StairBlock.FACING, back);
	}

	private static BlockState topSlab(Block block) {
		return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
	}

	private static BlockState candles(Block candle, int n) {
		return candle.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, true);
	}

	private static BlockState lit(Block campfire) {
		return campfire.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
	}

	/** The world direction of a local one (across, along: out from the middle of the stands, toward the pier). */
	private static Direction dir(Spot s, double du, double dw) {
		return Math.abs(du) >= Math.abs(dw) ? du >= 0 ? s.out : s.in : dw >= 0 ? s.toPier : s.toGate;
	}

	/** Bars along {@code a} (connected both ways). */
	private static BlockState bars(Direction a) {
		BlockState s = Blocks.IRON_BARS.defaultBlockState();
		return s.setValue(IronBarsBlock.PROPERTY_BY_DIRECTION.get(a), true).setValue(IronBarsBlock.PROPERTY_BY_DIRECTION.get(a.getOpposite()), true);
	}

	private static BlockState skull(Block block, Spot s, double du, double dw) {
		Direction d = dir(s, du, dw);
		return block.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(d));
	}

	/** Within a rectangle of the hall (across and along from its middle). */
	private static boolean in(Spot s, double cu0, double cu1, double cw0, double cw1) {
		return s.cu >= cu0 && s.cu < cu1 && s.cw >= cw0 && s.cw < cw1;
	}

	/** A cot: a raised frame with a blanket, and a white pillow at its head. */
	private static void cot(Spot s, Block blanket, boolean head) {
		s.put(F, topSlab(Blocks.SPRUCE_SLAB));
		s.put(F + 1, head ? Blocks.CARPET.white().defaultBlockState() : blanket.defaultBlockState());
	}

	/** Bookshelves from the floor up, with the odd chiseled one. */
	private static void shelves(Spot s, int height, int salt) {
		for (int y = F; y < F + height; y++) {
			s.put(y, s.roll(salt + y) % 5 == 0 ? b(Blocks.CHISELED_BOOKSHELF) : b(Blocks.BOOKSHELF));
		}
	}

	// --- The First Gallery ---

	/** Seven racks of arms in a row across the hall, a forge-dais of anvils in the middle, grindstones by the walls. */
	static void armoury(Spot s) {
		if (s.r() < 2.6) {
			s.put(F, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
			if (Math.abs(s.cu) < 0.5 && Math.abs(s.cw) < 1.6) {
				s.put(F + 1, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, s.toPier));
			} else if (s.r() > 1.8 && s.roll(3) % 3 == 0) {
				s.put(F + 1, Blocks.SMITHING_TABLE.defaultBlockState());
			}
			return;
		}
		if (!s.free()) {
			return;
		}
		int m = Math.floorMod(s.iw - (int) (s.len / 2), 6);
		boolean rack = (m == 0) && Math.abs(s.cu) > 2.5 && Math.abs(s.cu) < s.depth / 2 - 3 && Math.abs(s.cw) < 21 && Math.abs(s.cw) > 3;
		if (rack) {
			int which = (int) Math.floor((s.cw + 21) / 6);
			s.put(F, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
			s.put(F + 1, b(Blocks.DARK_OAK_FENCE));
			if (which == 6) {
				return;
			}
			s.put(F + 2, s.roll(5) % 2 == 0 ? GrandHalls.ROD.defaultBlockState() : b(Blocks.DARK_OAK_FENCE));
			return;
		}
		if (Math.abs(s.cu) > s.depth / 2 - 2.5 && s.roll(9) % 9 == 0) {
			s.put(F, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
				.setValue(HorizontalDirectionalBlock.FACING, s.toPier));
		}
		if (Math.abs(s.cw) < 0.5 && Math.abs(s.cu - 4) < 0.5) {
			s.sign(F + 1, s.toGate, DyeColor.WHITE, "SEVEN RACKS", "FOR THE SEVEN.", "THE SEVENTH", "STANDS EMPTY.");
		}
	}

	/** Stacks of shelves across the hall with reading tables between, the Chronicle on its lectern in the middle. */
	static void archive(Spot s) {
		if (s.r() < 0.75) {
			s.lectern(s.toGate, ColosseumLore.CHRONICLE);
			return;
		}
		if (s.r() < 3.4) {
			if (s.r() > 2.5) {
				s.put(F, topSlab(Blocks.DARK_OAK_SLAB));
				if (s.roll(4) % 4 == 0) {
					s.put(F + 1, candles(Blocks.CANDLE, 3));
				}
			}
			return;
		}
		if (s.depth - s.u < 1 && s.top > F + GrandHalls.B1 + 3) {
			// Shelves along the balcony's wall too.
			for (int y = F + GrandHalls.B1; y < F + GrandHalls.B1 + 3; y++) {
				s.put(y, b(Blocks.BOOKSHELF));
			}
		}
		if (!s.free()) {
			return;
		}
		int m = Math.floorMod(s.iw, 6);
		boolean stack = (m == 1 || m == 2) && Math.abs(s.cu) > 2 && Math.abs(s.cu) < s.depth / 2 - 2.5;
		if (stack) {
			shelves(s, 5, 11);
			if (m == 1 && Math.floorMod(s.iu, 4) == 0) {
				s.put(F + 5, Blocks.LANTERN.defaultBlockState());
			}
			return;
		}
		if (m == 4 && Math.abs(s.cu) > 2.5 && Math.abs(s.cu) < 5) {
			s.put(F, topSlab(Blocks.SPRUCE_SLAB));
			if (s.roll(6) % 3 == 0) {
				s.put(F + 1, candles(Blocks.CANDLE, 1 + s.roll(7) % 3));
			}
		}
	}

	/** Trophies on pedestals in two rows, the empty pedestal in the middle, the champions' plaque on the outer wall. */
	static void champions(Spot s) {
		if (s.r() < 2.5) {
			s.put(F, b(Blocks.SMOOTH_QUARTZ));
			if (s.r() < 0.75) {
				s.put(F + 1, b(Blocks.CHISELED_QUARTZ_BLOCK));
			} else if (Math.abs(s.cw - 1) < 0.5 && Math.abs(s.cu) < 0.5) {
				s.sign(F + 1, s.toPier, DyeColor.YELLOW, "RESERVED", "for whoever", "beats Vaelor", "");
			}
			return;
		}
		if (s.wallOut() && Math.abs(s.cw) < 0.5) {
			ColosseumLore.champions(s.c, s.dx, F + 2, s.dz, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallSignBlock.FACING, s.in));
			return;
		}
		if (!s.free()) {
			return;
		}
		boolean row = Math.abs(Math.abs(s.cu) - 4) < 0.5;
		if (row && Math.floorMod(s.iw, 5) == 2 && Math.abs(s.cw) > 3.5) {
			s.put(F, b(Blocks.CHISELED_QUARTZ_BLOCK));
			s.put(F + 1, b(Blocks.QUARTZ_PILLAR));
			s.put(F + 2, s.roll(12) % 3 == 0 ? b(Blocks.AMETHYST_BLOCK) : b(Blocks.GOLD_BLOCK));
			s.put(F + 3, Blocks.END_ROD.defaultBlockState());
		}
	}

	/** Pews facing the altar at the pier end, the star window over it, candles down the aisle, the Blessing on a lectern. */
	static void chapel(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 1 && Math.abs(s.cu) < 3.5) {
			// The star window, set against the pier.
			for (int y = F + 3; y <= Math.min(s.top - 1, F + 12); y++) {
				double r = Math.hypot(s.cu, y - (F + 7.5));
				s.put(y, r < 1.2 ? b(Blocks.SEA_LANTERN) : (Math.floorMod(y + s.iu, 2) == 0 ? b(Blocks.STAINED_GLASS.purple())
					: b(Blocks.STAINED_GLASS.magenta())));
			}
			return;
		}
		if (fromEnd < 7 && fromEnd >= 3 && Math.abs(s.cu) < 3.5) {
			s.put(F, b(Blocks.CALCITE));
			if (fromEnd < 5 && fromEnd >= 4 && Math.abs(s.cu) < 1.5) {
				s.put(F + 1, Math.abs(s.cu) < 0.5 ? b(Blocks.CRYING_OBSIDIAN) : b(Blocks.AMETHYST_BLOCK));
				s.put(F + 2, candles(Blocks.DYED_CANDLE.purple(), 3));
			}
			if (fromEnd >= 6 && Math.abs(s.cu) < 0.5) {
				s.lectern(s.toGate, ColosseumLore.LITANY);
			}
			return;
		}
		if (!s.free()) {
			return;
		}
		if (Math.abs(s.cu) < 1.6 && Math.floorMod(s.iw, 4) == 0 && Math.abs(s.cu) > 1.0) {
			s.put(F, candles(Blocks.CANDLE, 2));
			return;
		}
		boolean pew = Math.floorMod(s.iw, 3) == 0 && Math.abs(s.cu) >= 1.6 && Math.abs(s.cu) < s.depth / 2 - 3 && s.w > 6 && fromEnd > 8;
		if (pew) {
			s.put(F, stair(Blocks.BIRCH_STAIRS, s.toGate));
		}
	}

	/** Corvin's great round desk with his log in the middle, map tables and record barrels along the walls, a watch bell. */
	static void warden(Spot s) {
		if (s.r() < 0.75) {
			s.lectern(s.toGate, ColosseumLore.WARDEN);
			return;
		}
		if (s.r() > 1.5 && s.r() < 3.0) {
			s.put(F, topSlab(Blocks.DARK_OAK_SLAB));
			if (s.roll(2) % 5 == 0) {
				s.put(F + 1, candles(Blocks.CANDLE, 2));
			}
			return;
		}
		if (!s.free()) {
			return;
		}
		if (s.u < 2.5 && Math.floorMod(s.iw, 4) == 1) {
			s.put(F, s.roll(4) % 2 == 0 ? b(Blocks.CARTOGRAPHY_TABLE) : Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			return;
		}
		if (Math.abs(s.cw - s.len / 2 + 4) < 0.5 && Math.abs(s.cu) < 0.5) {
			s.put(F, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, s.toPier));
		}
		if (Math.abs(s.cw + 6) < 0.5 && Math.abs(s.cu + 2) < 0.5) {
			s.sign(F + 1, s.toPier, DyeColor.WHITE, "WENT DOWN:", "213", "CAME BACK", "BEATEN: 61");
		}
	}

	/** Cells along both walls, barred, with what the prisoners left; the stair down (see {@link Heartwell}) is kept clear. */
	static void cells(Spot s) {
		double a = s.dx * Heartwell.COS + s.dz * Heartwell.SIN;
		double lat = -s.dx * Heartwell.SIN + s.dz * Heartwell.COS;
		if (Math.abs(lat) < 5 && a > 50) {
			return;
		}
		if (s.doorway() || s.reserved()) {
			return;
		}
		boolean innerCell = s.u < 4.5 && s.u >= 0;
		boolean outerCell = s.depth - s.u < 4.5;
		if (!innerCell && !outerCell) {
			if (s.free() && s.roll(31) % 40 == 0) {
				s.put(F, Blocks.IRON_CHAIN.defaultBlockState());
			}
			return;
		}
		double fromFront = innerCell ? 4.5 - s.u : 4.5 - (s.depth - s.u);
		int m = Math.floorMod(s.iw, 5);
		if (m == 0) {
			s.fill(F, F + 3, b(Blocks.DEEPSLATE_BRICKS));
			return;
		}
		if (fromFront < 1) {
			boolean gate = m == 2;
			for (int y = F; y <= F + 3; y++) {
				s.put(y, gate && y < F + 2 ? AIR : bars(s.toPier));
			}
			s.put(F + 4, b(Blocks.DEEPSLATE_TILES));
			return;
		}
		s.put(F + 4, b(Blocks.DEEPSLATE_TILES));
		int r = s.roll(17) % 12;
		if (fromFront > 2.5 && r < 2) {
			s.put(F, b(Blocks.HAY_BLOCK));
		} else if (fromFront > 2.5 && r == 2) {
			s.put(F, skull(Blocks.SKELETON_SKULL, s, -s.cu, 0));
		} else if (r == 3) {
			s.put(F + 3, Blocks.COBWEB.defaultBlockState());
		} else if (r == 4 && fromFront > 2.5) {
			s.put(F, Blocks.IRON_CHAIN.defaultBlockState());
		}
	}

	/** The Oath Stone in the middle, seven banners of the Seven round it, cushions to kneel on. */
	static void oaths(Spot s) {
		if (s.r() < 1.6) {
			s.fill(F, F + 5, b(Blocks.OBSIDIAN));
			s.put(F + 3, b(Blocks.CRYING_OBSIDIAN));
			s.put(F + 6, b(Blocks.GOLD_BLOCK));
			return;
		}
		if (s.r() < 2.6) {
			s.put(F, b(Blocks.POLISHED_BLACKSTONE));
			return;
		}
		if (Math.abs(s.r() - 4) < 0.5) {
			s.put(F, Blocks.CARPET.red().defaultBlockState());
			return;
		}
		double ang = Math.atan2(s.cw, s.cu);
		for (int i = 0; i < 7; i++) {
			double a = i * Math.PI * 2 / 7;
			if (Math.hypot(s.cu - Math.cos(a) * 6.5, s.cw - Math.sin(a) * 6.5) < 0.6) {
				s.put(F, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
				s.put(F + 1, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
				Block[] banners = {Blocks.BANNER.red(), Blocks.BANNER.blue(), Blocks.BANNER.green(), Blocks.BANNER.yellow(), Blocks.BANNER.white(),
					Blocks.BANNER.orange(), Blocks.BANNER.purple()};
				s.put(F + 2, banners[i].defaultBlockState().setValue(net.minecraft.world.level.block.BannerBlock.ROTATION,
					RotationSegment.convertToSegment(dir(s, -Math.cos(a), -Math.sin(a)))));
				return;
			}
		}
		if (s.free() && Math.abs(s.cw) < 0.5 && Math.abs(s.cu + 8) < 0.5 && ang != 0) {
			s.sign(F + 1, s.out, DyeColor.WHITE, "I WILL FIGHT", "FAIR. I WILL", "NOT GIVE UP.", "I WILL RISE.");
		}
	}

	/** The great round vault door on the pier wall, the Champion's Purse on its dais, gold in heaps, strongboxes. */
	static void treasury(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 1) {
			for (int y = F; y <= Math.min(s.top - 1, F + 10); y++) {
				double r = Math.hypot(s.cu, y - (F + 5));
				if (r < 4.5) {
					s.put(y, r < 0.8 ? b(Blocks.GOLD_BLOCK) : r > 3.6 ? b(Blocks.GOLD_BLOCK) : Math.floorMod(y + s.iu, 3) == 0 ? b(GrandHalls.WAXED_CUT_COPPER)
						: b(Blocks.IRON_BLOCK));
				}
			}
			return;
		}
		if (s.r() < 0.75) {
			s.lectern(s.toGate, ColosseumLore.LEDGER);
			return;
		}
		if (s.r() < 2.5) {
			s.put(F, b(Blocks.GOLD_BLOCK));
			return;
		}
		if (!s.free()) {
			return;
		}
		int r = s.roll(23) % 30;
		if (fromEnd < 10 && r < 6) {
			s.put(F, r < 2 ? b(Blocks.GOLD_BLOCK) : r < 4 ? b(Blocks.RAW_GOLD_BLOCK) : topSlab(Blocks.CUT_SANDSTONE_SLAB));
			if (r == 0) {
				s.put(F + 1, b(Blocks.GOLD_BLOCK));
			}
		} else if (s.byWall(2.5) && Math.floorMod(s.iw, 3) == 0) {
			s.put(F, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		}
	}

	// --- The Second Gallery ---

	/** The great forge in the middle (lava in a cauldron, a brick chimney to the vault), anvils round it, furnaces along the walls. */
	static void forge(Spot s) {
		double r = s.r();
		if (r < 1.0) {
			s.put(F, Blocks.LAVA_CAULDRON.defaultBlockState());
			return;
		}
		if (r < 2.6) {
			s.fill(F, F + 1, b(Blocks.BRICKS));
			s.put(F + 2, b(Blocks.MAGMA_BLOCK));
			s.fill(F + 4, s.top, b(Blocks.BRICKS));
			s.put(F + 3, b(Blocks.BLAST_FURNACE).setValue(FurnaceBlock.FACING, dir(s, s.cu, s.cw)).setValue(FurnaceBlock.LIT, true));
			return;
		}
		if (r < 4.6 && r > 3.5 && s.roll(4) % 3 == 0) {
			s.put(F, s.roll(5) % 2 == 0 ? Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, s.toPier) : b(Blocks.SMITHING_TABLE));
			return;
		}
		if (!s.free()) {
			return;
		}
		if (s.byWall(2.0) && Math.floorMod(s.iw, 3) != 0) {
			Direction away = s.u < 2 ? s.out : s.in;
			s.put(F, b(Blocks.FURNACE).setValue(FurnaceBlock.FACING, away).setValue(FurnaceBlock.LIT, s.roll(6) % 3 == 0));
			if (s.roll(8) % 4 == 0) {
				s.put(F + 1, b(Blocks.COAL_BLOCK));
			}
		}
	}

	/** Two long tables down the hall with benches, candles and cakes on them, barrels of ale, a great hearth at the pier end. */
	static void feast(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 1 && Math.abs(s.cu) < 2.5) {
			s.put(F, lit(Blocks.CAMPFIRE));
			s.fill(F + 3, Math.min(s.top, F + 12), b(Blocks.BRICKS));
			return;
		}
		if (!s.free() || s.w < 5 || fromEnd < 5) {
			return;
		}
		double side = Math.abs(s.cu);
		Direction toTable = s.cu > 0 ? (side > 3 ? s.in : s.out) : (side > 3 ? s.out : s.in);
		if (side >= 2.5 && side < 3.5) {
			s.put(F, topSlab(Blocks.DARK_OAK_SLAB));
			int m = Math.floorMod(s.iw, 7);
			if (m == 3) {
				s.put(F + 1, Blocks.CAKE.defaultBlockState());
			} else if (m % 2 == 0) {
				s.put(F + 1, candles(Blocks.CANDLE, 1 + m / 2));
			}
		} else if (side >= 1.6 && side < 2.5 || side >= 3.5 && side < 4.5) {
			s.put(F, stair(Blocks.SPRUCE_STAIRS, toTable.getOpposite()));
		} else if (s.byWall(2.0) && Math.floorMod(s.iw, 4) == 1) {
			s.fill(F, F + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, s.wallIn() || s.u < 2 ? s.out : s.in));
		}
	}

	/** Raised beds of moss and flowers, cherry trees, a stream down the middle, blossoms hanging under the balconies. */
	static void gardens(Spot s) {
		if (Math.abs(s.cu) < 0.6 && s.w > 6 && s.len - s.w > 6 && !s.doorway()) {
			s.put(F - 1, b(Blocks.WATER));
			return;
		}
		if (s.top > F + GrandHalls.B1 + 3 && Math.abs(s.u - (s.depth - GrandHalls.BALCONY - 0.5)) < 0.5 && s.roll(3) % 3 == 0) {
			s.put(F + GrandHalls.B1 - 3, Blocks.SPORE_BLOSSOM.defaultBlockState());
		}
		if (!s.free()) {
			return;
		}
		// Four cherry trees.
		for (int i = 0; i < 4; i++) {
			double tw = (i - 1.5) * s.len / 4.5;
			double tu = (i % 2 == 0 ? -1 : 1) * 4.5;
			double r = Math.hypot(s.cu - tu, s.cw - tw);
			if (r < 0.6) {
				s.put(F - 1, b(Blocks.MOSS_BLOCK));
				s.fill(F, F + 4, b(Blocks.CHERRY_LOG));
				s.fill(F + 5, F + 6, b(Blocks.CHERRY_LEAVES).setValue(LeavesBlock.PERSISTENT, true));
				return;
			}
			if (r < 3.2) {
				int h = r < 2 ? 2 : 1;
				for (int y = F + 6 - h; y <= F + 5 + h - (r > 2.5 ? 1 : 0); y++) {
					s.put(y, b(Blocks.CHERRY_LEAVES).setValue(LeavesBlock.PERSISTENT, true));
				}
				if (r < 2.6) {
					s.put(F - 1, b(Blocks.MOSS_BLOCK));
					s.put(F, s.roll(9) % 3 == 0 ? Blocks.PINK_PETALS.defaultBlockState() : b(Blocks.MOSS_CARPET));
				}
				return;
			}
		}
		// Beds of flowers between the paths.
		boolean path = Math.floorMod(s.iw, 8) < 2 || Math.abs(s.cu) < 2.5;
		if (!path) {
			s.put(F - 1, b(Blocks.MOSS_BLOCK));
			int r = s.roll(14) % 10;
			Block[] flowers = {Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP,
				Blocks.FLOWERING_AZALEA, Blocks.AZALEA};
			if (r < flowers.length) {
				s.put(F, b(flowers[r]));
			}
		}
	}

	/** A great pool down the middle, a warm one at the pier end bubbling over magma, columns and lanterns round them. */
	static void baths(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd > 4 && fromEnd < 10 && Math.abs(s.cu) < 3) {
			s.put(F - 1, b(Blocks.WATER));
			s.put(F - 2, b(Blocks.MAGMA_BLOCK));
			return;
		}
		boolean pool = Math.abs(s.cu) < s.depth / 2 - 7 && s.w > 7 && fromEnd > 12;
		if (pool && !s.doorway()) {
			s.put(F - 1, b(Blocks.WATER));
			s.put(F - 2, s.roll(5) % 7 == 0 ? b(Blocks.SEA_LANTERN) : b(Blocks.PRISMARINE_BRICKS));
			return;
		}
		if (!s.free()) {
			return;
		}
		boolean rim = Math.abs(s.cu) < s.depth / 2 - 6 && s.w > 6 && fromEnd > 11;
		if (rim && Math.floorMod(s.iw, 5) == 0 && Math.abs(Math.abs(s.cu) - (s.depth / 2 - 6.5)) < 0.5) {
			s.fill(F, F + 2, b(Blocks.QUARTZ_PILLAR));
			s.put(F + 3, Blocks.LANTERN.defaultBlockState());
		}
	}

	/** A floor of stars, and in the middle the great armillary: three rings of gold round Aster, on a copper pillar. */
	static void observatory(Spot s) {
		int c = F + 9;
		double r = s.r();
		if (r < 7.5) {
			if (r < 0.6) {
				s.fill(F, c - 2, b(GrandHalls.WAXED_COPPER));
			}
			for (int y = c - 7; y <= c + 7; y++) {
				double ry = y - c;
				double sphere = Math.sqrt(s.cu * s.cu + s.cw * s.cw + ry * ry);
				if (sphere < 1.8) {
					s.put(y, sphere < 0.9 ? b(Blocks.SEA_LANTERN) : b(Blocks.AMETHYST_BLOCK));
				} else if (Math.abs(r - 6.5) < 0.5 && y == c || Math.abs(s.cw) < 0.5 && Math.abs(Math.hypot(s.cu, ry) - 6.5) < 0.5
					|| Math.abs(s.cu) < 0.5 && Math.abs(Math.hypot(s.cw, ry) - 5.5) < 0.5) {
					s.put(y, b(Blocks.GOLD_BLOCK));
				}
			}
		}
		if (!s.free() && r > 3.5) {
			return;
		}
		if (r > 3.5 && s.roll(19) % 23 == 0) {
			s.floor(b(Blocks.SEA_LANTERN));
		} else if (r > 3.5 && s.roll(19) % 23 == 1) {
			s.floor(b(Blocks.GOLD_BLOCK));
		}
	}

	/** Shelves of fight records along the walls, lecterns in rows, a sparring ring in the middle, tally boards. */
	static void bouts(Spot s) {
		double r = s.r();
		if (Math.abs(r - 5) < 0.5 && !(Math.abs(s.cw) < 1 && Math.abs(s.cu) > 3)) {
			s.put(F, b(Blocks.OAK_FENCE));
			return;
		}
		if (r < 4.5) {
			s.floor(b(Blocks.SAND));
			return;
		}
		if (!s.free()) {
			return;
		}
		if (s.byWall(2.0) && Math.floorMod(s.iw, 6) != 0) {
			shelves(s, 4, 3);
			return;
		}
		if (Math.floorMod(s.iw, 6) == 3 && Math.abs(Math.abs(s.cu) - 5) < 0.5 && Math.abs(s.cw) > 7) {
			if (Math.abs(s.cw) < 13 && s.cu > 0) {
				s.lectern(s.toGate, ColosseumLore.BOUTS);
			} else {
				s.put(F, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, s.toGate));
			}
		}
		if (Math.floorMod(s.iw, 6) == 0 && s.u < 2.5 && s.u >= 1) {
			String[][] tallies = {{"VAELOR v", "AUREL", "1 - 0", ""}, {"VAELOR v", "BRENNA", "1 - 0", ""}, {"VAELOR v", "DORN", "1 - 0", "(close)"},
				{"VAELOR v", "OLD MARR", "1 - 0", "(9 hours)"}, {"VAELOR v", "YSOLDE", "1 - 0", ""}, {"VAELOR v", "???", "", "(next)"}};
			s.sign(F + 2, s.out, DyeColor.WHITE, tallies[Math.floorMod(s.iw / 6, tallies.length)]);
		}
	}

	/** A stage at the pier end with crimson curtains, rows of seats facing it down the hall. */
	static void theatre(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 10 && Math.abs(s.cu) < s.depth / 2 - 3) {
			s.fill(F, F + 1, b(Blocks.DARK_OAK_PLANKS));
			if (fromEnd < 1.5 || Math.abs(s.cu) > s.depth / 2 - 4) {
				s.fill(F + 2, Math.min(s.top - 1, F + GrandHalls.B1 - 3), b(Blocks.WOOL.red()));
			} else if (fromEnd > 9) {
				s.put(F + 2, b(Blocks.DARK_OAK_SLAB));
			}
			return;
		}
		if (!s.free() || Math.abs(s.cu) < 1.6) {
			return;
		}
		if (Math.floorMod(s.iw, 2) == 0 && s.w > 5 && fromEnd > 13) {
			s.put(F, stair(Blocks.CRIMSON_STAIRS, s.toGate));
		}
	}

	/** Pens down both sides with bars, hay and troughs, each with its beast's name; a pond in the middle. */
	static void menagerie(Spot s) {
		if (s.r() < 3.0) {
			s.put(F - 1, b(Blocks.WATER));
			return;
		}
		if (s.doorway() || s.reserved()) {
			return;
		}
		boolean pen = Math.abs(s.cu) > s.depth / 2 - 6;
		if (!pen) {
			return;
		}
		double fromFront = 6 - (s.depth / 2 - Math.abs(s.cu));
		int m = Math.floorMod(s.iw, 8);
		if (m == 0) {
			s.fill(F, F + 2, b(Blocks.MOSSY_STONE_BRICKS));
			return;
		}
		if (fromFront < 1) {
			s.fill(F, F + 2, m == 4 ? AIR : bars(s.toPier));
			return;
		}
		s.floor(s.roll(3) % 3 == 0 ? b(Blocks.COARSE_DIRT) : b(Blocks.PODZOL));
		int r = s.roll(13) % 10;
		if (r < 2) {
			s.put(F, b(Blocks.HAY_BLOCK));
		} else if (r == 2) {
			s.put(F, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		}
		if (m == 4 && fromFront < 2 && fromFront >= 1) {
			String[] beasts = {"THE GRIFFON", "THE MANTICORE", "THE BASILISK", "THE WYRM", "THE WINTER WOLF", "THE SAND LION"};
			s.sign(F + 3, s.cu > 0 ? s.in : s.out, DyeColor.WHITE, beasts[Math.floorMod(s.iw / 8 + (s.cu > 0 ? 3 : 0), beasts.length)], "", "", "");
		}
	}

	// --- The Third Gallery ---

	/** Rows of bunks along both walls, footlockers at their ends, the captain's desk at the gate end. */
	static void barracks(Spot s) {
		if (s.doorway() || s.reserved() || s.w < 5) {
			return;
		}
		int m = Math.floorMod(s.iw, 3);
		boolean innerRow = s.u >= 1 && s.u < 3;
		boolean outerRow = s.depth - s.u >= 1 && s.depth - s.u < 3;
		if ((innerRow || outerRow) && m == 0) {
			boolean headCell = innerRow ? s.u < 2 : s.depth - s.u < 2;
			cot(s, s.roll(4) % 2 == 0 ? Blocks.CARPET.red() : Blocks.CARPET.gray(), headCell);
			return;
		}
		if ((innerRow || outerRow) && m == 1) {
			s.put(F, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			return;
		}
		if (s.r() < 2.0 && s.free()) {
			s.put(F, topSlab(Blocks.SPRUCE_SLAB));
		}
	}

	/** A sand floor with sparring rings, target dummies in them, archery targets on the pier wall. */
	static void training(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 1 && Math.abs(s.cu) < s.depth / 2 - 3) {
			s.fill(F + 1, F + 2, b(Blocks.TARGET));
			return;
		}
		if (!s.free()) {
			return;
		}
		if (Math.abs(s.cu) < s.depth / 2 - 4 && s.w > 5 && fromEnd > 5) {
			s.floor(b(Blocks.SAND));
		}
		double ring = Math.floorMod((int) Math.floor(s.cw + 1000), 16) - 8;
		double r = Math.hypot(s.cu, ring);
		if (Math.abs(s.cw) > s.len / 2 - 8) {
			return;
		}
		if (Math.abs(r - 5) < 0.5 && Math.abs(ring) > 1) {
			s.put(F, b(Blocks.OAK_FENCE));
		} else if (r < 0.6) {
			s.put(F, b(Blocks.HAY_BLOCK));
			s.put(F + 1, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, s.toGate));
		}
	}

	/** The Crown laid out across the floor (the field, the stands, the gates, the moat) with war tables round it. */
	static void maps(Spot s) {
		double r = s.r();
		double big = s.depth / 2 - 2;
		if (r < big) {
			double t = r / big;
			double ang = Math.toDegrees(Math.atan2(s.cw, s.cu));
			boolean gate = Math.abs(ang - 90 * Math.round(ang / 90)) * Math.PI / 180 * r < 0.7 && t > 0.3;
			BlockState f = t < 0.06 ? b(Blocks.DYED_TERRACOTTA.purple()) : t < 0.3 ? b(Blocks.DYED_TERRACOTTA.lime()) : gate ? b(Blocks.DYED_TERRACOTTA.lightGray())
				: t < 0.36 ? b(Blocks.DYED_TERRACOTTA.white()) : t < 0.7 ? (Math.floorMod((int) (t * 30), 2) == 0 ? b(Blocks.DYED_TERRACOTTA.orange())
				: b(Blocks.TERRACOTTA)) : t < 0.8 ? b(Blocks.DYED_TERRACOTTA.yellow()) : t < 0.88 ? b(Blocks.DYED_TERRACOTTA.lightBlue()) : b(Blocks.DYED_TERRACOTTA.green());
			s.floor(f);
			return;
		}
		if (!s.free()) {
			return;
		}
		if (Math.abs(r - big - 1.5) < 0.5 && s.roll(4) % 3 == 0) {
			s.put(F, s.roll(5) % 2 == 0 ? b(Blocks.CARTOGRAPHY_TABLE) : topSlab(Blocks.BIRCH_SLAB));
		}
		if (Math.abs(s.cw) < 0.5 && Math.abs(s.cu - big - 1.5) < 0.5) {
			s.sign(F + 1, s.in, DyeColor.WHITE, "YOU ARE", "HERE.", "HE IS UNDER", "THE FIELD.");
		}
	}

	/** Beds in rows, cauldrons of water and brewing stands between them, herbs in pots on the tables. */
	static void infirmary(Spot s) {
		if (s.doorway() || s.reserved() || s.w < 5 || s.len - s.w < 5) {
			return;
		}
		int m = Math.floorMod(s.iw, 4);
		boolean innerRow = s.u >= 1 && s.u < 3;
		boolean outerRow = s.depth - s.u >= 1 && s.depth - s.u < 3;
		if ((innerRow || outerRow) && m == 0) {
			boolean headCell = innerRow ? s.u < 2 : s.depth - s.u < 2;
			cot(s, Blocks.CARPET.lightBlue(), headCell);
			return;
		}
		if ((innerRow || outerRow) && m == 2) {
			s.put(F, s.roll(6) % 2 == 0 ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3) : b(Blocks.BREWING_STAND));
			return;
		}
		if (Math.abs(s.cu) < 1.6 && Math.floorMod(s.iw, 8) < 3 && s.free()) {
			s.put(F, topSlab(Blocks.BIRCH_SLAB));
			if (s.roll(8) % 2 == 0) {
				Block[] pots = {Blocks.POTTED_FERN, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_RED_MUSHROOM};
				s.put(F + 1, b(pots[s.roll(9) % pots.length]));
			}
		}
	}

	/** Ovens and smokers down the walls, counters with cauldrons, a great hearth in the middle, barrels of stores. */
	static void kitchens(Spot s) {
		if (s.r() < 1.0) {
			s.put(F, lit(Blocks.CAMPFIRE));
			return;
		}
		if (s.r() < 2.0) {
			s.put(F, b(Blocks.BRICKS));
			return;
		}
		if (!s.free()) {
			return;
		}
		Direction away = s.u < s.depth / 2 ? s.out : s.in;
		if (s.byWall(2.0) && Math.floorMod(s.iw, 4) < 3) {
			int m = Math.floorMod(s.iw, 4);
			s.put(F, m == 0 ? b(Blocks.SMOKER).setValue(FurnaceBlock.FACING, away).setValue(FurnaceBlock.LIT, true)
				: m == 1 ? b(Blocks.FURNACE).setValue(FurnaceBlock.FACING, away) : Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
			return;
		}
		if (Math.abs(Math.abs(s.cu) - 4) < 0.5 && Math.floorMod(s.iw, 5) != 0) {
			s.put(F, topSlab(Blocks.POLISHED_ANDESITE_SLAB));
			if (s.roll(7) % 5 == 0) {
				s.put(F + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			}
		}
	}

	/** Six tombs of the Six in two rows, their names at their heads; the seventh tomb, in the middle, open and empty. */
	static void crypt(Spot s) {
		String[] names = {"ORIN", "THE FIRST", "HALVARD", "THE SECOND", "SELENE", "THE THIRD", "BRAN", "THE FOURTH", "IDRIS", "THE FIFTH", "MAREN",
			"THE SIXTH"};
		if (s.r() < 2.6 && Math.abs(s.cu) < 1.5) {
			s.put(F, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
			if (Math.abs(s.cu) < 0.5 && Math.abs(s.cw) < 1.6) {
				s.put(F, AIR);
				s.put(F - 1, b(Blocks.SOUL_SOIL));
			}
			if (Math.abs(s.cw + 2) < 0.5 && Math.abs(s.cu) < 0.5) {
				s.sign(F + 1, s.toGate, DyeColor.PURPLE, "VAELOR", "THE SEVENTH", "(NOT HERE.", "NOT YET.)");
			}
			return;
		}
		for (int i = 0; i < 6; i++) {
			double tu = (i % 2 == 0 ? -1 : 1) * 4;
			double tw = (i / 2 - 1) * 10;
			if (in(s, tu - 1, tu + 1, tw - 2.5, tw + 2.5)) {
				s.put(F, b(Blocks.POLISHED_BLACKSTONE));
				s.put(F + 1, topSlab(Blocks.POLISHED_BLACKSTONE_SLAB));
				return;
			}
			if (in(s, tu - 1, tu + 1, tw - 3.5, tw - 2.5) && Math.abs(s.cu - tu) < 0.5) {
				s.sign(F + 1, s.toGate, DyeColor.WHITE, names[i * 2], names[i * 2 + 1], "OF THE SEVEN", "");
				return;
			}
			if (Math.abs(Math.abs(s.cu - tu) - 1.5) < 0.5 && Math.abs(Math.abs(s.cw - tw) - 3) < 0.5) {
				s.put(F, candles(Blocks.CANDLE, 3));
				return;
			}
		}
		if (s.free() && s.byWall(2.0) && s.roll(37) % 13 == 0) {
			s.put(F + 4, Blocks.COBWEB.defaultBlockState());
		}
		if (s.free() && Math.floorMod(s.iw, 7) == 3 && Math.abs(Math.abs(s.cu) - 7.5) < 0.5) {
			s.put(F, Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/** The round table of the Seven with its seven thrones (one cracked: his), and their banners round the walls. */
	static void council(Spot s) {
		double r = s.r();
		if (r < 2.5) {
			s.floor(r < 1 ? b(Blocks.GOLD_BLOCK) : b(Blocks.PURPUR_BLOCK));
			return;
		}
		if (r < 4.5) {
			s.put(F, topSlab(Blocks.POLISHED_DEEPSLATE_SLAB));
			if (s.roll(3) % 6 == 0) {
				s.put(F + 1, candles(Blocks.DYED_CANDLE.purple(), 2));
			}
			return;
		}
		for (int i = 0; i < 7; i++) {
			double a = i * Math.PI * 2 / 7 + Math.PI / 2;
			double su = Math.cos(a) * 5.5;
			double sw = Math.sin(a) * 5.5;
			if (Math.hypot(s.cu - su, s.cw - sw) < 0.6) {
				Direction back = dir(s, Math.cos(a), Math.sin(a));
				if (i == 0) {
					s.put(F, b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS));
				} else {
					s.put(F, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, back));
				}
				return;
			}
			if (Math.hypot(s.cu - Math.cos(a) * 6.5, s.cw - Math.sin(a) * 6.5) < 0.6 && i != 0) {
				s.fill(F, F + 1, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
				s.put(F + 2, b(Blocks.GOLD_BLOCK));
				return;
			}
		}
	}

	/** Betting booths along the inner wall under the odds boards, tables of coin, the bookmaker's counter at the pier end. */
	static void wagering(Spot s) {
		double fromEnd = s.len - s.w;
		if (fromEnd < 4 && fromEnd >= 2 && Math.abs(s.cu) < 4) {
			s.put(F, topSlab(Blocks.SMOOTH_SANDSTONE_SLAB));
			if (Math.abs(s.cu) < 0.5) {
				s.put(F + 1, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, s.toGate));
			}
			return;
		}
		if (!s.free()) {
			return;
		}
		if (s.u >= 1 && s.u < 2.5 && Math.floorMod(s.iw, 5) != 0) {
			s.put(F, topSlab(Blocks.SPRUCE_SLAB));
			if (Math.floorMod(s.iw, 5) == 2) {
				String[][] odds = {{"VAELOR", "TO WIN", "1 TO 1000", "NO TAKERS"}, {"CHALLENGER", "TO WIN", "1000 TO 1", "(ANY ONE)"},
					{"FIRST BLOOD", "VAELOR", "EVENS", ""}, {"LONGEST", "BOUT: OLD", "MARR, NINE", "HOURS"}};
				s.sign(F + 2, s.out, DyeColor.YELLOW, odds[Math.floorMod(s.iw / 5, odds.length)]);
			}
			return;
		}
		double tw = Math.floorMod((int) Math.floor(s.cw + 1000), 10) - 5;
		if (Math.hypot(s.cu - 1, tw) < 1.6 && Math.abs(s.cw) < s.len / 2 - 6) {
			s.put(F, topSlab(Blocks.DARK_OAK_SLAB));
			if (s.roll(5) % 4 == 0) {
				s.put(F + 1, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
			}
		}
	}
}

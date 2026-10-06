package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.practice.PracticeLayout.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the practice world's hub and arenas block by block (see {@link PracticeLayout} for where).
 * Everything is built on the flat desert; the crystal desert is the desert itself, with nothing in the
 * way, and only a ring of markers well outside it.
 */
final class PracticeBuilder {
	private static final int S = PracticeLayout.SURFACE;
	private static final int F = PracticeLayout.FLOOR;

	private final ServerLevel level;

	PracticeBuilder(ServerLevel level) {
		this.level = level;
	}

	void buildAll() {
		for (Site site : PracticeLayout.SITES) {
			clearVersionOneLobby(site);
		}
		hub();
		sword(PracticeLayout.SWORD);
		uhc(PracticeLayout.UHC);
		crystal(PracticeLayout.CRYSTAL);
		cart(PracticeLayout.CART);
		mace(PracticeLayout.MACE);
		// Green grass and leaves where there is grass (the desert biome tints them a dry olive).
		biome(PracticeLayout.UHC, net.minecraft.world.level.biome.Biomes.PLAINS);
		biome(PracticeLayout.CART, net.minecraft.world.level.biome.Biomes.PLAINS);
		for (Site site : PracticeLayout.SITES) {
			stands(site, palette(site));
			if (site == PracticeLayout.CART) {
				cartInfield(site);
				biomeOver(site, site.standEnd() + 2, net.minecraft.world.level.biome.Biomes.PLAINS);
			}
			viewingBox(site, palette(site));
			labels(site);
		}
		load(PracticeLayout.HALL_X - PracticeLayout.HALL_REACH_X, PracticeLayout.HALL_Z - PracticeLayout.HALL_REACH_Z,
			PracticeLayout.HALL_X + PracticeLayout.HALL_REACH_X, PracticeLayout.HALL_Z + PracticeLayout.HALL_REACH_Z);
		TrainingHall.build(level);
		// The version marker under the hub: an older or missing one means the world gets built again.
		set(0, S - 2, PracticeLayout.VERSION, Blocks.LODESTONE.defaultBlockState());
	}

	static boolean built(ServerLevel level) {
		return level.getBlockState(new BlockPos(0, S - 2, PracticeLayout.VERSION)).is(Blocks.LODESTONE);
	}

	/** Loads (generating if need be) every chunk in the box, so building never writes into unloaded ground. */
	void load(int minX, int minZ, int maxX, int maxZ) {
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	private void set(int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	/** Sets the biome over a square of half-side {@code half} round the site's centre, near the ground. */
	private void biomeOver(Site site, int half, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key) {
		var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(key);
		for (int x = site.centerX() - half; x <= site.centerX() + half; x += 16) {
			for (int z = site.centerZ() - half; z <= site.centerZ() + half; z += 16) {
				BlockPos from = new BlockPos(x, S - 4, z);
				BlockPos to = new BlockPos(Math.min(x + 15, site.centerX() + half), F + 30, Math.min(z + 15, site.centerZ() + half));
				net.minecraft.server.commands.FillBiomeCommand.fill(level, from, to, holder);
			}
		}
	}

	/** Sets the biome over a site, a chunk-sized column at a time (the same as /fillbiome, within its size limit). */
	private void biome(Site site, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key) {
		var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(key);
		for (int x = site.minX() - 4; x <= site.maxX() + 4; x += 16) {
			for (int z = site.minZ() - 4; z <= site.maxZ() + 4; z += 16) {
				BlockPos from = new BlockPos(x, S - site.depth(), z);
				BlockPos to = new BlockPos(Math.min(x + 15, site.maxX() + 4), Math.min(F + 24, site.maxY()), Math.min(z + 15, site.maxZ() + 4));
				net.minecraft.server.commands.FillBiomeCommand.fill(level, from, to, holder).ifRight(
					e -> io.github.flick256.sparbot.SparBot.LOGGER.warn("Practice world biome at {}: {}", from, e.getMessage()));
			}
		}
	}

	/** Air from the floor up over a whole site, clearing anything a previous build left. */
	private void clearAbove(Site site) {
		load(site.minX() - 6, site.minZ() - 6, site.maxX() + 6, site.maxZ() + 8);
		fill(site.minX() - 4, F, site.minZ() - 4, site.maxX() + 4, site.maxY(), site.maxZ() + 6, Blocks.AIR.defaultBlockState());
	}


	private static final BlockState[] PAD_COLORS = {Blocks.CONCRETE.lightBlue().defaultBlockState(), Blocks.CONCRETE.lime().defaultBlockState(),
		Blocks.CONCRETE.magenta().defaultBlockState(), Blocks.CONCRETE.orange().defaultBlockState(), Blocks.CONCRETE.yellow().defaultBlockState()};
	private static final Block[] PAD_BANNERS = {Blocks.WALL_BANNER.lightBlue(), Blocks.WALL_BANNER.lime(), Blocks.WALL_BANNER.magenta(),
		Blocks.WALL_BANNER.orange(), Blocks.WALL_BANNER.yellow()};
	private static final int[] PAD_TEXT = {0x55FFFF, 0x55FF55, 0xFF55FF, 0xFFAA00, 0xFFFF55};

	/**
	 * The hub: a round sandstone plaza with a fountain in the middle, a ring of palms and obelisks, and a
	 * coloured pad per arena between two banner pillars, its name floating over it.
	 */
	private void hub() {
		int r = PracticeLayout.HUB_RADIUS;
		load(-r - 12, -r - 12, r + 12, r + 12);
		fill(-r - 8, F, -r - 8, r + 8, F + 14, r + 8, Blocks.AIR.defaultBlockState());
		PracticeLabels.clear(level, new net.minecraft.world.phys.AABB(-r - 8, F - 2, -r - 8, r + 8, F + 20, r + 8));
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > r + 1.5) {
					continue;
				}
				double angle = Math.toDegrees(Math.atan2(z, x));
				boolean ray = d > 4 && d < 12 && Math.floorMod((int) Math.round(angle), 45) <= 2;
				BlockState floor = d > r + 0.5 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
					: Math.abs(d - 12) < 0.6 ? Blocks.CUT_SANDSTONE.defaultBlockState()
					: Math.abs(d - 6) < 0.6 ? Blocks.GLAZED_TERRACOTTA.orange().defaultBlockState()
					: ray ? Blocks.CUT_SANDSTONE.defaultBlockState()
					: Blocks.SMOOTH_SANDSTONE.defaultBlockState();
				set(x, S, z, floor);
				if (d > r + 0.5) {
					// A low balustrade round the edge, broken by the pads' banner pillars.
					set(x, F, z, Blocks.SANDSTONE_WALL.defaultBlockState());
				}
			}
		}
		fountain();
		// Obelisks on the diagonals and palms beyond the plaza.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int ox = sx * 11;
				int oz = sz * 11;
				set(ox, F, oz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				fill(ox, F + 1, oz, ox, F + 5, oz, Blocks.CUT_SANDSTONE.defaultBlockState());
				set(ox, F + 6, oz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				set(ox, F + 7, oz, Blocks.LANTERN.defaultBlockState());
				palm(sx * 20, F, sz * 18);
				palm(sx * 26, F, sz * 8);
			}
		}
		for (int i = 0; i < 60; i++) {
			int x = PracticeLayout.scatter(i, 7, 5) % 81 - 40;
			int z = PracticeLayout.scatter(i, 11, 6) % 81 - 40;
			if (x * x + z * z > (r + 4) * (r + 4)) {
				set(x, F, z, i % 3 == 0 ? Blocks.CACTUS.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState());
			}
		}
		for (int i = 0; i < PracticeLayout.SITES.size(); i++) {
			Site site = PracticeLayout.SITES.get(i);
			int[] p = PracticeLayout.pad(site);
			// The pad: a coloured block under a pressure plate, ringed in cut sandstone.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					set(p[0] + dx, S, p[1] + dz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				}
			}
			set(p[0], S, p[1], PAD_COLORS[i % PAD_COLORS.length]);
			set(p[0], F, p[1], Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
			// Two banner pillars either side of it, across the line from the centre.
			double len = Math.hypot(p[0], p[1]);
			double tx = -p[1] / len;
			double tz = p[0] / len;
			for (int side = -1; side <= 1; side += 2) {
				int bx = (int) Math.round(p[0] + side * 2.2 * tx);
				int bz = (int) Math.round(p[1] + side * 2.2 * tz);
				fill(bx, F, bz, bx, F + 3, bz, Blocks.CUT_SANDSTONE.defaultBlockState());
				set(bx, F + 4, bz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				set(bx, F + 5, bz, Blocks.LANTERN.defaultBlockState());
				// The banner hangs on the pillar's side facing the plaza's centre.
				net.minecraft.core.Direction toCentre = horizontal(-bx, -bz);
				BlockPos banner = new BlockPos(bx, F + 3, bz).relative(toCentre);
				if (level.getBlockState(banner).isAir()) {
					level.setBlock(banner, PAD_BANNERS[i % PAD_BANNERS.length].defaultBlockState()
						.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, toCentre), Block.UPDATE_CLIENTS);
				}
			}
			int color = PAD_TEXT[i % PAD_TEXT.length];
			PracticeLabels.put(level, p[0] + 0.5, F + 2.2, p[1] + 0.5, Component.literal(site.displayName())
				.withStyle(st -> st.withColor(color).withBold(true))
				.append(Component.literal("\n" + String.join(", ", site.modes())).withStyle(st -> st.withColor(0xBBBBBB).withBold(false))), 1.1F,
				0x60000000);
		}
		grandGateway();
		hallGateway();
		PracticeLabels.put(level, 0.5, F + 9.5, 0.5, Component.literal("SparBot Practice").withStyle(st -> st.withColor(0xFFD966).withBold(true))
			.append(Component.literal("\nstand on a pad to visit an arena").withStyle(st -> st.withColor(0xDDDDDD).withBold(false))), 2.2F, 0);
	}

	/** The training hall's pad, west of the plaza through a gap in the balustrade, between stone-brick pillars with lanterns. */
	private void hallGateway() {
		int[] p = PracticeLayout.HALL_PAD;
		fill(p[0] - 1, F, p[1] - 1, -PracticeLayout.HUB_RADIUS + 1, F + 3, p[1] + 1, Blocks.AIR.defaultBlockState());
		for (int x = p[0] - 1; x <= -PracticeLayout.HUB_RADIUS + 1; x++) {
			for (int z = p[1] - 1; z <= p[1] + 1; z++) {
				set(x, S, z, z == p[1] ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		set(p[0], S, p[1], Blocks.GOLD_BLOCK.defaultBlockState());
		set(p[0], F, p[1], Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
		for (int side = -2; side <= 2; side += 4) {
			int z = p[1] + side;
			fill(p[0], F, z, p[0], F + 4, z, Blocks.STONE_BRICKS.defaultBlockState());
			set(p[0], F + 5, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			set(p[0], F + 6, z, Blocks.LANTERN.defaultBlockState());
			level.setBlock(new BlockPos(p[0] + 1, F + 3, z), Blocks.WALL_BANNER.yellow().defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.EAST), Block.UPDATE_CLIENTS);
		}
		fill(p[0], F + 5, p[1] - 1, p[0], F + 5, p[1] + 1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		PracticeLabels.put(level, p[0] + 0.5, F + 2.4, p[1] + 0.5, Component.literal("Training Hall")
			.withStyle(st -> st.withColor(0xFFD966).withBold(true))
			.append(Component.literal("\nskill drills: bronze, silver, gold").withStyle(st -> st.withColor(0xDDDDDD).withBold(false))), 1.2F,
			0x60000000);
	}

	/** The Celestial Colosseum's pad, south of the plaza through a gap in the balustrade, between amethyst pillars. */
	private void grandGateway() {
		int[] p = PracticeLayout.GRAND_PAD;
		fill(p[0] - 1, F, PracticeLayout.HUB_RADIUS - 1, p[0] + 1, F, p[1] + 1, Blocks.AIR.defaultBlockState());
		for (int z = PracticeLayout.HUB_RADIUS - 1; z <= p[1] + 1; z++) {
			for (int x = p[0] - 1; x <= p[0] + 1; x++) {
				set(x, S, z, x == p[0] ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			}
		}
		set(p[0], S, p[1], Blocks.AMETHYST_BLOCK.defaultBlockState());
		set(p[0], F, p[1], Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
		for (int side = -2; side <= 2; side += 4) {
			int x = p[0] + side;
			set(x, S, p[1], Blocks.CHISELED_DEEPSLATE.defaultBlockState());
			fill(x, F, p[1], x, F + 4, p[1], Blocks.AMETHYST_BLOCK.defaultBlockState());
			set(x, F + 5, p[1], Blocks.PURPUR_PILLAR.defaultBlockState());
			set(x, F + 6, p[1], Blocks.END_ROD.defaultBlockState());
			level.setBlock(new BlockPos(x, F + 3, p[1] - 1), Blocks.WALL_BANNER.purple().defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
		}
		fill(p[0] - 1, F + 5, p[1], p[0] + 1, F + 5, p[1], Blocks.PURPUR_SLAB.defaultBlockState());
		PracticeLabels.put(level, p[0] + 0.5, F + 2.4, p[1] + 0.5, Component.literal("The Celestial Colosseum")
			.withStyle(st -> st.withColor(0xC77DFF).withBold(true))
			.append(Component.literal("\nfree for all: it resets once everyone has left").withStyle(st -> st.withColor(0xDDDDDD).withBold(false))), 1.2F,
			0x60000000);
	}

	/** A sandstone fountain: a round basin, and water falling from a lit column into it. */
	private void fountain() {
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= 2.6) {
					set(x, S - 2, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
					set(x, S - 1, z, Blocks.WATER.defaultBlockState());
					set(x, S, z, Blocks.WATER.defaultBlockState());
				} else if (d <= 3.6) {
					set(x, S, z, Blocks.CUT_SANDSTONE.defaultBlockState());
					set(x, F, z, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
				}
			}
		}
		fill(0, S - 1, 0, 0, F + 3, 0, Blocks.CHISELED_SANDSTONE.defaultBlockState());
		set(0, F + 4, 0, Blocks.SEA_LANTERN.defaultBlockState());
		set(0, F + 5, 0, Blocks.WATER.defaultBlockState());
	}

	/** The horizontal direction closest to (dx, dz). */
	private static net.minecraft.core.Direction horizontal(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
		}
		return dz >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
	}

	/** A quartz court with a blackstone border, a low wall and lantern posts at the corners. */
	private void sword(Site site) {
		clearAbove(site);
		int cx = site.centerX();
		int cz = site.centerZ();
		int r = site.radius();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				boolean edge = Math.abs(x) == r + 1 || Math.abs(z) == r + 1;
				boolean border = Math.abs(x) >= r - 1 || Math.abs(z) >= r - 1;
				boolean centreLine = z == 0 || Math.abs(x) + Math.abs(z) == 4;
				boolean grout = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
				BlockState floor = edge || border ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
					: x == 0 && z == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : centreLine ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
					: grout ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
				fill(cx + x, S - site.depth(), cz + z, cx + x, S - 1, cz + z, Blocks.STONE.defaultBlockState());
				set(cx + x, S, cz + z, floor);
				if (edge) {
					set(cx + x, F, cz + z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int px = cx + sx * (r + 1);
				int pz = cz + sz * (r + 1);
				fill(px, F, pz, px, F + 3, pz, Blocks.QUARTZ_PILLAR.defaultBlockState());
				set(px, F + 4, pz, Blocks.LANTERN.defaultBlockState());
			}
		}
	}

	/**
	 * The UHC meadow: rolling grass in a ring of old stone-brick walls with lanterns on top, flowers and
	 * tall grass, a few oaks by the wall, a pond and some mossy boulders. Dirt and stone underneath for
	 * buckets and digging.
	 */
	private void uhc(Site site) {
		clearAbove(site);
		int cx = site.centerX();
		int cz = site.centerZ();
		int r = site.radius();
		for (int dx = -r - 2; dx <= r + 2; dx++) {
			for (int dz = -r - 2; dz <= r + 2; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 2.5) {
					continue;
				}
				fill(x, S - site.depth(), z, x, S - 4, z, Blocks.STONE.defaultBlockState());
				if (d > r + 0.5) {
					// The wall: two blocks thick, seven high, weathered, with battlements and lanterns.
					int roll = PracticeLayout.scatter(x, z, 1);
					BlockState brick = roll < 200 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
						: roll < 300 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
					fill(x, S - 3, z, x, F + 6, z, brick);
					if (d > r + 1.5) {
						int angle = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)));
						boolean merlon = Math.floorMod(angle, 12) < 6;
						if (merlon) {
							set(x, F + 7, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
						}
						if (Math.floorMod(angle, 24) == 0) {
							set(x, F + 7, z, Blocks.STONE_BRICKS.defaultBlockState());
							set(x, F + 8, z, Blocks.LANTERN.defaultBlockState());
						}
					}
					continue;
				}
				int h = PracticeLayout.meadowHeight(dx, dz);
				fill(x, S - 3, z, x, S + h - 1, z, Blocks.DIRT.defaultBlockState());
				set(x, S + h, z, Blocks.GRASS_BLOCK.defaultBlockState());
				int roll = PracticeLayout.scatter(x, z, 2);
				BlockState plant = roll < 90 ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 98 ? Blocks.DANDELION.defaultBlockState()
					: roll < 104 ? Blocks.POPPY.defaultBlockState() : roll < 108 ? Blocks.CORNFLOWER.defaultBlockState()
					: roll < 111 ? Blocks.OXEYE_DAISY.defaultBlockState() : null;
				if (plant != null && d < r - 1) {
					set(x, F + h, z, plant);
				}
			}
		}
		// A pond off to one side, two deep, with a sandy edge.
		int px = cx + 15;
		int pz = cz - 4;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				int h = PracticeLayout.meadowHeight(px + dx - cx, pz + dz - cz);
				if (d <= 2.6) {
					fill(px + dx, F + h, pz + dz, px + dx, F + 3, pz + dz, Blocks.AIR.defaultBlockState());
					fill(px + dx, S - 1, pz + dz, px + dx, S + h, pz + dz, Blocks.AIR.defaultBlockState());
					set(px + dx, S - 2, pz + dz, Blocks.SAND.defaultBlockState());
					set(px + dx, S - 1, pz + dz, Blocks.WATER.defaultBlockState());
					set(px + dx, S, pz + dz, Blocks.WATER.defaultBlockState());
					fill(px + dx, S + 1, pz + dz, px + dx, S + 3, pz + dz, Blocks.AIR.defaultBlockState());
				} else if (d <= 3.6) {
					fill(px + dx, S + 1, pz + dz, px + dx, F + 3, pz + dz, Blocks.AIR.defaultBlockState());
					set(px + dx, S, pz + dz, Blocks.SAND.defaultBlockState());
				}
			}
		}
		// Oaks by the wall, out of the middle of the fight.
		int[][] trees = {{-22, -14}, {20, 17}, {-17, 21}, {24, -16}};
		for (int[] t : trees) {
			oak(cx + t[0], F + PracticeLayout.meadowHeight(t[0], t[1]), cz + t[1], 4 + PracticeLayout.scatter(t[0], t[1], 3) % 2);
		}
		// Mossy boulders.
		int[][] rocks = {{-9, 6}, {8, 20}, {-25, 2}};
		for (int[] b : rocks) {
			int y = F + PracticeLayout.meadowHeight(b[0], b[1]);
			set(cx + b[0], y, cz + b[1], Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			set(cx + b[0] + 1, y, cz + b[1], Blocks.COBBLESTONE.defaultBlockState());
			set(cx + b[0], y, cz + b[1] + 1, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			set(cx + b[0], y + 1, cz + b[1], Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState());
		}
	}

	/**
	 * How a site's stands look.
	 *
	 * @param aisle the stairs of the aisles that break up the rows
	 * @param back the back wall
	 * @param crest the back wall's battlements
	 * @param banner the wall banner hung on the pillars
	 */
	private record Palette(BlockState fill, Block seat, Block aisle, BlockState back, BlockState crest, BlockState pillar, BlockState light, Block banner,
		BlockState boxFloor, BlockState rail, BlockState roof, Block carpet, int textColor) {
	}

	private static Palette palette(Site site) {
		return switch (site.id()) {
			case "uhc" -> new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
				Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
				Blocks.LANTERN.defaultBlockState(), Blocks.WALL_BANNER.red(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
				Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.CARPET.red(), 0x7CFC5A);
			case "crystal" -> new Palette(Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SANDSTONE_STAIRS,
				Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
				Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.WALL_BANNER.blue(), Blocks.CUT_SANDSTONE.defaultBlockState(),
				Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), Blocks.CARPET.blue(), 0x66E0FF);
			case "cart" -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS,
				Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
				Blocks.LANTERN.defaultBlockState(), Blocks.WALL_BANNER.red(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
				Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), Blocks.DARK_OAK_SLAB.defaultBlockState(), Blocks.CARPET.red(), 0xFF6B5A);
			case "mace" -> new Palette(Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.TUFF_BRICK_STAIRS, Blocks.POLISHED_TUFF_STAIRS,
				Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.TUFF_BRICK_WALL.defaultBlockState(), Blocks.CHISELED_TUFF_BRICKS.defaultBlockState(),
				Blocks.COPPER_LANTERN.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState(),
				Blocks.WALL_BANNER.orange(), Blocks.POLISHED_TUFF.defaultBlockState(), Blocks.TUFF_BRICK_WALL.defaultBlockState(),
				Blocks.TUFF_BRICK_SLAB.defaultBlockState(), Blocks.CARPET.orange(), 0xFFB347);
			default -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.QUARTZ_STAIRS,
				Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
				Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.WALL_BANNER.white(),
				Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
				Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), Blocks.CARPET.black(), 0xF5F5F5);
		};
	}

	/**
	 * The grandstands: rows of seats rising one block per row away from the arena, facing it, with aisles
	 * every few blocks, a back wall with battlements, and pillars carrying lanterns and banners (inside and
	 * out). Straight stands also get a canopy over their top rows. They start outside the arena (and out of
	 * blast reach for blast modes), so fights and round resets never touch them.
	 */
	private void stands(Site site, Palette p) {
		int cx = site.centerX();
		int cz = site.centerZ();
		int start = site.standStart();
		int rows = site.standRows();
		int end = site.standEnd();
		int top = F + site.standBase() + rows;
		int wallTop = top + 2;
		load(cx - end - 4, cz - end - 4, cx + end + 4, cz + end + 4);
		for (int dx = -end - 1; dx <= end + 1; dx++) {
			for (int dz = -end - 1; dz <= end + 1; dz++) {
				double d = site.round() ? Math.sqrt(dx * dx + dz * dz) : Math.max(Math.abs(dx), Math.abs(dz));
				int k = (int) Math.floor(d) - start;
				if (k < -1 || k > rows) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				// Where along the stand this column is: arc length round a ring, the side's other coordinate on a straight one.
				double along = site.round() ? Math.atan2(dz, dx) * d : Math.abs(dx) > Math.abs(dz) ? dz : dx;
				Direction out = site.round() ? horizontal(dx, dz) : Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
					: Math.abs(dz) > Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : null;
				if (k == -1) {
					// A walkway between the arena's edge and the first row.
					if (!site.round()) {
						set(x, S, z, p.fill());
					}
					continue;
				}
				if (site == PracticeLayout.CART && k < 0) {
					continue;
				}
				fill(x, F + site.standBase() + k, z, x, wallTop + 12, z, Blocks.AIR.defaultBlockState());
				if (k == rows) {
					fill(x, S - 2, z, x, wallTop, z, p.back());
					arcade(site, x, z, out, along, p);
					boolean pillar = Math.floorMod(Math.round(along), site.round() ? 10 : 8) == 4;
					if (pillar) {
						int height = site.round() ? 5 : 3;
						fill(x, wallTop + 1, z, x, wallTop + height, z, p.pillar());
						if (site.round()) {
							set(x, wallTop + height + 1, z, p.light());
						}
						if (out != null) {
							banner(x, wallTop + height - 1, z, out.getOpposite(), p.banner());
							banner(x, F + 3, z, out, p.banner());
						}
					} else if (Math.floorMod(Math.round(along), 2) == 0) {
						set(x, wallTop + 1, z, p.crest());
					}
					if (!site.round()) {
						set(x, wallTop + 4, z, p.roof());
					}
					continue;
				}
				int seatY = F + site.standBase() + k;
				fill(x, S - 2, z, x, seatY - 1, z, p.fill());
				boolean aisle = Math.floorMod(Math.round(along), 12) == 0;
				set(x, seatY, z, out == null ? p.fill() : (aisle ? p.aisle() : p.seat()).defaultBlockState().setValue(StairBlock.FACING, out));
				if (!site.round() && k >= rows - 3) {
					// The canopy over the top rows, with lanterns hanging from it.
					set(x, wallTop + 4, z, p.roof());
					if (k == rows - 2 && Math.floorMod(Math.round(along), 8) == 0) {
						set(x, wallTop + 3, z, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
					}
				}
			}
		}
	}

	/**
	 * The back wall's outer face: two tiers of arched openings between the pillars, dark behind as if the
	 * stand were hollow, each under a lintel.
	 */
	private void arcade(Site site, int x, int z, Direction out, double along, Palette p) {
		if (out == null) {
			return;
		}
		int period = site.round() ? 10 : 8;
		int phase = Math.floorMod(Math.round(along) - (site.round() ? 9 : 0) + 1, period);
		if (phase > 2) {
			return;
		}
		BlockPos behind = new BlockPos(x, 0, z).relative(out.getOpposite());
		BlockState shadow = site == PracticeLayout.SWORD ? Blocks.CONCRETE.black().defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		// One tall arch per bay, five blocks high from the ground, under a lintel with a keystone in the middle.
		for (int y = F; y <= F + 4; y++) {
			set(x, y, z, Blocks.AIR.defaultBlockState());
			set(behind.getX(), y, behind.getZ(), shadow);
		}
		set(x, S, z, p.boxFloor());
		set(x, F + 5, z, phase == 1 ? p.pillar() : p.back());
		if (phase != 1) {
			// The arch's shoulders: upside-down stairs in the top corners.
			set(x, F + 4, z, p.seat().defaultBlockState().setValue(StairBlock.FACING, phase == 0 ? clockwise(out) : clockwise(out).getOpposite())
				.setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
		}
	}

	private static Direction clockwise(Direction d) {
		return d.getClockWise();
	}

	private void banner(int x, int y, int z, Direction facing, Block banner) {
		BlockPos at = new BlockPos(x, y, z).relative(facing);
		if (level.getBlockState(at).isAir()) {
			level.setBlock(at, banner.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, facing), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * The viewing box at the top of the south stand, where the hub pad brings you: a carpeted platform with a
	 * rail in front, a canopy on corner pillars, and the pad back to the hub behind.
	 */
	private void viewingBox(Site site, Palette p) {
		int cx = site.centerX();
		int front = site.lobbyZ() - 1;
		int back = site.lobbyZ() + 1;
		int floor = site.lobbyY() - 1;
		for (int dx = -4; dx <= 4; dx++) {
			for (int z = front; z <= back; z++) {
				int x = cx + dx;
				fill(x, S - 2, z, x, floor - 1, z, p.fill());
				set(x, floor, z, Math.abs(dx) == 4 ? p.back() : p.boxFloor());
				fill(x, floor + 1, z, x, floor + 5, z, Blocks.AIR.defaultBlockState());
				if (Math.abs(dx) == 4) {
					set(x, floor + 1, z, p.rail());
				} else if (z == front) {
					set(x, floor + 1, z, p.rail());
				} else if (!(dx == 0 && z == back)) {
					set(x, floor + 1, z, p.carpet().defaultBlockState());
				}
			}
		}
		for (int dx = -4; dx <= 4; dx += 8) {
			for (int z = front; z <= back; z += back - front) {
				fill(cx + dx, floor + 1, z, cx + dx, floor + 4, z, p.pillar());
			}
		}
		fill(cx - 4, floor + 5, front, cx + 4, floor + 5, back, p.roof());
		set(cx, floor + 4, site.lobbyZ(), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		set(cx, floor, back, Blocks.CONCRETE.lightBlue().defaultBlockState());
		set(cx, floor + 1, back, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
	}

	/** The arena's name over its far stand, and a label over the pad back to the hub. */
	private void labels(Site site) {
		int cx = site.centerX();
		int cz = site.centerZ();
		int end = site.standEnd();
		PracticeLabels.clear(level, new net.minecraft.world.phys.AABB(cx - end - 6, F - 4, cz - end - 6, cx + end + 6, F + 80, cz + end + 6));
		Palette p = palette(site);
		PracticeLabels.put(level, cx + 0.5, F + site.standBase() + site.standRows() + 10, cz - end + 0.5,
			Component.literal(site.displayName()).withStyle(st -> st.withColor(p.textColor()).withBold(true)), 8.0F, 0);
		PracticeLabels.put(level, cx + 0.5, site.lobbyY() + 1.4, site.lobbyZ() + 1.5, Component.literal("Back to the hub")
			.withStyle(st -> st.withColor(0x55FFFF).withBold(true))
			.append(Component.literal("\nfight here: the menu's Practice tab").withStyle(st -> st.withColor(0xCCCCCC).withBold(false))), 0.8F, 0x60000000);
	}

	/** Version 1 put a little platform where the stands now are (or, by the crystal desert, out on the sand): take it away. */
	private void clearVersionOneLobby(Site site) {
		int x = site.centerX();
		int z = site.centerZ() + site.radius() + 4;
		load(x - 4, z - 3, x + 4, z + 4);
		fill(x - 3, F, z - 2, x + 3, F + 4, z + 3, Blocks.AIR.defaultBlockState());
		fill(x - 3, S, z - 2, x + 3, S, z + 3, Blocks.SAND.defaultBlockState());
		PracticeLabels.clear(level, new net.minecraft.world.phys.AABB(x - 4, F - 1, z - 3, x + 4, F + 6, z + 4));
	}

	/** A desert palm: a leaning jungle-wood trunk with a fan of leaves. */
	private void palm(int x, int y, int z) {
		int tx = x;
		for (int dy = 0; dy < 6; dy++) {
			if (dy == 3) {
				tx += Integer.signum(x);
			}
			set(tx, y + dy, z, Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState());
		}
		BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int top = y + 6;
		set(tx, top, z, leaves);
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms) {
			set(tx + a[0], top, z + a[1], leaves);
			set(tx + 2 * a[0], top, z + 2 * a[1], leaves);
			set(tx + 3 * a[0], top - 1, z + 3 * a[1], leaves);
		}
	}

	private void oak(int x, int y, int z, int trunk) {
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dy = trunk - 2; dy <= trunk + 1; dy++) {
			int rad = dy >= trunk ? 1 : 2;
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					if (Math.abs(dx) == rad && Math.abs(dz) == rad && (rad == 2 || dy == trunk + 1)) {
						continue;
					}
					set(x + dx, y + dy, z + dz, leaves);
				}
			}
		}
		fill(x, y, z, x, y + trunk - 1, z, Blocks.OAK_LOG.defaultBlockState());
	}

	/**
	 * The crystal desert is the open desert itself: nothing to clear, nothing in the way, sand and
	 * sandstone over stone and deepslate down to bedrock. Only cut-sandstone markers with soul lanterns
	 * stand round it, well outside where anyone fights.
	 */
	private void crystal(Site site) {
		load(site.minX() - 8, site.minZ() - 8, site.maxX() + 8, site.maxZ() + 8);
		int cx = site.centerX();
		int cz = site.centerZ();
		double ring = site.radius() + 5;
		for (int i = 0; i < 16; i++) {
			double a = 2 * Math.PI * i / 16;
			int x = cx + (int) Math.round(Math.cos(a) * ring);
			int z = cz + (int) Math.round(Math.sin(a) * ring);
			fill(x, F, z, x, F + 1, z, Blocks.CUT_SANDSTONE.defaultBlockState());
			set(x, F + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/** Grass between the cart field's fence and its stands (they stand back out of blast reach). */
	private void cartInfield(Site site) {
		int r = site.radius() + 2;
		int start = site.standStart();
		for (int dx = -start; dx <= start; dx++) {
			for (int dz = -start; dz <= start; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d >= r && d < start) {
					int x = site.centerX() + dx;
					int z = site.centerZ() + dz;
					set(x, S, z, d == start - 2 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
					int roll = PracticeLayout.scatter(x, z, 9);
					if (d < start - 3 && roll < 60) {
						set(x, F, z, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
	}

	/** A grass field (rails go on anything solid) with patches of coarse dirt, fenced, lanterns on posts. */
	private void cart(Site site) {
		clearAbove(site);
		int cx = site.centerX();
		int cz = site.centerZ();
		int r = site.radius();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				fill(x, S - site.depth(), z, x, S - 1, z, Blocks.DIRT.defaultBlockState());
				int roll = PracticeLayout.scatter(x, z, 4);
				set(x, S, z, roll < 40 ? Blocks.COARSE_DIRT.defaultBlockState() : roll < 70 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
				boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
				if (edge) {
					boolean post = Math.floorMod(dx + dz, 8) == 0;
					set(x, F, z, post ? Blocks.STRIPPED_OAK_LOG.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
					if (post) {
						set(x, F + 1, z, Blocks.LANTERN.defaultBlockState());
					}
				}
			}
		}
	}

	/** A tuff and copper court for the mace and the bow: open sky above for wind-charge jumps. */
	private void mace(Site site) {
		clearAbove(site);
		int cx = site.centerX();
		int cz = site.centerZ();
		int r = site.radius();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
				boolean ring = Math.abs(Math.sqrt(dx * dx + dz * dz) - 8) < 0.5;
				fill(x, S - site.depth(), z, x, S - 1, z, Blocks.TUFF.defaultBlockState());
				set(x, S, z, edge ? Blocks.TUFF_BRICKS.defaultBlockState() : ring ? Blocks.CUT_COPPER.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState()
					: (Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0) ? Blocks.POLISHED_TUFF.defaultBlockState() : Blocks.CHISELED_TUFF.defaultBlockState());
				if (edge) {
					set(x, F, z, Blocks.TUFF_BRICK_WALL.defaultBlockState());
					if (Math.floorMod(dx + dz, 10) == 0) {
						set(x, F + 1, z, Blocks.COPPER_LANTERN.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState());
					}
				}
			}
		}
	}
}

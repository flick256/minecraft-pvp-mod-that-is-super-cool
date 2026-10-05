package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.practice.PracticeLayout.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
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
			lobby(site);
		}
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

	private void sign(int x, int y, int z, int rotation, String... lines) {
		BlockPos pos = new BlockPos(x, y, z);
		level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = new SignText();
			for (int i = 0; i < lines.length && i < 4; i++) {
				text = text.setMessage(i, Component.literal(lines[i]));
			}
			sign.setText(text, true);
			sign.setWaxed(true);
			sign.setChanged();
			level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Sign rotation (0-15, 0 = facing south) for a sign at dx, dz from a point that should face it. */
	private static int facing(int dx, int dz) {
		double yaw = Math.toDegrees(Math.atan2(dx, -dz));
		return Math.floorMod((int) Math.round(yaw / 22.5), 16);
	}

	private static final BlockState[] PAD_COLORS = {Blocks.CONCRETE.lightBlue().defaultBlockState(), Blocks.CONCRETE.lime().defaultBlockState(),
		Blocks.CONCRETE.magenta().defaultBlockState(), Blocks.CONCRETE.orange().defaultBlockState(), Blocks.CONCRETE.yellow().defaultBlockState()};

	/** A round sandstone plaza with a lantern tower in the middle and a coloured pad (with its sign) per arena. */
	private void hub() {
		int r = PracticeLayout.HUB_RADIUS;
		load(-r - 4, -r - 4, r + 4, r + 4);
		fill(-r - 2, F, -r - 2, r + 2, F + 12, r + 2, Blocks.AIR.defaultBlockState());
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > r + 0.5) {
					continue;
				}
				BlockState floor = Math.abs(d - 6) < 0.6 || Math.abs(d - 12) < 0.6 ? Blocks.CUT_SANDSTONE.defaultBlockState()
					: d < 2.5 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
				set(x, S, z, floor);
				if (d > r - 0.5) {
					set(x, F, z, Blocks.SANDSTONE_WALL.defaultBlockState());
				}
			}
		}
		// The tower: a sandstone pillar with lanterns, to find the hub from the arenas.
		fill(0, F, 0, 0, F + 5, 0, Blocks.CHISELED_SANDSTONE.defaultBlockState());
		set(0, F + 6, 0, Blocks.SEA_LANTERN.defaultBlockState());
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : around) {
			set(a[0], F, a[1], Blocks.LANTERN.defaultBlockState());
		}
		sign(0, F, 3, 0, "SparBot", "Practice", "Step on a pad", "to visit an arena");
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int ox = sx * 10;
				int oz = sz * 10;
				fill(ox, F, oz, ox, F + 4, oz, Blocks.CUT_SANDSTONE.defaultBlockState());
				set(ox, F + 5, oz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				set(ox, F + 6, oz, Blocks.LANTERN.defaultBlockState());
				palm(sx * 19, F, sz * 17);
			}
		}
		for (int i = 0; i < 40; i++) {
			int x = PracticeLayout.scatter(i, 7, 5) % 61 - 30;
			int z = PracticeLayout.scatter(i, 11, 6) % 61 - 30;
			if (x * x + z * z > (r + 3) * (r + 3)) {
				set(x, F, z, Blocks.DEAD_BUSH.defaultBlockState());
			}
		}
		for (int i = 0; i < PracticeLayout.SITES.size(); i++) {
			Site site = PracticeLayout.SITES.get(i);
			int[] p = PracticeLayout.pad(site);
			set(p[0], S, p[1], PAD_COLORS[i % PAD_COLORS.length]);
			set(p[0], F, p[1], Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
			// The sign just outside the pad, readable from the middle of the plaza.
			int sx = (int) Math.round(p[0] * 1.25);
			int sz = (int) Math.round(p[1] * 1.25);
			sign(sx, F, sz, facing(-sx, -sz), site.displayName(), String.join(", ", site.modes().stream().limit(2).toList()),
				site.modes().size() > 2 ? "and more" : "", "");
		}
	}

	/** Where the hub pad lands you: a little platform south of the arena, looking in, with a pad back to the hub. */
	private void lobby(Site site) {
		int x = site.centerX();
		int z = site.lobbyZ();
		load(x - 3, z - 3, x + 3, z + 3);
		fill(x - 2, S, z - 1, x + 2, S, z + 2, Blocks.POLISHED_ANDESITE.defaultBlockState());
		set(x + 2, F, z + 2, Blocks.LANTERN.defaultBlockState());
		set(x - 2, F, z + 2, Blocks.LANTERN.defaultBlockState());
		set(x, S, z + 2, Blocks.CONCRETE.lightBlue().defaultBlockState());
		set(x, F, z + 2, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
		sign(x + 1, F, z + 2, 8, "Back to", "the hub", "", "");
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

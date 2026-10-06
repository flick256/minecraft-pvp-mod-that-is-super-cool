package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The training hall where the skill drills run: six walled bays either side of a lamplit walkway, the
 * hall's name and how to start a drill over the walkway, and a pad back to the hub. Each bay is put back
 * the way it was before every stage of a drill (blasts, lava, webs and blocks all go).
 */
public final class TrainingHall {
	private static final int S = PracticeLayout.SURFACE;
	private static final int F = PracticeLayout.FLOOR;
	/** How high above the floor a bay's reset clears. */
	private static final int CLEAR_HEIGHT = 30;
	/** How deep under the floor a bay's reset rebuilds (a crystal or anchor blast digs a crater). */
	private static final int DEPTH = 6;

	private TrainingHall() {
	}

	/** Where the hub pad lands you: in the walkway, facing north. */
	public static BlockPos arrival() {
		return new BlockPos(PracticeLayout.HALL_X, F, PracticeLayout.HALL_Z + 3);
	}

	/** The pad back to the hub, just behind the arrival. */
	public static BlockPos returnPad() {
		return new BlockPos(PracticeLayout.HALL_X, F, PracticeLayout.HALL_Z + 6);
	}

	public static void build(ServerLevel level) {
		int hx = PracticeLayout.HALL_X;
		int hz = PracticeLayout.HALL_Z;
		int rx = PracticeLayout.HALL_REACH_X;
		int rz = PracticeLayout.HALL_REACH_Z;
		PracticeLabels.clear(level, new AABB(hx - rx, F - 4, hz - rz, hx + rx + 1, F + 40, hz + rz + 1));
		// The paving: stone bricks with a polished border, and the walkway down the middle.
		for (int x = hx - rx; x <= hx + rx; x++) {
			for (int z = hz - rz; z <= hz + rz; z++) {
				fill(level, x, F, z, x, F + CLEAR_HEIGHT, z, Blocks.AIR.defaultBlockState());
				fill(level, x, S - 3, z, x, S - 1, z, Blocks.STONE.defaultBlockState());
				boolean edge = Math.abs(x - hx) == rx || Math.abs(z - hz) == rz;
				boolean walk = Math.abs(z - hz) <= 6;
				BlockState floor = edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
					: walk ? (Math.abs(z - hz) == 6 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
						: Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState())
					: Blocks.STONE_BRICKS.defaultBlockState();
				set(level, x, S, z, floor);
				if (edge) {
					set(level, x, F, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
				}
			}
		}
		// Lamp posts along the walkway.
		for (int x = hx - rx + 6; x <= hx + rx - 6; x += 10) {
			for (int side = -1; side <= 1; side += 2) {
				int z = hz + side * 5;
				fill(level, x, F, z, x, F + 3, z, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
				set(level, x, F + 4, z, Blocks.LANTERN.defaultBlockState());
			}
		}
		for (int i = 0; i < PracticeLayout.BAYS; i++) {
			resetBay(level, i);
			int[] b = PracticeLayout.bay(i);
			int r = PracticeLayout.BAY_RADIUS + 1;
			int facing = b[1] < hz ? 1 : -1;
			PracticeLabels.put(level, b[0] + 0.5, F + 9, b[1] + facing * (r + 0.5) + 0.5, Component.literal("Bay " + (i + 1))
				.withStyle(st -> st.withColor(0xFFD966).withBold(true)), 2.0F, 0);
		}
		// The pad back to the hub, on an amethyst block.
		BlockPos pad = returnPad();
		set(level, pad.getX(), S, pad.getZ(), Blocks.AMETHYST_BLOCK.defaultBlockState());
		set(level, pad.getX(), F, pad.getZ(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
		PracticeLabels.put(level, pad.getX() + 0.5, F + 1.6, pad.getZ() + 0.5, Component.literal("Back to the hub")
			.withStyle(st -> st.withColor(0x55FFFF).withBold(true)), 0.8F, 0x60000000);
		PracticeLabels.put(level, hx + 0.5, F + 7, hz + 0.5, Component.literal("Training Hall").withStyle(st -> st.withColor(0xFFD966).withBold(true))
			.append(Component.literal("\nevery PvP skill, drilled: pick one in the menu's Drills tab\nor /sparbot drill <name>")
				.withStyle(st -> st.withColor(0xDDDDDD).withBold(false))), 2.0F, 0);
	}

	/**
	 * Puts bay {@code i} back as built: the ground under it, a checked floor with a line across the middle,
	 * glass-topped walls with lanterns on the corner pillars and banners, and nothing above.
	 */
	public static void resetBay(ServerLevel level, int i) {
		int[] b = PracticeLayout.bay(i);
		int r = PracticeLayout.BAY_RADIUS;
		int w = r + 1;
		int towardWalk = b[1] < PracticeLayout.HALL_Z ? 1 : -1;
		for (int x = b[0] - w; x <= b[0] + w; x++) {
			for (int z = b[1] - w; z <= b[1] + w; z++) {
				fill(level, x, S - DEPTH, z, x, S - 1, z, Blocks.STONE.defaultBlockState());
				fill(level, x, F, z, x, F + CLEAR_HEIGHT, z, Blocks.AIR.defaultBlockState());
				int lx = x - b[0];
				int lz = z - b[1];
				boolean wall = Math.abs(lx) == w || Math.abs(lz) == w;
				BlockState floor;
				if (wall) {
					floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
				} else if (lz == 0) {
					floor = Blocks.CALCITE.defaultBlockState();
				} else if (Math.abs(lx) == r || Math.abs(lz) == r) {
					floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				} else {
					floor = Math.floorMod(Math.floorDiv(lx, 2) + Math.floorDiv(lz, 2), 2) == 0 ? Blocks.SMOOTH_STONE.defaultBlockState()
						: Blocks.POLISHED_ANDESITE.defaultBlockState();
				}
				set(level, x, S, z, floor);
				if (!wall) {
					continue;
				}
				boolean corner = Math.abs(lx) == w && Math.abs(lz) == w;
				if (corner) {
					fill(level, x, F, z, x, F + 6, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
					set(level, x, F + 7, z, Blocks.LANTERN.defaultBlockState());
				} else {
					fill(level, x, F, z, x, F + 1, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
					fill(level, x, F + 2, z, x, F + 5, z, Blocks.GLASS.defaultBlockState());
					set(level, x, F + 6, z, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
				}
			}
		}
		// Banners on the wall facing the walkway, inside.
		for (int lx = -r + 3; lx <= r - 3; lx += 6) {
			int z = b[1] + towardWalk * r;
			level.setBlock(new BlockPos(b[0] + lx, F + 1, z), Blocks.WALL_BANNER.lightBlue().defaultBlockState()
				.setValue(WallBannerBlock.FACING, towardWalk > 0 ? Direction.NORTH : Direction.SOUTH), Block.UPDATE_CLIENTS);
		}
	}

	/** Bay {@code i}'s box, for clearing what was left in it. */
	public static AABB bayBox(int i) {
		int[] b = PracticeLayout.bay(i);
		int w = PracticeLayout.BAY_RADIUS + 1;
		return new AABB(b[0] - w, S - DEPTH, b[1] - w, b[0] + w + 1, F + CLEAR_HEIGHT + 10, b[1] + w + 1);
	}

	/** The bay a position is in, or -1. */
	public static int bayAt(double x, double z) {
		for (int i = 0; i < PracticeLayout.BAYS; i++) {
			int[] b = PracticeLayout.bay(i);
			if (Math.abs(x - b[0] - 0.5) <= PracticeLayout.BAY_RADIUS + 1 && Math.abs(z - b[1] - 0.5) <= PracticeLayout.BAY_RADIUS + 1) {
				return i;
			}
		}
		return -1;
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.getBlockState(pos).equals(state)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					set(level, x, y, z, state);
				}
			}
		}
	}
}

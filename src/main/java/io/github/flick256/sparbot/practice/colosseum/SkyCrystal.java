package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AMETHYST;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.END_ROD;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.PURPLE_GLASS;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.SEA_LANTERN;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.put;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The great crystal floating over the Grand Bowl, inside the halo: a hexagonal crystal a hundred blocks from
 * tip to tip and thirty across, its faces purple and magenta glass banded like a gem, amethyst along its
 * edges, a column of light down its heart and beams of end rods from its tips. Six smaller crystals float
 * round it at different heights, and two rings orbit it: a gold one above, a glass one below.
 */
final class SkyCrystal {
	/** The middle of the great crystal. */
	static final int CENTRE = F + 150;
	private static final BlockState MAGENTA_GLASS = Blocks.STAINED_GLASS.magenta().defaultBlockState();
	private static final BlockState PINK_GLASS = Blocks.STAINED_GLASS.pink().defaultBlockState();
	private static final BlockState BLUE_GLASS = Blocks.STAINED_GLASS.lightBlue().defaultBlockState();

	private SkyCrystal() {
	}

	static void build(BlockState[] c, int dx, int dz) {
		crystal(c, dx, dz, CENTRE, 1.0);
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(30 + 60 * i);
			double ox = dx - Math.cos(a) * 34;
			double oz = dz - Math.sin(a) * 34;
			if (Math.abs(ox) < 9 && Math.abs(oz) < 9) {
				crystal(c, ox, oz, CENTRE + (i % 2 == 0 ? -24 : 20), 0.38);
			}
		}
		double d = Math.hypot(dx, dz);
		double deg = Math.toDegrees(Math.atan2(dz, dx));
		// The gold ring above, set with lights; the glass ring below, hung with rods.
		if (Math.abs(d - 24) < 0.7) {
			put(c, CENTRE + 22, CelestialColosseum.offStep(deg, 15, d) < 0.8 ? SEA_LANTERN : Blocks.GOLD_BLOCK.defaultBlockState());
		}
		if (Math.abs(d - 44) < 0.7) {
			boolean light = CelestialColosseum.offStep(deg, 10, d) < 0.8;
			put(c, CENTRE - 24, light ? SEA_LANTERN : BLUE_GLASS);
			if (light) {
				put(c, CENTRE - 25, END_ROD);
				put(c, CENTRE - 26, END_ROD);
			}
		}
	}

	/** The crystal's half-width (to its flat faces) {@code h} blocks above its middle, at full size. */
	private static double radius(double h) {
		if (h < -52 || h > 46) {
			return -1;
		}
		if (h < -12) {
			return 15 * (h + 52) / 40.0;
		}
		if (h <= 14) {
			return 15;
		}
		return 15 * (46 - h) / 32.0;
	}

	/** One crystal centred {@code ox, oz} away from this column, its middle at {@code centre}, scaled by {@code scale}. */
	private static void crystal(BlockState[] c, double ox, double oz, int centre, double scale) {
		double x = Math.abs(ox) / scale;
		double z = Math.abs(oz) / scale;
		// Distance to the hexagon's faces: flat faces north and south, corners east and west.
		double slanted = 0.866 * x + 0.5 * z;
		double hex = Math.max(z, slanted);
		if (hex > 15.5) {
			return;
		}
		boolean ridge = Math.abs(z - slanted) < 0.8 / scale || z < 0.7 / scale && x > 1;
		int lo = (int) Math.floor(centre - 52 * scale);
		int hi = (int) Math.ceil(centre + 46 * scale);
		for (int y = lo; y <= hi; y++) {
			double h = (y - centre) / scale;
			double r = radius(h);
			if (r < 0 || hex > r) {
				continue;
			}
			double rUp = radius(h + 1 / scale);
			double rDown = radius(h - 1 / scale);
			boolean skin = hex > r - 1.2 / scale || hex > rUp - 0.2 || hex > rDown - 0.2;
			if (skin) {
				BlockState glass = Math.floorMod(y / 4, 3) == 0 ? MAGENTA_GLASS : Math.floorMod(y / 4, 3) == 1 ? PURPLE_GLASS : PINK_GLASS;
				put(c, y, ridge || r < 3 ? AMETHYST : glass);
			} else if (hex <= 2.2 && h > -46 && h < 40) {
				// The heart: a column of light seen through the glass.
				put(c, y, SEA_LANTERN);
			}
		}
		if (hex < 0.6 / scale) {
			// Beams from both tips.
			int top = (int) Math.ceil(centre + 46 * scale);
			int bottom = (int) Math.floor(centre - 52 * scale);
			for (int i = 1; i <= (scale >= 1 ? 6 : 2); i++) {
				put(c, top + i, END_ROD);
				put(c, bottom - i, END_ROD);
			}
		}
	}
}

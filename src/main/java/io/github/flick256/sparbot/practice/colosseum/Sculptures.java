package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The colosseum's sculptures, made once into a {@link Voxels} set: eight colossal knights guarding the
 * bridges, and the creatures in the sky (a dragon coiling above the bowl, a phoenix, a sky whale carrying
 * a little island, and glowing jellyfish drifting about). Coordinates are relative to the colosseum's
 * centre; heights are absolute.
 */
final class Sculptures {
	private static final int F = PracticeLayout.FLOOR;
	private static final int S = PracticeLayout.SURFACE;

	private static Voxels built;

	private Sculptures() {
	}

	static synchronized Voxels get() {
		if (built == null) {
			long start = System.currentTimeMillis();
			Voxels v = new Voxels();
			for (int[] g : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
				for (int side = -1; side <= 1; side += 2) {
					// Facing out along the bridge, one each side of it, at the bridge's outer end.
					int along = 198;
					int across = 24 * side;
					knight(v, g[0] * along - g[1] * across, g[1] * along + g[0] * across, g[0], g[1]);
				}
			}
			dragon(v);
			phoenix(v);
			whale(v);
			jellyfish(v);
			built = v;
			io.github.flick256.sparbot.SparBot.LOGGER.info("Sculpted the colosseum's statues and creatures ({} blocks) in {} ms", v.size(),
				System.currentTimeMillis() - start);
		}
		return built;
	}

	// --- The knights ---

	private static final BlockState ARMOUR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	private static final BlockState PLATE = Blocks.DEEPSLATE_TILES.defaultBlockState();
	private static final BlockState DARK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
	private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
	private static final BlockState BLADE = Blocks.CALCITE.defaultBlockState();
	private static final BlockState GLOW = Blocks.SEA_LANTERN.defaultBlockState();
	private static final BlockState CAPE = Blocks.WOOL.purple().defaultBlockState();
	private static final BlockState PEDESTAL = Blocks.CHISELED_DEEPSLATE.defaultBlockState();

	/**
	 * A knight seventy blocks tall standing on a pedestal at (x, z), facing (fx, fz), hands crossed on the
	 * pommel of a greatsword planted in front of it, a purple cape behind, eyes glowing through the visor.
	 */
	private static void knight(Voxels v, int x, int z, int fx, int fz) {
		int rx = fz;
		int rz = -fx;
		int base = F + 6;
		Box b = (x0, y0, z0, x1, y1, z1, s) -> {
			for (int lx = Math.min(x0, x1); lx <= Math.max(x0, x1); lx++) {
				for (int lz = Math.min(z0, z1); lz <= Math.max(z0, z1); lz++) {
					int wx = x + lx * rx + lz * fx;
					int wz = z + lx * rz + lz * fz;
					for (int ly = Math.min(y0, y1); ly <= Math.max(y0, y1); ly++) {
						v.set(wx, base + ly, wz, s);
					}
				}
			}
		};
		// Pedestal, stepped.
		b.box(-13, -7 - (base - S - 7), -11, 13, -3, 13, PEDESTAL);
		b.box(-11, -2, -9, 11, -1, 11, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		b.box(-12, -3, -10, 12, -3, 12, GILDED);
		// Legs and boots.
		b.box(-7, 0, -3, -2, 28, 3, ARMOUR);
		b.box(2, 0, -3, 7, 28, 3, ARMOUR);
		b.box(-8, 0, -4, -1, 3, 5, DARK);
		b.box(1, 0, -4, 8, 3, 5, DARK);
		b.box(-7, 14, -4, -2, 15, 4, PLATE);
		b.box(2, 14, -4, 7, 15, 4, PLATE);
		// Tassets and belt.
		b.box(-9, 26, -5, 9, 31, 5, PLATE);
		b.box(-9, 31, -5, 9, 32, 5, GOLD);
		// Torso and breastplate.
		b.box(-9, 33, -5, 9, 51, 5, PLATE);
		b.box(-8, 35, 5, 8, 50, 6, DARK);
		b.box(-1, 37, 6, 1, 48, 6, GILDED);
		b.box(-5, 42, 6, 5, 43, 6, GILDED);
		// Pauldrons.
		b.box(-15, 45, -6, -9, 53, 6, ARMOUR);
		b.box(9, 45, -6, 15, 53, 6, ARMOUR);
		b.box(-15, 52, -6, -9, 53, 6, GOLD);
		b.box(9, 52, -6, 15, 53, 6, GOLD);
		// Arms, down and forward to the hands on the pommel.
		b.box(-14, 33, -3, -10, 45, 3, ARMOUR);
		b.box(10, 33, -3, 14, 45, 3, ARMOUR);
		b.box(-12, 33, 3, -3, 37, 8, ARMOUR);
		b.box(3, 33, 3, 12, 37, 8, ARMOUR);
		b.box(-3, 34, 6, 3, 38, 10, DARK);
		// The greatsword: pommel, grip, guard, and the blade down to the pedestal, a glowing fuller down it.
		b.box(-1, 39, 7, 1, 41, 9, Blocks.AMETHYST_BLOCK.defaultBlockState());
		b.box(0, 30, 8, 0, 38, 8, DARK);
		b.box(-7, 28, 7, 7, 29, 9, GOLD);
		b.box(-2, -2, 8, 2, 27, 8, BLADE);
		b.box(0, 2, 8, 0, 25, 8, GLOW);
		// Head, helmet, visor with glowing eyes, crest.
		b.box(-5, 53, -5, 5, 64, 5, PLATE);
		b.box(-5, 57, 5, 5, 59, 5, DARK);
		b.box(-3, 58, 5, -2, 58, 5, GLOW);
		b.box(2, 58, 5, 3, 58, 5, GLOW);
		b.box(-1, 65, -6, 1, 70, 5, Blocks.PURPUR_BLOCK.defaultBlockState());
		b.box(-6, 63, -6, 6, 64, 6, ARMOUR);
		// The cape.
		b.box(-10, 6, -7, 10, 52, -6, CAPE);
		b.box(-10, 50, -6, 10, 52, -5, CAPE);
	}

	/** A box in a knight's own frame (x across, y up, z forward). */
	private interface Box {
		void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s);
	}

	// --- The dragon ---

	private static final BlockState SCALE = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState SCALE_SIDE = Blocks.DEEPSLATE_TILES.defaultBlockState();
	private static final BlockState BELLY = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState MEMBRANE = Blocks.STAINED_GLASS.purple().defaultBlockState();
	private static final BlockState BONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

	/** Where the dragon's spine is at t (0 the tail tip, 1 the head), and how thick it is there. */
	private static double[] spine(double t) {
		double a = Math.toRadians(200 + 58 * t);
		double r = 186 - 8 * Math.sin(t * Math.PI);
		double y = F + 162 + 11 * Math.sin(t * Math.PI * 3);
		double thick = t < 0.68 ? 1.0 + 4.6 * t / 0.68 : t < 0.9 ? 5.6 - 2.4 * (t - 0.68) / 0.22 : 3.4;
		return new double[] {Math.cos(a) * r, y, Math.sin(a) * r, thick};
	}

	/**
	 * A dragon coiling above the bowl: a long scaled body (blackstone above, amethyst belly) waving up and
	 * down, spines along its back, horned head with glowing eyes, and two great wings of purple glass on
	 * blackstone bones, one over the stands and one over the moat.
	 */
	private static void dragon(Voxels v) {
		int n = 420;
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			double[] p = spine(t);
			double r = p[3];
			int r0 = (int) Math.ceil(r);
			for (int x = -r0; x <= r0; x++) {
				for (int y = -r0; y <= r0; y++) {
					for (int z = -r0; z <= r0; z++) {
						if (x * x + y * y + z * z > r * r) {
							continue;
						}
						BlockState s = y > 0.35 * r ? SCALE : y < -0.45 * r ? BELLY : SCALE_SIDE;
						v.set((int) Math.round(p[0] + x), (int) Math.round(p[1] + y), (int) Math.round(p[2] + z), s);
					}
				}
			}
			if (i % 9 == 0 && t > 0.05 && t < 0.9) {
				// A spine on its back.
				v.tube(p[0], p[1] + r, p[2], p[0], p[1] + r + 2.5 + r * 0.4, p[2], 0.9, 0.3, Blocks.AMETHYST_BLOCK.defaultBlockState());
			}
		}
		// The head: snout forward along the body, a jaw, horns back, eyes.
		double[] h = spine(1);
		double[] h0 = spine(0.985);
		double fx = h[0] - h0[0];
		double fy = h[1] - h0[1];
		double fz = h[2] - h0[2];
		double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= fl;
		fy /= fl;
		fz /= fl;
		double sx = -fz;
		double sz = fx;
		v.ball(h[0], h[1], h[2], 4.6, SCALE);
		v.tube(h[0], h[1] + 0.5, h[2], h[0] + fx * 11, h[1] + fy * 11 + 0.5, h[2] + fz * 11, 3.3, 1.6, SCALE);
		v.tube(h[0], h[1] - 2.2, h[2], h[0] + fx * 10, h[1] + fy * 10 - 3.4, h[2] + fz * 10, 2.0, 1.0, BELLY);
		for (int side = -1; side <= 1; side += 2) {
			v.tube(h[0] + sx * side * 2.5, h[1] + 3, h[2] + sz * side * 2.5, h[0] - fx * 9 + sx * side * 5, h[1] + 9, h[2] - fz * 9 + sz * side * 5,
				1.3, 0.4, Blocks.BONE_BLOCK.defaultBlockState());
			v.set((int) Math.round(h[0] + fx * 3 + sx * side * 3.6), (int) Math.round(h[1] + 1.8), (int) Math.round(h[2] + fz * 3 + sz * side * 3.6),
				Blocks.SHROOMLIGHT.defaultBlockState());
		}
		// The wings, at the shoulders.
		double[] w = spine(0.64);
		double[] w0 = spine(0.62);
		double tx = w[0] - w0[0];
		double tz = w[2] - w0[2];
		double tl = Math.hypot(tx, tz);
		tx /= tl;
		tz /= tl;
		double nx = tz;
		double nz = -tx;
		for (int side = -1; side <= 1; side += 2) {
			double ox = nx * side;
			double oz = nz * side;
			double[] shoulder = {w[0] + ox * 4, w[1] + 3, w[2] + oz * 4};
			double[] elbow = {w[0] + ox * 26, w[1] + 15, w[2] + oz * 26};
			double[] tip = {w[0] + ox * 52 - tx * 12, w[1] + 6, w[2] + oz * 52 - tz * 12};
			double[] trail1 = {w[0] + ox * 38 - tx * 24, w[1] - 2, w[2] + oz * 38 - tz * 24};
			double[] trail2 = {w[0] + ox * 16 - tx * 26, w[1] - 3, w[2] + oz * 16 - tz * 26};
			double[] root = {w[0] - tx * 12, w[1] + 1, w[2] - tz * 12};
			v.triangle(shoulder, elbow, trail2, MEMBRANE);
			v.triangle(elbow, tip, trail1, MEMBRANE);
			v.triangle(elbow, trail1, trail2, MEMBRANE);
			v.triangle(shoulder, trail2, root, MEMBRANE);
			v.tube(shoulder[0], shoulder[1], shoulder[2], elbow[0], elbow[1], elbow[2], 1.6, 1.1, BONE);
			v.tube(elbow[0], elbow[1], elbow[2], tip[0], tip[1], tip[2], 1.1, 0.4, BONE);
			v.tube(elbow[0], elbow[1], elbow[2], trail1[0], trail1[1], trail1[2], 0.8, 0.4, BONE);
			v.tube(elbow[0], elbow[1], elbow[2], trail2[0], trail2[1], trail2[2], 0.8, 0.4, BONE);
		}
		// A fin at the tail's tip.
		double[] tail = spine(0);
		double[] tail1 = spine(0.03);
		v.triangle(new double[] {tail[0], tail[1], tail[2]}, new double[] {tail1[0], tail1[1] + 6, tail1[2]},
			new double[] {tail1[0], tail1[1] - 6, tail1[2]}, Blocks.AMETHYST_BLOCK.defaultBlockState());
	}

	// --- The phoenix ---

	/**
	 * A phoenix east of the bowl, wings spread wide in red, orange and gold feathers with glowing tips, a
	 * crested head and long trailing tail feathers.
	 */
	private static void phoenix(Voxels v) {
		double cx = 228;
		double cy = F + 178;
		double cz = -36;
		BlockState red = Blocks.CONCRETE.red().defaultBlockState();
		BlockState orange = Blocks.CONCRETE.orange().defaultBlockState();
		BlockState yellow = Blocks.CONCRETE.yellow().defaultBlockState();
		BlockState glow = Blocks.SHROOMLIGHT.defaultBlockState();
		v.ellipsoid(cx, cy, cz, 9, 6, 6, (x, y, z, e) -> e > 0.7 ? (y > 0 ? red : orange) : Blocks.MAGMA_BLOCK.defaultBlockState());
		// Head and beak, towards the bowl (west).
		v.ball(cx - 11, cy + 5, cz, 4.2, red);
		v.tube(cx - 14, cy + 5, cz, cx - 21, cy + 3, cz, 1.6, 0.4, yellow);
		v.set((int) Math.round(cx - 13), (int) Math.round(cy + 6.5), (int) Math.round(cz - 2.5), glow);
		v.set((int) Math.round(cx - 13), (int) Math.round(cy + 6.5), (int) Math.round(cz + 2.5), glow);
		for (int k = -1; k <= 1; k++) {
			v.tube(cx - 10, cy + 8, cz + k * 1.5, cx - 4, cy + 17, cz + k * 4, 1.0, 0.3, orange);
		}
		// Wings: fans of feathers, each a triangle from the shoulder to its own tip, red inside, gold outside.
		for (int side = -1; side <= 1; side += 2) {
			double[] shoulder = {cx - 2, cy + 3, cz + side * 4};
			for (int f = 0; f < 7; f++) {
				double spread = 54 - f * 3.5;
				double sweep = f * 5.0;
				double lift = 16 - f * 3.2;
				BlockState s = f < 2 ? red : f < 4 ? orange : yellow;
				double[] tip = {cx + sweep, cy + lift, cz + side * spread};
				double[] tip2 = {cx + sweep + 6, cy + lift - 3, cz + side * (spread - 6)};
				v.triangle(shoulder, tip, tip2, s);
				v.set((int) Math.round(tip[0]), (int) Math.round(tip[1]), (int) Math.round(tip[2]), glow);
			}
		}
		// Tail feathers trailing east and down.
		for (int k = -2; k <= 2; k++) {
			BlockState s = Math.abs(k) == 2 ? yellow : Math.abs(k) == 1 ? orange : red;
			v.tube(cx + 8, cy - 1, cz + k, cx + 46, cy - 18 + Math.abs(k) * 3, cz + k * 7, 1.8, 0.6, s);
			v.set((int) Math.round(cx + 46), (int) Math.round(cy - 18 + Math.abs(k) * 3), (int) Math.round(cz + k * 7), glow);
		}
	}

	// --- The sky whale ---

	/**
	 * A sky whale west of the bowl: blue above, a grooved pale belly, glowing spots along its flanks, broad
	 * fins and tail flukes, and a little grassy island with a cherry tree riding on its back.
	 */
	private static void whale(Voxels v) {
		double cx = -232;
		double cy = F + 138;
		double cz = 36;
		BlockState top = Blocks.CONCRETE.blue().defaultBlockState();
		BlockState side = Blocks.CONCRETE.lightBlue().defaultBlockState();
		BlockState belly = Blocks.CALCITE.defaultBlockState();
		BlockState groove = Blocks.SMOOTH_STONE.defaultBlockState();
		BlockState spot = Blocks.SEA_LANTERN.defaultBlockState();
		v.ellipsoid(cx, cy, cz, 15, 13, 46, (x, y, z, e) -> {
			if (y < -0.35) {
				return Math.floorMod((int) Math.round(x * 15), 3) == 0 ? groove : belly;
			}
			if (e > 0.8 && y > -0.1 && y < 0.3 && Math.floorMod((int) Math.round(z * 46), 9) == 0) {
				return spot;
			}
			return y > 0.25 ? top : side;
		});
		// Eyes, near the front (north).
		v.set((int) Math.round(cx - 14), (int) Math.round(cy + 1), (int) Math.round(cz - 34), spot);
		v.set((int) Math.round(cx + 14), (int) Math.round(cy + 1), (int) Math.round(cz - 34), spot);
		// Fins.
		for (int s = -1; s <= 1; s += 2) {
			v.triangle(new double[] {cx + s * 12, cy - 4, cz - 18}, new double[] {cx + s * 34, cy - 14, cz - 8},
				new double[] {cx + s * 12, cy - 6, cz - 2}, side);
		}
		// The tail and its flukes (south).
		v.tube(cx, cy + 1, cz + 42, cx, cy + 4, cz + 62, 6, 2.5, side);
		v.triangle(new double[] {cx, cy + 4, cz + 60}, new double[] {cx - 22, cy + 7, cz + 72}, new double[] {cx - 4, cy + 4, cz + 70}, top);
		v.triangle(new double[] {cx, cy + 4, cz + 60}, new double[] {cx + 22, cy + 7, cz + 72}, new double[] {cx + 4, cy + 4, cz + 70}, top);
		// The island on its back.
		for (int x = -6; x <= 6; x++) {
			for (int z = -9; z <= 9; z++) {
				if (x * x / 36.0 + z * z / 81.0 <= 1) {
					v.set((int) cx + x, (int) cy + 13, (int) cz - 4 + z, Blocks.DIRT.defaultBlockState());
					v.set((int) cx + x, (int) cy + 14, (int) cz - 4 + z, Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
		}
		v.box((int) cx, (int) cy + 15, (int) cz - 4, (int) cx, (int) cy + 19, (int) cz - 4, Blocks.CHERRY_LOG.defaultBlockState());
		BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		v.ellipsoid(cx, cy + 21, cz - 4, 4, 2.5, 4, (x, y, z, e) -> leaves);
	}

	// --- Jellyfish ---

	/** Glowing jellyfish drifting round the colosseum at different heights: glass bells, lit cores, chain tentacles. */
	private static void jellyfish(Voxels v) {
		BlockState[] bells = {Blocks.STAINED_GLASS.magenta().defaultBlockState(), Blocks.STAINED_GLASS.purple().defaultBlockState(),
			Blocks.STAINED_GLASS.lightBlue().defaultBlockState(), Blocks.STAINED_GLASS.pink().defaultBlockState()};
		for (int i = 0; i < 18; i++) {
			int roll = PracticeLayout.scatter(i, 5, 91);
			double a = Math.toRadians(i * 20 + roll % 11);
			double d = 120 + roll % 150;
			double x = Math.cos(a) * d;
			double z = Math.sin(a) * d;
			double y = F + 120 + roll % 90;
			double r = 3 + roll % 3;
			BlockState bell = bells[i % bells.length];
			v.ellipsoid(x, y, z, r, r * 0.8, r, (bx, by, bz, e) -> by < 0 ? null : e > 0.55 ? bell : null);
			v.ball(x, y + 1, z, Math.max(1, r * 0.4), i % 2 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState());
			for (int k = 0; k < 5; k++) {
				double ta = Math.toRadians(k * 72);
				int tx = (int) Math.round(x + Math.cos(ta) * r * 0.6);
				int tz = (int) Math.round(z + Math.sin(ta) * r * 0.6);
				int len = 6 + (roll + k * 7) % 9;
				for (int dy = 1; dy <= len; dy++) {
					v.set(tx, (int) Math.round(y) - dy + 1, tz, dy == 1 ? bell : Blocks.IRON_CHAIN.defaultBlockState());
				}
			}
		}
	}
}

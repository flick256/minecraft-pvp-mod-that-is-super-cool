import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Paints every texture SparBot adds (1.2 on), so they can be made again and changed in one place:
 * <ul>
 * <li>Vaelor (128 by 128) and what glows on him, box by box: the boxes here are the ones in VaelorModel, so a box
 * that moves there must move here.</li>
 * <li>The reward items (16 by 16): Oathkeeper, Starfall drawn and pulled, the Unbroken Plate pieces and the Heart of
 * Aster.</li>
 * <li>The Unbroken Plate as worn (64 by 32, the humanoid and leggings layers).</li>
 * </ul>
 * Run from the repository's root: {@code java tools/textures/SparBotTextures.java}. It writes into
 * src/main/resources/assets/sparbot/textures, and a large preview of everything into the folder given as the first
 * argument, if any.
 */
public class SparBotTextures {
	static final Path OUT = Path.of("src/main/resources/assets/sparbot/textures");

	// Star-iron, gold, Vaelor's violet, the cape, the blade.
	static final int STEEL = 0x434A63;
	static final int STEEL_LIGHT = 0x6E7899;
	static final int STEEL_DARK = 0x262A3B;
	static final int GOLD = 0xD8A63C;
	static final int GOLD_LIGHT = 0xF6D77A;
	static final int GOLD_DARK = 0x8A6420;
	static final int VIOLET = 0xB57BFF;
	static final int VIOLET_DARK = 0x6B3FA0;
	static final int CLOTH = 0x4A1E6A;
	static final int CLOTH_LIGHT = 0x6A2E91;
	static final int CLOTH_DARK = 0x2A0F3E;
	static final int BLADE = 0xC4CEE4;
	static final int BLADE_LIGHT = 0xEEF3FF;
	static final int BLADE_DARK = 0x7A86A6;
	static final int BLACK = 0x0A0A12;
	static final int LEATHER = 0x4B2A6E;
	static final int LEATHER_DARK = 0x2E1745;

	public static void main(String[] args) throws IOException {
		Path preview = args.length > 0 ? Path.of(args[0]) : null;
		BufferedImage[] v = vaelor();
		write(v[0], OUT.resolve("entity/vaelor/vaelor.png"));
		write(v[1], OUT.resolve("entity/vaelor/vaelor_glow.png"));
		List<BufferedImage> icons = new ArrayList<>();
		icons.add(save("item/oathkeeper.png", oathkeeper()));
		for (int pull = -1; pull <= 2; pull++) {
			icons.add(save(pull < 0 ? "item/starfall.png" : "item/starfall_pulling_" + pull + ".png", starfall(pull)));
		}
		icons.add(save("item/unbroken_helm.png", sprite(HELM)));
		icons.add(save("item/unbroken_plate.png", sprite(PLATE)));
		icons.add(save("item/unbroken_greaves.png", sprite(GREAVES)));
		icons.add(save("item/unbroken_sabatons.png", sprite(SABATONS)));
		icons.add(save("item/heart_of_aster.png", heart()));
		BufferedImage[] worn = equipment();
		write(worn[0], OUT.resolve("entity/equipment/humanoid/unbroken.png"));
		write(worn[1], OUT.resolve("entity/equipment/humanoid_leggings/unbroken.png"));
		if (preview != null) {
			Files.createDirectories(preview);
			write(scale(v[0], 6), preview.resolve("vaelor.png"));
			write(scale(v[1], 6), preview.resolve("vaelor_glow.png"));
			BufferedImage sheet = new BufferedImage(icons.size() * 18 * 8, 18 * 8, BufferedImage.TYPE_INT_ARGB);
			for (int i = 0; i < icons.size(); i++) {
				paste(sheet, scale(icons.get(i), 8), i * 18 * 8 + 8, 8);
			}
			write(sheet, preview.resolve("items.png"));
			write(scale(worn[0], 10), preview.resolve("worn.png"));
			write(scale(worn[1], 10), preview.resolve("worn_legs.png"));
		}
	}

	// --- Vaelor ---

	enum Mat { STEEL, GOLD, CLOTH, HELM, CHEST, BLADE, GRIP, POMMEL, GUARD, CROWN, SPIKE, LIMB, HIPS, TASSET }

	/** A box of the model: where its texture starts, its size, and what it's made of. */
	record Box(int u, int v, int w, int h, int d, Mat mat) {
	}

	/** The boxes of VaelorModel, in the same order. */
	static final Box[] BOXES = {
		new Box(46, 20, 12, 5, 7, Mat.HIPS),
		new Box(104, 26, 8, 6, 1, Mat.TASSET),
		new Box(104, 34, 8, 6, 1, Mat.TASSET),
		new Box(0, 20, 14, 14, 8, Mat.CHEST),
		new Box(0, 0, 9, 10, 9, Mat.HELM),
		new Box(40, 0, 10, 2, 10, Mat.CROWN),
		new Box(84, 0, 1, 3, 1, Mat.SPIKE),
		new Box(90, 0, 1, 5, 1, Mat.SPIKE),
		new Box(100, 0, 13, 24, 1, Mat.CLOTH),
		new Box(0, 44, 5, 15, 5, Mat.LIMB),
		new Box(22, 44, 5, 15, 5, Mat.LIMB),
		new Box(44, 44, 8, 5, 9, Mat.STEEL),
		new Box(80, 44, 2, 3, 2, Mat.SPIKE),
		new Box(60, 66, 1, 1, 6, Mat.GRIP),
		new Box(76, 66, 2, 2, 2, Mat.POMMEL),
		new Box(86, 66, 9, 2, 2, Mat.GUARD),
		new Box(0, 86, 3, 1, 26, Mat.BLADE),
		new Box(0, 66, 5, 13, 5, Mat.LIMB),
		new Box(22, 66, 5, 13, 5, Mat.LIMB),
		new Box(44, 60, 6, 3, 2, Mat.GOLD),
	};

	enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

	static BufferedImage[] vaelor() {
		BufferedImage tex = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage glow = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Random rnd = new Random(1312);
		for (Box b : BOXES) {
			for (Face f : Face.values()) {
				int x0;
				int y0;
				int fw;
				int fh;
				switch (f) {
					case TOP -> { x0 = b.u + b.d; y0 = b.v; fw = b.w; fh = b.d; }
					case BOTTOM -> { x0 = b.u + b.d + b.w; y0 = b.v; fw = b.w; fh = b.d; }
					case RIGHT -> { x0 = b.u; y0 = b.v + b.d; fw = b.d; fh = b.h; }
					case FRONT -> { x0 = b.u + b.d; y0 = b.v + b.d; fw = b.w; fh = b.h; }
					case LEFT -> { x0 = b.u + b.d + b.w; y0 = b.v + b.d; fw = b.d; fh = b.h; }
					default -> { x0 = b.u + 2 * b.d + b.w; y0 = b.v + b.d; fw = b.w; fh = b.h; }
				}
				for (int y = 0; y < fh; y++) {
					for (int x = 0; x < fw; x++) {
						int[] px = paint(b, f, x, y, fw, fh, rnd);
						tex.setRGB(x0 + x, y0 + y, px[0]);
						if (px[1] != 0) {
							glow.setRGB(x0 + x, y0 + y, px[1]);
						}
					}
				}
			}
		}
		return new BufferedImage[] {tex, glow};
	}

	static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}

	static int shade(int rgb, double k) {
		int r = clamp((int) Math.round((rgb >> 16 & 255) * k));
		int g = clamp((int) Math.round((rgb >> 8 & 255) * k));
		int b = clamp((int) Math.round((rgb & 255) * k));
		return r << 16 | g << 8 | b;
	}

	static int mix(int a, int b, double t) {
		int r = (int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = (int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	static int clamp(int c) {
		return Math.max(0, Math.min(255, c));
	}

	/** Light from the top left: the first row and column of a face catch it, the last ones don't. */
	static int bevel(int base, int light, int dark, int x, int y, int fw, int fh) {
		if (y == 0 || x == 0) {
			return light;
		}
		if (y == fh - 1 || x == fw - 1) {
			return dark;
		}
		return base;
	}

	static int noisy(int rgb, Random rnd, double amount) {
		return shade(rgb, 1 + (rnd.nextDouble() - 0.5) * amount);
	}

	static final int GLOW = 0xFFFFFFFF;
	static final int HALO = 0xFF6A6A6A;

	/** One pixel of a box's face: its colour, and its glow (0 for none). */
	static int[] paint(Box b, Face f, int x, int y, int fw, int fh, Random rnd) {
		int c;
		int g = 0;
		switch (b.mat) {
			case GOLD, CROWN, SPIKE, GUARD, POMMEL -> {
				c = noisy(bevel(GOLD, GOLD_LIGHT, GOLD_DARK, x, y, fw, fh), rnd, 0.08);
				if (b.mat == Mat.CROWN && f == Face.FRONT && fh >= 2 && (x == fw / 2 || x == 2 || x == fw - 3) && y == 0) {
					c = VIOLET;
					g = GLOW;
				}
				if (b.mat == Mat.CROWN && (f == Face.LEFT || f == Face.RIGHT || f == Face.BACK) && x == fw / 2 && y == 0) {
					c = VIOLET;
					g = GLOW;
				}
				if (b.mat == Mat.POMMEL && (f == Face.BACK || f == Face.TOP || f == Face.BOTTOM)) {
					c = x == 0 && y == 0 ? VIOLET : VIOLET_DARK;
					g = x == 0 && y == 0 ? GLOW : HALO;
				}
				if (b.mat == Mat.GUARD && f == Face.FRONT && x == fw / 2) {
					c = VIOLET;
					g = GLOW;
				}
				if (b.mat == Mat.SPIKE && b.h == 5 && f == Face.FRONT && y == 1) {
					c = VIOLET;
					g = GLOW;
				}
			}
			case CLOTH -> {
				double fold = Math.sin(x * 1.3) * 0.12 + Math.sin(x * 0.55 + 1) * 0.08;
				c = noisy(shade(CLOTH, 1 + fold - y * 0.008), rnd, 0.06);
				boolean outer = f == Face.BACK;
				if ((f == Face.BACK || f == Face.FRONT) && (x == 0 || x == fw - 1)) {
					c = noisy(GOLD_DARK, rnd, 0.1);
				}
				if (outer && y == 0) {
					c = GOLD;
				}
				// Gold border near the hem, and a ragged hem (some pixels see through).
				if ((f == Face.BACK || f == Face.FRONT) && y == fh - 4) {
					c = noisy(GOLD_DARK, rnd, 0.1);
				}
				if (y >= fh - 2 && (x * 7 + y * 3) % 5 < (y == fh - 1 ? 3 : 1)) {
					c = -1;
				}
				// A star on the outside of the cape.
				if (outer) {
					double sx = x - (fw - 1) / 2.0;
					double sy = y - 8;
					double r = Math.hypot(sx, sy);
					double a = Math.atan2(sy, sx);
					double star = 2.2 + 2.6 * Math.pow(Math.abs(Math.cos(2 * a)), 6);
					if (r <= star && r > 0) {
						c = r < 1.6 ? GOLD_LIGHT : GOLD;
					}
					if (r < 0.8) {
						c = GOLD_LIGHT;
					}
				}
			}
			case BLADE -> {
				if (f == Face.TOP || f == Face.BOTTOM) {
					// The broad faces: bright edges, a dark fuller down the middle with runes in it.
					int along = y;
					c = x == 1 ? BLADE_DARK : x == 0 ? BLADE_LIGHT : BLADE;
					c = noisy(c, rnd, 0.05);
					if (x == 1 && along % 3 == 1 && along > 2 && along < fh - 3) {
						c = VIOLET;
						g = GLOW;
					}
					if (along <= 1) {
						c = BLADE_LIGHT;
					}
				} else {
					c = noisy(BLADE_LIGHT, rnd, 0.04);
				}
			}
			case GRIP -> {
				c = (x + y) % 2 == 0 ? LEATHER : LEATHER_DARK;
			}
			case HELM -> {
				c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.07);
				if (f == Face.FRONT) {
					int mid = fw / 2;
					if (y == 4) {
						c = BLACK;
						if (x == 2 || x == fw - 3) {
							c = VIOLET;
							g = GLOW;
						} else if (x == 1 || x == 3 || x == fw - 2 || x == fw - 4) {
							g = HALO;
						}
					} else if (y == 5 && (x == 2 || x == fw - 3)) {
						g = HALO;
						c = shade(STEEL_DARK, 0.8);
					} else if (x == mid && y < 4) {
						c = STEEL_LIGHT;
					} else if (y >= 6 && y <= 8 && (x == mid - 2 || x == mid + 2) && y % 2 == 0) {
						c = BLACK;
					} else if (y == fh - 1) {
						c = GOLD_DARK;
					}
				} else if (f == Face.TOP) {
					c = x == fw / 2 ? STEEL_LIGHT : c;
				} else if (y == fh - 1) {
					c = GOLD_DARK;
				}
			}
			case CHEST -> {
				c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.07);
				if (f == Face.FRONT || f == Face.BACK) {
					if (y == 0 || y == 1 && x > 2 && x < fw - 3) {
						c = y == 0 ? GOLD_LIGHT : GOLD;
					}
					if (y == fh - 1) {
						c = GOLD_DARK;
					}
				}
				if (f == Face.FRONT) {
					// The star in his chest, in a gold setting, and the plates' seams.
					double sx = x - (fw - 1) / 2.0;
					double sy = y - 5.5;
					double r = Math.hypot(sx, sy);
					double a = Math.atan2(sy, sx);
					double star = 1.3 + 2.2 * Math.pow(Math.abs(Math.cos(2 * a)), 8);
					if (r <= star + 0.6) {
						c = r <= star ? (r < 1.0 ? 0xF4E8FF : VIOLET) : GOLD;
						if (r <= star) {
							g = r < 1.5 ? GLOW : 0xFFC8C8C8;
						}
					} else if (r <= 4.2 && r > 3.4) {
						c = GOLD_DARK;
					} else if (y == 10 && x > 1 && x < fw - 2) {
						c = STEEL_DARK;
					} else if (x == (fw - 1) / 2 || x == fw / 2) {
						c = y > 9 ? STEEL_LIGHT : c;
					}
				} else if (f == Face.BACK) {
					if (y == 6 || y == 10) {
						c = STEEL_DARK;
					}
				}
			}
			case HIPS -> {
				c = noisy(bevel(LEATHER, shade(LEATHER, 1.3), LEATHER_DARK, x, y, fw, fh), rnd, 0.06);
				if (y == 1 || y == 2) {
					c = y == 1 ? GOLD_LIGHT : GOLD;
					if (f == Face.FRONT && x >= fw / 2 - 1 && x <= fw / 2) {
						c = VIOLET;
					}
				}
			}
			case TASSET -> {
				c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.06);
				if (y == fh - 1 || x == 0 || x == fw - 1) {
					c = GOLD_DARK;
				}
				if (y % 2 == 1 && y < fh - 1) {
					c = shade(c, 0.85);
				}
			}
			case LIMB -> {
				c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.07);
				// Segmented plates: a dark seam every four rows, gold at the cuff.
				if (f != Face.TOP && f != Face.BOTTOM) {
					if (y % 4 == 3) {
						c = STEEL_DARK;
					}
					if (y == fh - 4) {
						c = GOLD;
					}
					if (y > fh - 4) {
						c = noisy(shade(STEEL, 0.85), rnd, 0.06);
					}
				}
			}
			default -> {
				c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.07);
				if (b.mat == Mat.STEEL && f != Face.TOP && f != Face.BOTTOM && y == fh - 1) {
					c = GOLD;
				}
				if (b.mat == Mat.STEEL && f == Face.TOP && (x == 0 || x == fw - 1 || y == 0 || y == fh - 1)) {
					c = GOLD_DARK;
				}
			}
		}
		return new int[] {c == -1 ? 0 : opaque(c), g};
	}

	// --- Items ---

	static BufferedImage save(String path, BufferedImage img) throws IOException {
		write(img, OUT.resolve(path));
		return img;
	}

	/** Oathkeeper, drawn by hand: the blade from the top right, the gold guard across it, the grip and the violet pommel. */
	static final String[] OATHKEEPER = {
		".............oLo",
		"...........oLBDo",
		"..........oLvDo.",
		".........oLBDo..",
		"........oLBDo...",
		".......oLvDo....",
		"..d...oLBDo.....",
		"..Gg.oLBDo......",
		"...GgLvDo.......",
		"....GgD.........",
		"...HhGg.........",
		"..Hh..Gg........",
		".Hh....dd.......",
		"GpG.............",
		"dG..............",
		"................",
	};

	static BufferedImage oathkeeper() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = switch (OATHKEEPER[y].charAt(x)) {
					case 'o' -> 0x1B1630;
					case 'L' -> BLADE_LIGHT;
					case 'B' -> BLADE;
					case 'D' -> BLADE_DARK;
					case 'v' -> VIOLET;
					case 'g' -> GOLD_LIGHT;
					case 'G' -> GOLD;
					case 'd' -> GOLD_DARK;
					case 'h' -> LEATHER;
					case 'H' -> LEATHER_DARK;
					case 'p' -> VIOLET;
					default -> -1;
				};
				if (c != -1) {
					img.setRGB(x, y, opaque(c));
				}
			}
		}
		return img;
	}

	static BufferedImage starfall(int pull) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// Tips at the top left and bottom right, the string between them, the limbs bowed out to the top right.
		double ax = 1.5;
		double ay = 1.5;
		double bx = 14.5;
		double by = 14.5;
		double nx = Math.sqrt(0.5);
		double ny = -Math.sqrt(0.5);
		double back = pull < 0 ? 0 : 1.6 + pull * 1.1;
		for (int i = 0; i <= 200; i++) {
			double s = i / 200.0;
			double off = 5.2 * Math.sin(Math.PI * s);
			double px = ax + (bx - ax) * s + nx * off;
			double py = ay + (by - ay) * s + ny * off;
			int c = Math.abs(s - 0.5) < 0.08 ? GOLD_LIGHT : s < 0.07 || s > 0.93 ? GOLD_LIGHT : 0x8E63C9;
			set(img, px, py, c);
			// The limb is two wide, with a violet thread along it.
			double qx = px - nx * 0.9;
			double qy = py - ny * 0.9;
			if (s > 0.05 && s < 0.95) {
				set(img, qx, qy, Math.abs(s - 0.5) < 0.08 ? GOLD : (i / 25) % 3 == 0 ? VIOLET : 0x4E2F78);
			}
		}
		// The string: straight, or drawn back to the nock.
		double mx = (ax + bx) / 2 - nx * back;
		double my = (ay + by) / 2 - ny * back;
		line(img, ax + 0.6, ay + 0.6, mx, my, 0xDDE6FF);
		line(img, mx, my, bx - 0.6, by - 0.6, 0xDDE6FF);
		if (pull >= 0) {
			// The arrow, from the nock out past the bow.
			double len = 7.5 + back;
			for (double s = 0; s <= len; s += 0.25) {
				double px = mx + nx * s;
				double py = my + ny * s;
				int c = s > len - 1.6 ? BLADE_LIGHT : s < 1.5 ? 0xF2F2F2 : 0xD9C08A;
				set(img, px, py, c);
			}
			set(img, mx + nx * (len + 0.3), my + ny * (len + 0.3), BLADE);
		}
		return img;
	}

	static void set(BufferedImage img, double x, double y, int rgb) {
		int ix = (int) Math.floor(x);
		int iy = (int) Math.floor(y);
		if (ix >= 0 && iy >= 0 && ix < img.getWidth() && iy < img.getHeight()) {
			img.setRGB(ix, iy, opaque(rgb));
		}
	}

	static void line(BufferedImage img, double x0, double y0, double x1, double y1, int rgb) {
		double len = Math.hypot(x1 - x0, y1 - y0);
		for (double s = 0; s <= len; s += 0.25) {
			set(img, x0 + (x1 - x0) * s / len, y0 + (y1 - y0) * s / len, rgb);
		}
	}

	/** Darkens the shape's own edge pixels (where it meets the transparent background), like vanilla's items. */
	static void outline(BufferedImage img, int rgb) {
		int w = img.getWidth();
		int h = img.getHeight();
		boolean[][] edge = new boolean[w][h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if ((img.getRGB(x, y) >>> 24) == 0) {
					continue;
				}
				edge[x][y] = clear(img, x - 1, y) || clear(img, x + 1, y) || clear(img, x, y - 1) || clear(img, x, y + 1);
			}
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (edge[x][y]) {
					int c = img.getRGB(x, y) & 0xFFFFFF;
					img.setRGB(x, y, opaque(mix(c, rgb, 0.45)));
				}
			}
		}
	}

	static boolean clear(BufferedImage img, int x, int y) {
		return x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight() || (img.getRGB(x, y) >>> 24) == 0;
	}

	// The armour icons: left halves, mirrored. s steel, g gold, k black, v violet.
	static final String[] HELM = {
		"........",
		"........",
		"...g..g.",
		"...ggggg",
		"...ssssl",
		"..sssssl",
		"..sssssl",
		"..skkvkk",
		"..sssssl",
		"..sssssl",
		"..sssssl",
		"..ssssss",
		"...sssss",
		"........",
		"........",
		"........",
	};
	static final String[] PLATE = {
		"........",
		"........",
		".gss....",
		".sssg..g",
		".sssss.g",
		".ssssssg",
		"..ssssss",
		"...ssssv",
		"...ssssg",
		"...sssss",
		"...sssss",
		"...ggggg",
		"...sssss",
		"........",
		"........",
		"........",
	};
	static final String[] GREAVES = {
		"........",
		"........",
		"..gggggg",
		"..ssssss",
		"..ssssss",
		"..ssssss",
		"..ssss..",
		"..gsss..",
		"..ssss..",
		"..ssss..",
		"..ssss..",
		"..ssss..",
		"..ssss..",
		"........",
		"........",
		"........",
	};
	static final String[] SABATONS = {
		"........",
		"........",
		"........",
		"........",
		"..ssss..",
		"..ssss..",
		"..gggg..",
		"..ssss..",
		"..ssss..",
		".sssss..",
		".sssss..",
		"........",
		"........",
		"........",
		"........",
		"........",
	};

	static BufferedImage sprite(String[] half) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		char[][] m = new char[16][16];
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 8; x++) {
				char ch = half[y].charAt(x);
				m[y][x] = ch;
				m[y][15 - x] = ch;
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char ch = m[y][x];
				if (ch == '.') {
					continue;
				}
				boolean up = y == 0 || m[y - 1][x] == '.';
				boolean left = x == 0 || m[y][x - 1] == '.';
				boolean down = y == 15 || m[y + 1][x] == '.';
				boolean right = x == 15 || m[y][x + 1] == '.';
				int c = switch (ch) {
					case 'g' -> up || left ? GOLD_LIGHT : down || right ? GOLD_DARK : GOLD;
					case 'k' -> BLACK;
					case 'v' -> VIOLET;
					case 'l' -> STEEL_LIGHT;
					default -> up || left ? STEEL_LIGHT : down || right ? STEEL_DARK : (x + y) % 5 == 0 ? shade(STEEL, 1.12) : STEEL;
				};
				img.setRGB(x, y, opaque(c));
			}
		}
		outline(img, 0x101220);
		return img;
	}

	static BufferedImage heart() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double sx = x - 7.5;
				double sy = y - 7.5;
				double r = Math.hypot(sx, sy);
				double a = Math.atan2(sy, sx);
				double star = 2.6 + 4.6 * Math.pow(Math.abs(Math.cos(2 * a)), 5);
				double ring = Math.abs(r - 4.6);
				int c = -1;
				if (r <= star) {
					double t = r / star;
					c = t < 0.25 ? 0xFFFFFF : t < 0.5 ? 0xEBD4FF : t < 0.78 ? VIOLET : VIOLET_DARK;
					// Light from the top left.
					if (sx + sy < -2 && t > 0.3 && t < 0.8) {
						c = mix(c, 0xFFFFFF, 0.3);
					}
				} else if (ring < 0.55 && Math.abs(Math.sin(2 * a)) > 0.45) {
					c = sx + sy < 0 ? GOLD_LIGHT : GOLD;
				}
				if (c != -1) {
					img.setRGB(x, y, opaque(c));
				}
			}
		}
		outline(img, 0x24133A);
		return img;
	}

	// --- The plate as worn ---

	/** A box of the humanoid armour layout: where its texture starts and its size. */
	record Part(String name, int u, int v, int w, int h, int d) {
	}

	static BufferedImage[] equipment() {
		BufferedImage outer = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage legs = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Random rnd = new Random(77);
		Part[] parts = {new Part("head", 0, 0, 8, 8, 8), new Part("body", 16, 16, 8, 12, 4), new Part("arm", 40, 16, 4, 12, 4),
			new Part("leg", 0, 16, 4, 12, 4)};
		for (Part p : parts) {
			for (Face f : Face.values()) {
				int x0;
				int y0;
				int fw;
				int fh;
				switch (f) {
					case TOP -> { x0 = p.u + p.d; y0 = p.v; fw = p.w; fh = p.d; }
					case BOTTOM -> { x0 = p.u + p.d + p.w; y0 = p.v; fw = p.w; fh = p.d; }
					case RIGHT -> { x0 = p.u; y0 = p.v + p.d; fw = p.d; fh = p.h; }
					case FRONT -> { x0 = p.u + p.d; y0 = p.v + p.d; fw = p.w; fh = p.h; }
					case LEFT -> { x0 = p.u + p.d + p.w; y0 = p.v + p.d; fw = p.d; fh = p.h; }
					default -> { x0 = p.u + 2 * p.d + p.w; y0 = p.v + p.d; fw = p.w; fh = p.h; }
				}
				for (int y = 0; y < fh; y++) {
					for (int x = 0; x < fw; x++) {
						int c = worn(p, f, x, y, fw, fh, rnd);
						boolean side = f != Face.TOP && f != Face.BOTTOM;
						switch (p.name) {
							case "head", "arm" -> outer.setRGB(x0 + x, y0 + y, opaque(c));
							case "body" -> {
								outer.setRGB(x0 + x, y0 + y, opaque(c));
								// The leggings' belt: the bottom of the body.
								if (!side || y >= fh - 4) {
									legs.setRGB(x0 + x, y0 + y, opaque(y == fh - 4 && side ? GOLD : c));
								}
							}
							default -> {
								// Boots on the outer layer (the foot of the leg), greaves on the leggings layer.
								if (!side || y >= fh - 5) {
									outer.setRGB(x0 + x, y0 + y, opaque(side && y == fh - 5 ? GOLD : c));
								}
								if (!side || y < fh - 4) {
									legs.setRGB(x0 + x, y0 + y, opaque(c));
								}
							}
						}
					}
				}
			}
		}
		return new BufferedImage[] {outer, legs};
	}

	static int worn(Part p, Face f, int x, int y, int fw, int fh, Random rnd) {
		int c = noisy(bevel(STEEL, STEEL_LIGHT, STEEL_DARK, x, y, fw, fh), rnd, 0.07);
		boolean side = f != Face.TOP && f != Face.BOTTOM;
		switch (p.name) {
			case "head" -> {
				if (f == Face.TOP && (x == 0 || y == 0 || x == fw - 1 || y == fh - 1)) {
					c = (x + y) % 3 == 0 ? VIOLET : GOLD;
				}
				if (f == Face.FRONT) {
					if (y == 3) {
						c = x == 2 || x == 5 ? VIOLET : BLACK;
					} else if (y == 0) {
						c = GOLD_LIGHT;
					} else if ((x == 3 || x == 4) && y < 3) {
						c = STEEL_LIGHT;
					} else if (y >= 5 && y <= 6 && (x == 2 || x == 5)) {
						c = BLACK;
					}
				} else if (side && y == 0) {
					c = GOLD;
				}
			}
			case "body" -> {
				if (f == Face.FRONT) {
					double sx = x - 3.5;
					double sy = y - 4.0;
					double r = Math.hypot(sx, sy);
					if (r < 1.3) {
						c = r < 0.8 ? 0xF4E8FF : VIOLET;
					} else if (r < 2.2) {
						c = GOLD;
					} else if (y == 0) {
						c = GOLD_LIGHT;
					}
				} else if (side && y == 0) {
					c = GOLD;
				}
				if (side && y == fh - 3) {
					c = STEEL_DARK;
				}
			}
			case "arm" -> {
				if (side && y <= 2) {
					c = y == 2 ? GOLD : noisy(shade(STEEL, 1.15), rnd, 0.05);
				} else if (side && y % 3 == 2) {
					c = STEEL_DARK;
				} else if (f == Face.TOP) {
					c = GOLD_DARK;
				}
				if (side && y >= fh - 2) {
					c = y == fh - 2 ? GOLD : noisy(shade(STEEL, 0.85), rnd, 0.05);
				}
			}
			default -> {
				if (side && y == 4 && f == Face.FRONT) {
					c = GOLD;
				} else if (side && y % 3 == 0 && y < fh - 5) {
					c = STEEL_DARK;
				}
				if (side && y >= fh - 2) {
					c = noisy(shade(STEEL, 0.8), rnd, 0.05);
				}
			}
		}
		return c;
	}

	// --- Files ---

	static void write(BufferedImage img, Path path) throws IOException {
		Files.createDirectories(path.getParent());
		ImageIO.write(img, "png", path.toFile());
	}

	static BufferedImage scale(BufferedImage img, int k) {
		BufferedImage out = new BufferedImage(img.getWidth() * k, img.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				int c = img.getRGB(x / k, y / k);
				if ((c >>> 24) == 0) {
					c = ((x / 4 + y / 4) % 2 == 0) ? 0xFF2B2B2B : 0xFF333333;
				}
				out.setRGB(x, y, c);
			}
		}
		return out;
	}

	static void paste(BufferedImage dst, BufferedImage src, int x0, int y0) {
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				dst.setRGB(x0 + x, y0 + y, src.getRGB(x, y));
			}
		}
	}
}

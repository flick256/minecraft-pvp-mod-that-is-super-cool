package io.github.flick256.sparbot.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * SparBot's look: a forest clearing built from the game's own textures. Dark, mossy dirt behind everything;
 * panels framed in spruce planks with oak leaves along the top and vines hanging from the corners; cards that
 * read like grass blocks (a strip of turf over dark soil); and the game's own bevelled buttons, tinted grass,
 * oak or gold. Text sits on solid, dark surfaces so it stays readable whatever the texture pack.
 */
final class ForestTheme {
	private ForestTheme() {
	}

	// Text and accents: parchment on dark wood, leaf green for what's active, gold for what's special.
	static final int TEXT = 0xFFF4EED8;
	static final int MUTED = 0xFFBCC8A6;
	static final int ACCENT = 0xFF9BDB5C;
	static final int GOLD = 0xFFF2C14E;
	static final int DANGER = 0xFFE8806C;
	static final int WARN = 0xFFFFD277;

	// Surfaces.
	static final int SURFACE = 0xF0161E12;
	static final int SOIL = 0xFF2B2117;
	static final int SOIL_DARK = 0xFF1E1710;
	static final int BARK = 0xFF4A3A26;
	static final int GRASS = 0xFF5D9B3A;
	static final int GRASS_LIGHT = 0xFF83C25A;
	static final int SLOT = 0xFF14100B;

	private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");
	private static final Identifier MOSS = Identifier.withDefaultNamespace("textures/block/moss_block.png");
	private static final Identifier SPRUCE = Identifier.withDefaultNamespace("textures/block/spruce_planks.png");
	private static final Identifier DARK_OAK = Identifier.withDefaultNamespace("textures/block/dark_oak_planks.png");
	private static final Identifier OAK = Identifier.withDefaultNamespace("textures/block/oak_planks.png");
	private static final Identifier LEAVES = Identifier.withDefaultNamespace("textures/block/oak_leaves.png");
	private static final Identifier VINE = Identifier.withDefaultNamespace("textures/block/vine.png");
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");

	/** Leaf and vine tint (the plains foliage colour). */
	private static final int FOLIAGE = 0xFF5FA83A;

	/** Tiles a 16x16 block texture over a rectangle, {@code scale} screen pixels per texel, tinted. */
	static void tile(GuiGraphicsExtractor g, Identifier texture, int x0, int y0, int x1, int y1, int scale, int tint) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		int size = 16 * scale;
		g.enableScissor(x0, y0, x1, y1);
		for (int x = x0; x < x1; x += size) {
			for (int y = y0; y < y1; y += size) {
				g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, 16, 16, 16, 16, tint);
			}
		}
		g.disableScissor();
	}

	/** The whole screen behind a panel: dark dirt with a deep green dusk over it. */
	static void backdrop(GuiGraphicsExtractor g, int width, int height) {
		tile(g, DIRT, 0, 0, width, height, 2, 0xFF404040);
		g.fillGradient(0, 0, width, height, 0xB0122012, 0xD0080C06);
		// Grass along the bottom edge of the screen, like the top of the world.
		g.fill(0, height - 3, width, height, 0x9046762C);
	}

	/**
	 * A panel's frame: spruce planks around the edge, oak leaves along the top and vines hanging from the
	 * top corners. The inside is left to the caller.
	 */
	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 3, y + 5, x + w + 3, y + h + 5, 0x80000000);
		int b = 5;
		tile(g, SPRUCE, x - b, y - b, x + w + b, y, 1, 0xFFB8A58A);
		tile(g, SPRUCE, x - b, y + h, x + w + b, y + h + b, 1, 0xFFA08E76);
		tile(g, SPRUCE, x - b, y, x, y + h, 1, 0xFFA8957C);
		tile(g, SPRUCE, x + w, y, x + w + b, y + h, 1, 0xFFA8957C);
		g.outline(x - b - 1, y - b - 1, w + 2 * b + 2, h + 2 * b + 2, 0xFF120D08);
		g.outline(x - 1, y - 1, w + 2, h + 2, 0xFF2A1F14);
		// Leaves along the top, broken up so it reads as a canopy, not a stripe.
		for (int lx = x - b; lx < x + w + b - 8; lx += 16) {
			int lh = ((lx / 16) % 3 == 0) ? 9 : 7;
			tile(g, LEAVES, lx, y - b - 4, Math.min(lx + 16, x + w + b), y - b - 4 + lh, 1, FOLIAGE);
		}
		if (h > 120) {
			vine(g, x + 8, y, Math.min(40, h / 4));
			vine(g, x + w - 24, y, Math.min(28, h / 6));
		}
	}

	/** A vine hanging from {@code y}, {@code length} pixels long. */
	static void vine(GuiGraphicsExtractor g, int x, int y, int length) {
		tile(g, VINE, x, y, x + 16, y + length, 1, FOLIAGE);
	}

	/** The navigation column: dark oak boards, shaded so the labels stay readable. */
	static void sidebar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		tile(g, DARK_OAK, x0, y0, x1, y1, 2, 0xFF8A7A66);
		g.fillGradient(x0, y0, x1, y1, 0x90140F0A, 0xB00C0906);
		g.verticalLine(x1 - 1, y0, y1 - 1, 0xFF120D08);
	}

	/** The reading surface: near-black moss, with the moss texture only just showing. */
	static void surface(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, SURFACE);
		tile(g, MOSS, x0, y0, x1, y1, 2, 0x18FFFFFF);
	}

	/** A header band: a strip of turf over soil, like the top of a grass block. */
	static void header(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fillGradient(x0, y0, x1, y1, 0xFF22301A, 0xF0161E12);
		g.fill(x0, y1 - 3, x1, y1 - 1, GRASS);
		g.horizontalLine(x0, x1 - 1, y1 - 3, GRASS_LIGHT);
		g.horizontalLine(x0, x1 - 1, y1 - 1, 0xFF2A1F14);
	}

	/** A content card: turf over soil; a featured card has a gold rim. */
	static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean featured, boolean selected) {
		g.fillGradient(x, y, x + w, y + h, featured ? 0xFF3A2E1A : SOIL, featured ? 0xFF261E12 : SOIL_DARK);
		int turf = Math.min(4, Math.max(2, h / 10));
		g.fill(x, y, x + w, y + turf, featured ? 0xFFB08A36 : GRASS);
		g.horizontalLine(x, x + w - 1, y, featured ? GOLD : GRASS_LIGHT);
		// A few dirt specks under the turf, as on a grass block's side.
		for (int sx = x + 5; sx < x + w - 4; sx += 13) {
			int drop = 1 + ((sx * 7) & 3);
			g.fill(sx, y + turf, sx + 2, y + turf + drop, featured ? 0xFF8A6A2A : 0xFF4E7F30);
		}
		g.outline(x, y, w, h, featured ? GOLD : selected ? ACCENT : BARK);
	}

	/** An inset slot, like an inventory cell: for text fields and item cells. */
	static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
		g.fill(x, y, x + w, y + h, SLOT);
		g.horizontalLine(x, x + w - 1, y, 0xFF070503);
		g.verticalLine(x, y, y + h - 1, 0xFF070503);
		g.horizontalLine(x, x + w - 1, y + h - 1, focused ? ACCENT : 0xFF5A4A34);
		g.verticalLine(x + w - 1, y, y + h - 1, focused ? ACCENT : 0xFF5A4A34);
	}

	/** The game's bevelled button, tinted for its role; {@code hover} from 0 to 1. */
	static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, MenuButton.Tone tone, boolean active, boolean selected, float hover) {
		int tint = switch (tone) {
			case PRIMARY -> 0xFF8ED65A;
			case GOLD -> 0xFFF0CC6A;
			case DANGER -> 0xFFE58C78;
			default -> selected ? 0xFFC8E6A8 : 0xFFD9CDB8;
		};
		g.blitSprite(RenderPipelines.GUI_TEXTURED, !active ? BUTTON_OFF : hover > 0.5F || selected ? BUTTON_HOVER : BUTTON, x, y, w, h, tint);
	}

	/** A navigation entry: an oak board; the chosen one is lighter with a leaf-green edge. */
	static void navEntry(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, float hover) {
		int tint = selected ? 0xFFC9B48E : mix(0xFF6E5E48, 0xFF948062, hover);
		tile(g, OAK, x, y, x + w, y + h, 1, tint);
		g.fillGradient(x, y, x + w, y + h, selected ? 0x40000000 : 0x80000000, selected ? 0x60000000 : 0xA0000000);
		g.outline(x, y, w, h, selected ? ACCENT : 0xFF1A130C);
		if (selected) {
			g.fill(x, y + 2, x + 3, y + h - 2, ACCENT);
		}
	}

	static int mix(int a, int b, float t) {
		int alpha = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int blue = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return alpha << 24 | r << 16 | gr << 8 | blue;
	}
}

package io.github.flick256.sparbot.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Small ornaments drawn with native items and pixels; no textures or animation workers to load. */
final class MenuArt {
	private static final Map<Item, ItemStack> ITEMS = new HashMap<>();

	private MenuArt() { }

	static void item(GuiGraphicsExtractor g, Item item, int x, int y) {
		g.fakeItem(ITEMS.computeIfAbsent(item, ItemStack::new), x, y);
	}

	static Item navigation(String name) {
		return switch (name) {
			case "Overview" -> Items.COMPASS;
			case "Practice" -> Items.DIAMOND_SWORD;
			case "Bots" -> Items.PLAYER_HEAD;
			case "Spawn" -> Items.TOTEM_OF_UNDYING;
			case "Kits" -> Items.CHEST;
			case "Drills" -> Items.TARGET;
			case "You" -> Items.WRITABLE_BOOK;
			default -> Items.REDSTONE;
		};
	}

	static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		for (int sx : new int[] {0, 1}) {
			for (int sy : new int[] {0, 1}) {
				int cx = x + (sx == 0 ? 0 : w - 1), cy = y + (sy == 0 ? 0 : h - 1);
				g.horizontalLine(sx == 0 ? cx : cx - 6, sx == 0 ? cx + 6 : cx, cy, color);
				g.verticalLine(cx, sy == 0 ? cy : cy - 6, sy == 0 ? cy + 6 : cy, color);
			}
		}
	}

	static void loadout(GuiGraphicsExtractor g, int right, int y, boolean uhc) {
		Item[] items = uhc ? new Item[] {Items.COBWEB, Items.LAVA_BUCKET, Items.GOLDEN_APPLE}
			: new Item[] {Items.DIAMOND_SWORD, Items.SHIELD, Items.TARGET};
		int border = uhc ? 0xFF725B3B : 0xFF38505D;
		for (int i = 0; i < items.length; i++) {
			int x = right - 82 + i * 28;
			g.fillGradient(x, y, x + 24, y + 26, uhc ? 0xFF3F3529 : 0xFF243B43, 0xFF17212E);
			corners(g, x, y, 24, 26, border);
			item(g, items[i], x + 4, y + 4);
		}
	}
}

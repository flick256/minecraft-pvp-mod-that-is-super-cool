package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.stats.FightStats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Counts consumables the bot really used up (totem pops, golden apples eaten, pearls thrown) by
 * watching its item totals drop between ticks. Moving items around never changes a total, and bots
 * never drop items, so any decrease is consumption.
 */
final class ConsumptionTracker {
	private int totems = -1;
	private int gapples = -1;
	private int pearls = -1;

	void update(BotPlayer player, FightStats stats) {
		int t = count(player, Items.TOTEM_OF_UNDYING);
		int g = count(player, Items.GOLDEN_APPLE) + count(player, Items.ENCHANTED_GOLDEN_APPLE);
		int p = count(player, Items.ENDER_PEARL);
		if (totems >= 0) {
			for (int i = t; i < totems; i++) {
				stats.recordTotemPop();
			}
			for (int i = g; i < gapples; i++) {
				stats.recordGappleEaten();
			}
			for (int i = p; i < pearls; i++) {
				stats.recordPearlThrown();
			}
		}
		totems = t;
		gapples = g;
		pearls = p;
	}

	void reset() {
		totems = -1;
		gapples = -1;
		pearls = -1;
	}

	private static int count(BotPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}

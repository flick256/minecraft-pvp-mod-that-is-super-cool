package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.item.Potions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Restocks the hotbar from the main inventory between engagements, like a player opening the
 * inventory and pressing number keys over each stack: healing potions into empty hotbar slots, plus
 * one stack of pearls and golden apples when the hotbar has none, and a water bucket. Takes the profile's inventory time,
 * the bot cannot move or fight while the screen is open, and it gives up if the opponent closes in.
 */
public final class RefillTactic implements Tactic {
	private static final int MAX_POT_SLOTS = 4;
	private static final double SAFE_DISTANCE = 6.0;
	private static final double ABORT_DISTANCE = 3.5;

	private enum Phase {
		IDLE,
		OPEN,
		CLICKING
	}

	private Phase phase = Phase.IDLE;
	private int ticksLeft;
	private final List<InventoryClick> plan = new ArrayList<>();

	@Override
	public String name() {
		return "refill";
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return c.targetDistance() < ABORT_DISTANCE ? 0 : Scores.SURVIVAL + 0.15;
		}
		if (c.targetDistance() < SAFE_DISTANCE || c.self.inventory().usingItem()) {
			return 0;
		}
		return planFor(c.self.inventory()).isEmpty() ? 0 : Scores.SPECIALIST + 0.06;
	}

	@Override
	public void onEnter(BrainContext c) {
		plan.clear();
		plan.addAll(planFor(c.self.inventory()));
		phase = Phase.OPEN;
		ticksLeft = Math.max(InputShaper.MIN_TICKS_OPEN_BEFORE_CLICK + 1, (int) Math.round(c.profile.items().inventoryMs().sample(c.rng) / 50.0));
	}

	@Override
	public void onExit(BrainContext c) {
		phase = Phase.IDLE;
		plan.clear();
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		plan.clear();
	}

	@Override
	public Inputs act(BrainContext c) {
		if (phase == Phase.IDLE) {
			// Finished one go and chosen again straight away (onEnter only runs on a change of tactic).
			onEnter(c);
		}
		switch (phase) {
			case OPEN -> {
				if (--ticksLeft > 0) {
					return Inputs.inInventory(null);
				}
				phase = Phase.CLICKING;
				ticksLeft = 0;
				return Inputs.inInventory(null);
			}
			case CLICKING -> {
				if (plan.isEmpty()) {
					phase = Phase.IDLE;
					return Inputs.IDLE; // close the screen
				}
				if (--ticksLeft > 0) {
					return Inputs.inInventory(null);
				}
				// Moving the mouse to the next stack takes a moment; never faster than the click limit.
				ticksLeft = InputShaper.MIN_TICKS_BETWEEN_INVENTORY_ACTIONS + (c.profile.items().inventoryMs().mean() > 500 ? 2 : 0);
				return Inputs.inInventory(plan.remove(0));
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	/** Which main-inventory stacks to move into which empty hotbar slots. */
	static List<InventoryClick> planFor(InventoryState inv) {
		List<InventoryClick> clicks = new ArrayList<>();
		List<Integer> freeHotbar = new ArrayList<>();
		for (int i = 0; i < InventoryState.HOTBAR_SIZE; i++) {
			if (inv.slot(i).isEmpty()) {
				freeHotbar.add(i);
			}
		}
		List<Integer> used = new ArrayList<>();
		addIfMissing(inv, clicks, freeHotbar, used, i -> i.is(ItemKind.ENDER_PEARL), 1);
		addIfMissing(inv, clicks, freeHotbar, used, i -> i.is(ItemKind.GOLDEN_APPLE) || i.is(ItemKind.ENCHANTED_GOLDEN_APPLE), 1);
		// TNT minecarts don't stack, so the hotbar runs out of them one by one.
		addIfMissing(inv, clicks, freeHotbar, used, i -> i.is(ItemKind.TNT_MINECART), 1);
		int potsInHotbar = 0;
		for (int i = 0; i < InventoryState.HOTBAR_SIZE; i++) {
			if (Potions.isHealingSplash(inv.slot(i))) {
				potsInHotbar++;
			}
		}
		for (int i = InventoryState.HOTBAR_SIZE; i < InventoryState.SIZE && potsInHotbar < MAX_POT_SLOTS && !freeHotbar.isEmpty(); i++) {
			if (Potions.isHealingSplash(inv.slot(i)) && !used.contains(i)) {
				clicks.add(new InventoryClick(i, freeHotbar.remove(0)));
				used.add(i);
				potsInHotbar++;
			}
		}
		// UHC: a water bucket belongs in the hotbar (a web or fire with none to hand is deadly). The spare
		// goes where the empty bucket is, or into a free slot.
		boolean waterRefill = false;
		if (inv.hotbarSlot(ItemKind.WATER_BUCKET) < 0) {
			int from = inv.mainInventorySlot(i -> i.is(ItemKind.WATER_BUCKET));
			int to = inv.hotbarSlot(ItemKind.BUCKET);
			if (to < 0 && !freeHotbar.isEmpty()) {
				to = freeHotbar.remove(0);
			}
			if (from >= 0 && to >= 0 && !used.contains(from)) {
				clicks.add(new InventoryClick(from, to));
				used.add(from);
				waterRefill = true;
			}
		}
		// Only worth opening the inventory when the hotbar has run dry of something important.
		boolean hotbarOutOfPots = inv.hotbarSlot(Potions::isHealingSplash) < 0;
		boolean worthIt = hotbarOutOfPots || waterRefill || clicks.size() >= 2;
		return worthIt ? clicks : List.of();
	}

	private static void addIfMissing(InventoryState inv, List<InventoryClick> clicks, List<Integer> freeHotbar, List<Integer> used,
		Predicate<ItemInfo> kind, int stacks) {
		if (inv.hotbarSlot(kind) >= 0 || freeHotbar.isEmpty()) {
			return;
		}
		int from = inv.mainInventorySlot(kind);
		if (from >= 0 && stacks > 0) {
			clicks.add(new InventoryClick(from, freeHotbar.remove(0)));
			used.add(from);
		}
	}
}

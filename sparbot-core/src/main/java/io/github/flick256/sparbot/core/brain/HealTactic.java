package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.profile.SkillProfile;

/**
 * Eating: golden apples when health is low (preferably with some distance to the opponent), and
 * ordinary food when hungry and safe. Food is finite and eating takes vanilla's full use time
 * (32 ticks for golden apples), during which the bot moves at item-use speed and cannot attack.
 */
public final class HealTactic implements Tactic {
	/** Never start eating with the opponent closer than this unless desperate. */
	private static final double SAFE_EAT_DISTANCE = 3.5;
	private static final String REGENERATION = "minecraft:regeneration";
	private static final String ABSORPTION = "minecraft:absorption";
	/** Just walled off: eat behind the wall within this many ticks, at any distance. */
	private static final int BEHIND_WALL_TICKS = 60;

	private boolean started;
	private int startedCount;
	private int slot = -1;

	@Override
	public String name() {
		return "heal";
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		SkillProfile.ItemSkills items = c.profile.items();
		if (started && inv.usingItem() && inv.usingKind().isFood()) {
			return 0.95; // finish what we started
		}
		double distance = c.targetDistance();
		boolean safe = c.target == null || distance > 10;
		boolean healthLow = c.self.healthFraction() < items.gappleHealthFraction();
		boolean desperate = c.self.healthFraction() < items.gappleHealthFraction() * 0.5;
		// Already absorbing/regenerating from a previous apple: eating another now is wasteful.
		boolean buffed = c.self.hasEffect(REGENERATION) && c.self.absorption() > 0;
		if (healthLow && !buffed && gappleSlot(inv) >= 0 && (distance > SAFE_EAT_DISTANCE || desperate)) {
			return desperate ? 0.9 : 0.85;
		}
		// The wall was put up to eat behind: eat now, before the opponent gets round it.
		boolean walled = c.observation.tick() - c.memory.walledAt < BEHIND_WALL_TICKS;
		if (walled && !buffed && gappleSlot(inv) >= 0 && c.self.healthFraction() < items.gappleHealthFraction() + 0.25) {
			return 0.9;
		}
		if (c.self.foodLevel() < items.eatHungerBelow() && foodSlot(inv) >= 0 && safe) {
			return 0.55;
		}
		return 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		started = false;
		InventoryState inv = c.self.inventory();
		boolean walled = c.observation.tick() - c.memory.walledAt < BEHIND_WALL_TICKS;
		boolean wantsGapple = c.self.healthFraction() < c.profile.items().gappleHealthFraction() + (walled ? 0.25 : 0);
		slot = wantsGapple ? gappleSlot(inv) : -1;
		if (slot < 0) {
			slot = foodSlot(inv);
		}
	}

	@Override
	public void onExit(BrainContext c) {
		started = false;
	}

	@Override
	public void reset() {
		started = false;
		slot = -1;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (slot < 0 || inv.slot(slot).isEmpty() || !inv.slot(slot).kind().isFood()) {
			onEnter(c);
			if (slot < 0) {
				return Inputs.IDLE;
			}
		}
		int press = c.memory.hands.request(c, slot);
		boolean inHand = inv.selectedSlot() == slot;
		if (inHand && !started) {
			started = true;
			startedCount = inv.slot(slot).count();
		}
		if (started && (!inHand || inv.slot(slot).count() < startedCount)) {
			// Finished (count dropped) or the item left our hand: done eating this one.
			started = false;
		}

		// Back away from the opponent while eating (at item-use speed) and keep an eye on them.
		float yawDelta = 0;
		float pitchDelta = 0;
		int forward = 0;
		if (c.seen() != null) {
			float goalYaw = Angles.yawTowards(c.self.eyePosition(), c.seen().chest());
			float[] look = c.aim.step(c.self.yaw(), c.self.pitch(), goalYaw, Angles.pitchTowards(c.self.eyePosition(), c.seen().chest()));
			yawDelta = look[0];
			pitchDelta = look[1];
			forward = c.targetDistance() < 6 ? -1 : 0;
		}
		Inputs inputs = new Inputs(yawDelta, pitchDelta, forward, c.memory.strafeDirection, false, false, false, false, inHand, press);
		return Movement.guardEdges(c, inputs);
	}

	static int gappleSlot(InventoryState inv) {
		int slot = inv.hotbarSlot(ItemKind.ENCHANTED_GOLDEN_APPLE);
		return slot >= 0 ? slot : inv.hotbarSlot(ItemKind.GOLDEN_APPLE);
	}

	static int foodSlot(InventoryState inv) {
		int slot = inv.hotbarSlot(ItemKind.FOOD);
		return slot >= 0 ? slot : gappleSlot(inv);
	}

	static boolean isFood(ItemInfo item) {
		return item.kind().isFood();
	}
}

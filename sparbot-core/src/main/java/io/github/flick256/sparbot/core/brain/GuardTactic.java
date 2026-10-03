package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Raise the offhand shield while an archer is drawing on us, and walk in behind it. A 26.2 shield
 * starts blocking 5 ticks (0.25 s) after it is raised and blocks hits within 90 degrees of facing.
 */
public final class GuardTactic implements Tactic {
	private final Decision willGuard = new Decision();

	@Override
	public String name() {
		return "guard";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		InventoryState inv = c.self.inventory();
		if (c.target == null || t == null || !t.visible() || !inv.offhand().is(ItemKind.SHIELD) || inv.offhand().onCooldown()) {
			return 0;
		}
		boolean aimingAtUs = t.usingKind() == ItemKind.BOW || t.usingKind() == ItemKind.CROSSBOW || t.mainHand() == ItemKind.CROSSBOW;
		if (!aimingAtUs || c.targetDistance() < 4.0) {
			return 0;
		}
		return willGuard.get(c.rng, c.profile.items().shieldSkill(), 40) ? Scores.SPECIALIST + 0.04 : 0;
	}

	@Override
	public void reset() {
		willGuard.reset();
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		// The main hand must hold something that is not itself usable (a weapon), or right click would
		// use it instead of the shield.
		int weapon = Hands.preferredMeleeSlot(inv);
		int press = c.memory.hands.request(c, weapon);
		boolean ready = weapon < 0 || inv.selectedSlot() == weapon;
		TargetState t = c.seen();
		float[] look = c.lookAt(Angles.yawTowards(c.self.eyePosition(), t.chest()), Angles.pitchTowards(c.self.eyePosition(), t.chest()));
		Inputs inputs = new Inputs(look[0], look[1], 1, 0, false, false, false, false, ready, press);
		return Movement.guardEdges(c, inputs);
	}
}

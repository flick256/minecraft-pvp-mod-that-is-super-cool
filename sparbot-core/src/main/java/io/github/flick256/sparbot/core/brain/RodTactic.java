package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Ballistics;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.OptionalDouble;

/**
 * Fishing rod against a fleeing opponent. In 26.2 a bobber that hits a player does no damage or
 * knockback; it hooks them (FishingHook#onHitEntity) and reeling in pulls them towards the angler
 * (FishingHook#pullEntity). So the bot casts at an opponent running away, then reels them back in.
 */
public final class RodTactic implements Tactic {
	private static final double MIN_RANGE = 3.5;
	private static final double MAX_RANGE = 9.0;
	/** Opponent moving away faster than this (blocks/tick) counts as fleeing. */
	private static final double FLEEING_SPEED = 0.08;
	private static final int GIVE_UP_TICKS = 25;

	private final Decision willRod = new Decision();
	private int hookTicks;
	private int hookedTicks;
	private boolean useHeld;

	@Override
	public String name() {
		return "rod";
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (inv.fishingHookOut() && Hands.holds(inv, ItemKind.FISHING_ROD)) {
			return 0.9; // finish: reel in or retract
		}
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || inv.hotbarSlot(ItemKind.FISHING_ROD) < 0) {
			return 0;
		}
		double distance = c.targetDistance();
		if (distance < MIN_RANGE || distance > MAX_RANGE) {
			return 0;
		}
		Vec3 away = t.position().subtract(c.self.position());
		double len = away.horizontalLength();
		double speedAway = len < 1e-6 ? 0 : (t.velocity().x() * away.x() + t.velocity().z() * away.z()) / len;
		if (speedAway < FLEEING_SPEED) {
			return 0;
		}
		return willRod.get(c.rng, c.profile.items().rodSkill(), 40) ? Scores.SPECIALIST : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		hookTicks = 0;
		hookedTicks = 0;
	}

	@Override
	public void reset() {
		willRod.reset();
		hookTicks = 0;
		hookedTicks = 0;
		useHeld = false;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		int slot = inv.hotbarSlot(ItemKind.FISHING_ROD);
		int press = c.memory.hands.request(c, slot);
		boolean inHand = slot >= 0 && inv.selectedSlot() == slot;
		TargetState t = c.seen();
		float[] goal = t == null ? new float[] {c.self.yaw(), c.self.pitch()} : aim(c, t);
		float[] look = c.lookAt(goal[0], goal[1]);

		boolean click = false;
		if (inHand) {
			if (!inv.fishingHookOut()) {
				click = c.aimError(goal[0], goal[1]) < 3.0F;
				hookTicks = 0;
			} else if (inv.fishingHookOnTarget()) {
				// Give the hook a moment to settle, then reel in: vanilla pulls the hooked entity towards us.
				click = ++hookedTicks >= 2;
			} else {
				click = ++hookTicks > GIVE_UP_TICKS;
			}
		}
		// Right click is an edge: release between presses so each press is a new use.
		boolean use = click && !useHeld;
		useHeld = use;
		if (use && inv.fishingHookOut()) {
			willRod.reset(); // reeled in or retracted: decide afresh next time
		}
		return new Inputs(look[0], look[1], 1, 0, false, false, true, false, use, press);
	}

	private static float[] aim(BrainContext c, TargetState t) {
		Vec3 eye = c.self.eyePosition();
		Vec3 point = t.chest().add(t.velocity().scale(6 * c.profile.aim().trackingLead()));
		OptionalDouble pitch = Ballistics.solvePitch(Ballistics.HOOK, point.horizontalDistanceTo(eye), point.y() - eye.y());
		return new float[] {Angles.yawTowards(eye, point), pitch.isPresent() ? (float) pitch.getAsDouble() : -10.0F};
	}
}

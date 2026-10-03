package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * For ranged playstyles: when the opponent gets inside the preferred range, turn and sprint away to
 * reopen the gap for the bow. If the opponent is already on top of the bot, kiting has failed and it
 * fights (score 0).
 */
public final class KiteTactic implements Tactic {
	/** Start backing off below this fraction of the preferred range. */
	private static final double BACK_OFF_FRACTION = 0.75;
	private static final double CAUGHT_DISTANCE = 2.5;

	@Override
	public String name() {
		return "kite";
	}

	@Override
	public double score(BrainContext c) {
		double preferred = c.style.preferredRange();
		TargetState t = c.seen();
		if (preferred <= 0 || c.target == null || t == null || !t.visible() || RangedTactic.weaponSlot(c.self.inventory()) < 0) {
			return 0;
		}
		double distance = c.targetDistance();
		if (distance < CAUGHT_DISTANCE || distance >= preferred * BACK_OFF_FRACTION) {
			return 0;
		}
		return Scores.SPECIALIST + 0.03;
	}

	@Override
	public Inputs act(BrainContext c) {
		float away = Angles.wrapDegrees(Angles.yawTowards(c.self.eyePosition(), c.seen().position()) + 180.0F);
		// Zig-zag slightly so the chaser cannot line up free hits.
		float wobble = (float) Math.sin(c.observation.tick() / 5.0) * 20.0F;
		float[] look = c.lookAt(away + wobble, 0.0F);
		boolean jump = c.self.onGround() && c.self.horizontalCollision();
		return Movement.guardEdges(c, new Inputs(look[0], look[1], 1, 0, jump, false, true, false, false, -1));
	}
}

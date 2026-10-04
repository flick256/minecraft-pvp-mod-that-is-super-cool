package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.sense.SelfState;

/**
 * What any player does when their feet stop getting them anywhere: in water, hold jump to swim up and
 * climb out over the edge; pressing to move but not moving (a block, a hole's rim, a leftover water
 * pocket), jump, then step sideways. Applied on top of every tactic.
 */
final class Unstuck {
	/** Not moving for this many ticks while pressing to move counts as stuck. */
	private static final int STUCK_TICKS = 6;
	/** Still stuck after jumping this long: step sideways too. */
	private static final int SIDESTEP_AFTER = 14;
	private static final int GIVE_UP_DIRECTION = 40;
	private static final double MOVING = 0.02;

	private Unstuck() {
	}

	static Inputs apply(BrainContext c, Inputs in) {
		SelfState self = c.self;
		DuelMemory m = c.memory;
		boolean pressing = in.forward() != 0 || in.strafe() != 0;
		if (in.inventoryOpen() || self.inWeb() || !pressing) {
			m.stuckTicks = 0;
			return in;
		}
		if (self.inWater()) {
			// Swimming: jump keeps the head up and lifts the bot out over a one-block edge.
			in = in.withJump(true);
		}
		double moved = Math.hypot(m.selfMotion.x(), m.selfMotion.z());
		boolean slowedByItem = self.inventory().usingItem();
		// Stuck means pushing against something (a block, a rim, the edge of a water pocket).
		if (moved > MOVING || slowedByItem || !self.horizontalCollision() && !self.inWater()) {
			m.stuckTicks = 0;
			return in;
		}
		m.stuckTicks++;
		if (m.stuckTicks > STUCK_TICKS && (self.onGround() || self.inWater())) {
			in = in.withJump(true);
		}
		if (m.stuckTicks > SIDESTEP_AFTER) {
			if (m.stuckTicks > GIVE_UP_DIRECTION) {
				m.detourStrafe = -m.detourStrafe;
				m.stuckTicks = SIDESTEP_AFTER + 1;
			}
			in = Movement.guardEdges(c, in.withMovement(in.forward(), m.detourStrafe));
		}
		return in;
	}
}

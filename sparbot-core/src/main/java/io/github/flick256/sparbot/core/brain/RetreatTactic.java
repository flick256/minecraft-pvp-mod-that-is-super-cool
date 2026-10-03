package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;

/**
 * Disengage when health drops below the profile's panic threshold: turn away and sprint off, fighting
 * back only when cornered. There is no healing here; the bot only gets vanilla natural regeneration,
 * which needs a full enough hunger bar, like any player.
 */
public final class RetreatTactic implements Tactic {
	/** After this long running away the bot commits back to the fight. */
	private static final int MAX_RETREAT_TICKS = 160;
	/** After giving up on a retreat, the bot fights for at least this long before panicking again. */
	private static final int RETREAT_COOLDOWN_TICKS = 200;

	@Override
	public String name() {
		return "retreat";
	}

	@Override
	public double score(BrainContext c) {
		if (c.target == null) {
			return 0;
		}
		if (c.memory.retreatCooldown > 0) {
			return 0;
		}
		double panic = c.profile.panicHealthFraction();
		// Worn-out armor makes every hit hurt more: disengage earlier.
		if (averageArmorDurability(c) < 0.25) {
			panic += 0.1;
		}
		boolean panicking = c.self.healthFraction() < panic;
		boolean recovered = c.self.healthFraction() > panic + 0.15;
		if (panicking) {
			return 0.8;
		}
		// Hysteresis: once retreating, keep going until health has recovered somewhat.
		return c.memory.retreatTicks > 0 && !recovered ? 0.7 : 0;
	}

	private static double averageArmorDurability(BrainContext c) {
		double total = 0;
		int pieces = 0;
		for (var piece : c.self.inventory().armor()) {
			if (!piece.isEmpty()) {
				total += piece.durability();
				pieces++;
			}
		}
		return pieces == 0 ? 1.0 : total / pieces;
	}

	@Override
	public void onExit(BrainContext c) {
		c.memory.retreatTicks = 0;
	}

	@Override
	public Inputs act(BrainContext c) {
		c.memory.retreatTicks++;
		boolean cornered = c.targetDistance() < 1.5;
		if (cornered || c.memory.retreatTicks > MAX_RETREAT_TICKS) {
			// Running has failed: commit back to the fight for a while.
			c.memory.retreatCooldown = RETREAT_COOLDOWN_TICKS;
		}
		float away = Angles.wrapDegrees(Angles.yawTowards(c.self.eyePosition(), c.seen().position()) + 180.0F);
		// Run in a slight zig-zag so the chaser cannot line up easy hits.
		float wobble = (float) Math.sin(c.memory.retreatTicks / 6.0) * 25.0F;
		float[] look = c.aim.step(c.self.yaw(), c.self.pitch(), away + wobble, 0.0F);
		boolean jump = c.self.onGround() && c.self.horizontalCollision();
		Inputs inputs = new Inputs(look[0], look[1], 1, 0, jump, false, true, false, false, -1);
		return Movement.guardEdges(c, inputs);
	}
}

package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * The block boost (UHC and SMP): a few blocks from the opponent, put a block down ahead, sprint-jump onto
 * it and launch off it towards them. The higher launch means more time in the air at sprint speed, so
 * the bot covers the gap faster and arrives from above, falling into its hit. Skill decides how often.
 */
public final class BoostTactic implements Tactic {
	private static final double MIN_RANGE = 5.5;
	private static final double MAX_RANGE = 10.0;
	/** The block goes this far ahead (blocks), towards the opponent. */
	private static final double AHEAD = 1.8;
	private static final int TIMEOUT = 40;
	private static final int COOLDOWN = 160;

	private enum Phase {
		IDLE,
		PLACE,
		STEP,
		LAUNCH
	}

	private final Decision willBoost = new Decision();
	private Phase phase = Phase.IDLE;
	private BlockSpot step;
	private int ticks;
	private int blocksBefore;
	private long last = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "boost";
	}

	@Override
	public String detail() {
		return phase == Phase.IDLE ? "" : phase.name().toLowerCase();
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return Scores.SPECIALIST + 0.05;
		}
		SelfState self = c.self;
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || !self.onGround() || self.inWater() || slot(self.inventory()) < 0
			|| c.observation.tick() - last < COOLDOWN) {
			return 0;
		}
		double d = c.targetDistance();
		if (d < MIN_RANGE || d > MAX_RANGE || Math.abs(t.position().y() - self.position().y()) > 1.5) {
			return 0;
		}
		return willBoost.get(c.rng, c.profile.items().uhcSkill(), 20) ? Scores.SPECIALIST - 0.02 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		if (phase == Phase.IDLE) {
			phase = Phase.PLACE;
			ticks = 0;
			blocksBefore = count(c.self.inventory());
			Vec3 here = c.self.position();
			Vec3 to = c.seen().position().subtract(here);
			double len = Math.sqrt(to.x() * to.x() + to.z() * to.z());
			BlockSpot ground = BlockPlay.groundUnder(here);
			step = new BlockSpot((int) Math.floor(here.x() + to.x() / len * AHEAD), ground.y() + 1, (int) Math.floor(here.z() + to.z() / len * AHEAD));
		}
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		willBoost.reset();
		last = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		if (phase == Phase.IDLE) {
			onEnter(c);
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		if (++ticks > TIMEOUT || c.seen() == null) {
			return finish(c);
		}
		Vec3 eye = self.eyePosition();
		Vec3 top = new Vec3(step.x() + 0.5, step.y() + 1, step.z() + 0.5);
		switch (phase) {
			case PLACE -> {
				if (count(inv) < blocksBefore) {
					phase = Phase.STEP;
				} else if (slot(inv) < 0) {
					return finish(c);
				} else {
					// Click the top of the ground where the step goes, walking up to it.
					Inputs click = BlockPlay.clickTop(c, new BlockSpot(step.x(), step.y() - 1, step.z()), 1.0, slot(inv));
					return click.withMovement(0, 0).withSprint(false);
				}
				return walk(c, top, false);
			}
			case STEP -> {
				if (self.onGround() && self.position().y() >= step.y() + 1 - 0.05) {
					phase = Phase.LAUNCH;
					return launch(c);
				}
				// Sprint at it and jump onto it.
				boolean close = Math.hypot(top.x() - self.position().x(), top.z() - self.position().z()) < 1.3;
				return walk(c, top, close && self.onGround());
			}
			case LAUNCH -> {
				if (!self.onGround()) {
					return finish(c); // in the air, heading for them: melee takes it from here
				}
				return launch(c);
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	/** Sprint-jump off the step, straight at the opponent. */
	private Inputs launch(BrainContext c) {
		return walk(c, c.seen().position(), true);
	}

	private Inputs walk(BrainContext c, Vec3 goal, boolean jump) {
		Vec3 eye = c.self.eyePosition();
		Vec3 chest = c.seen().chest();
		// Look where it is going until the launch, then at the opponent.
		Vec3 lookAt = phase == Phase.LAUNCH ? chest : new Vec3(goal.x(), eye.y() - 0.2, goal.z());
		float[] look = c.lookAt(Angles.yawTowards(eye, lookAt), Angles.pitchTowards(eye, lookAt));
		int press = c.memory.hands.request(c, Hands.preferredMeleeSlot(c.self.inventory()));
		return Movement.guardEdges(c, new Inputs(look[0], look[1], 1, 0, jump, false, true, false, false, phase == Phase.STEP ? -1 : press));
	}

	private Inputs finish(BrainContext c) {
		phase = Phase.IDLE;
		willBoost.reset();
		last = c.observation.tick();
		return Inputs.IDLE;
	}

	private static int slot(InventoryState inv) {
		return inv.hotbarSlot(WallTactic::wallBlock);
	}

	private static int count(InventoryState inv) {
		return inv.count(WallTactic::wallBlock);
	}
}

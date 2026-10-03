package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Mace + wind charge combo. 26.2 facts: a mace hit "smashes" when the attacker has fallen more than
 * 1.5 blocks (bonus 4 per block for the first 3, 2 per block up to 8, then 1), and a wind charge
 * exploding at the thrower's feet launches them upward with no fall damage from that launch.
 * So: throw a wind charge straight down, switch to the mace while rising, and hit on the way down.
 */
public final class MaceTactic implements Tactic {
	private static final double MIN_RANGE = 1.5;
	private static final double MAX_RANGE = 6.0;
	/** MaceItem.SMASH_ATTACK_FALL_THRESHOLD */
	private static final double SMASH_FALL = 1.5;

	private enum Phase {
		IDLE,
		LAUNCH,
		AIRBORNE
	}

	private final Decision willMace = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticks;
	private boolean thrown;

	@Override
	public String name() {
		return "mace";
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return Scores.SPECIALIST + 0.12;
		}
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || !c.self.onGround() || maceSlot(inv) < 0 || windSlot(inv) < 0) {
			return 0;
		}
		double d = c.targetDistance();
		if (d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		return willMace.get(c.rng, c.profile.items().maceSkill(), 40) ? Scores.SPECIALIST + 0.05 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		phase = Phase.LAUNCH;
		ticks = 0;
		thrown = false;
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		willMace.reset();
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		ticks++;
		switch (phase) {
			case LAUNCH -> {
				if (!c.self.onGround() && c.self.velocity().y() > 0.3) {
					// The charge went off under us. (Checked first: a thrown charge is on cooldown.)
					phase = Phase.AIRBORNE;
					ticks = 0;
					return act(c);
				}
				int wind = windSlot(inv);
				if (ticks > 30 || wind < 0 && !thrown) {
					return finish();
				}
				int press = wind >= 0 ? c.memory.hands.request(c, wind) : -1;
				float[] look = c.lookAt(c.self.yaw(), 90.0F);
				boolean throwNow = !thrown && wind >= 0 && inv.selectedSlot() == wind && c.self.pitch() >= 80.0F && c.self.onGround();
				thrown |= throwNow;
				return new Inputs(look[0], look[1], 0, 0, false, false, false, false, throwNow, press);
			}
			case AIRBORNE -> {
				if (c.self.onGround() && ticks > 3 || ticks > 80 || t == null) {
					return finish();
				}
				int mace = maceSlot(inv);
				int press = c.memory.hands.request(c, mace);
				float goalYaw = Angles.yawTowards(c.self.eyePosition(), t.chest());
				float goalPitch = Angles.pitchTowards(c.self.eyePosition(), t.chest());
				float[] look = c.lookAt(goalYaw, goalPitch);
				double reach = c.self.attackReach() + c.memory.reachError;
				boolean falling = c.self.velocity().y() < 0 && c.self.fallDistance() > SMASH_FALL;
				boolean smash = inv.selectedSlot() == mace && falling && c.targetDistance() <= reach && c.crosshairOnTarget(reach + 0.5);
				// Drift towards the opponent while in the air.
				int forward = c.targetDistance() > 1.0 ? 1 : 0;
				return new Inputs(look[0], look[1], forward, 0, false, false, false, smash, false, press);
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	private Inputs finish() {
		phase = Phase.IDLE;
		willMace.reset();
		return Inputs.IDLE;
	}

	static int maceSlot(InventoryState inv) {
		return inv.hotbarSlot(ItemKind.MACE);
	}

	static int windSlot(InventoryState inv) {
		return inv.hotbarSlot(i -> i.kind() == ItemKind.WIND_CHARGE && !i.onCooldown());
	}
}

package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.ml.Mlp;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Melee driven by a trained network instead of hand-written rules: the network chooses the movement
 * keys, sprint, jump and when to click; aiming stays with the profile's aim model. Everything it sees
 * and does goes through the same reaction delay, aim limits and click limits as the scripted brain, so
 * a learned bot is held to the same human limits as any other.
 */
public final class LearnedMeleeTactic implements Tactic {
	private final Mlp net;

	public LearnedMeleeTactic(Mlp net) {
		if (net.inputs() != MeleeFeatures.COUNT || net.outputs() != MeleeFeatures.OUTPUTS) {
			throw new IllegalArgumentException("a melee network needs " + MeleeFeatures.COUNT + " inputs and " + MeleeFeatures.OUTPUTS + " outputs, got " + net);
		}
		this.net = net;
	}

	@Override
	public String name() {
		// Same name as the scripted melee, so playstyle weights apply to it.
		return "engage";
	}

	@Override
	public String detail() {
		return "learned";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		if (c.target == null || t == null || t.ticksSinceSeen() > 100) {
			return 0;
		}
		return t.visible() ? Scores.MELEE : Scores.MELEE - 0.2;
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		DuelMemory m = c.memory;
		TargetState aimAt = c.seen();
		Vec3 lead = aimAt.velocity().scale(c.profile.aim().trackingLead() * (c.trackingDelayTicks + 1));
		Vec3 goal = aimAt.chest().add(lead);
		float[] look = c.aim.step(self.yaw(), self.pitch(), Angles.yawTowards(self.eyePosition(), goal), Angles.pitchTowards(self.eyePosition(), goal));

		if (c.target.hurtTime() > m.lastTargetHurtTime && m.ticksSinceOwnClick < 6) {
			m.sinceOwnHit = 0;
		}
		SwordPlan.observe(c);
		MeleeFeatures.Decision d = MeleeFeatures.decode(net.forward(MeleeFeatures.encode(c)));

		int weapon = Hands.preferredMeleeSlot(self.inventory());
		int press = m.hands.request(c, weapon);
		boolean armed = weapon < 0 || self.inventory().selectedSlot() == weapon;
		boolean attack = d.attack() && armed && self.holdingMeleeWeapon() && !self.inventory().usingItem();
		if (attack) {
			m.ticksSinceOwnClick = 0;
			m.reachError = c.profile.reach().rangeErrorBlocks().sample(c.rng);
		}
		Inputs inputs = new Inputs(look[0], look[1], d.forward(), d.strafe(), d.jump(), false, d.sprint(), attack, false, press);
		return aimAt.visible() ? Movement.guardEdges(c, inputs) : Movement.navigate(c, inputs);
	}
}

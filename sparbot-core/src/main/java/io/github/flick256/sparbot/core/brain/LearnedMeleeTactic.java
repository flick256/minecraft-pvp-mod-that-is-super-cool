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
	private final int version;
	/** The scripted melee, for situations the network never saw in training (webs, water, lava, fire). */
	private final EngageTactic scripted = new EngageTactic();

	public LearnedMeleeTactic(Mlp net) {
		if (!fits(net)) {
			throw new IllegalArgumentException("a melee network needs " + MeleeFeatures.COUNT + " inputs and " + MeleeFeatures.OUTPUTS + " outputs (or "
				+ MeleeFeatures.COUNT_V2 + " and " + MeleeFeatures.OUTPUTS_V2 + "), got " + net);
		}
		this.net = net;
		this.version = MeleeFeatures.version(net.inputs());
	}

	/** Whether {@code net} has the shape of a melee network (version 1 or 2). */
	public static boolean fits(Mlp net) {
		return net.inputs() == MeleeFeatures.COUNT && net.outputs() == MeleeFeatures.OUTPUTS
			|| net.inputs() == MeleeFeatures.COUNT_V2 && net.outputs() == MeleeFeatures.OUTPUTS_V2;
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
	public void reset() {
		scripted.reset();
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		if (c.target == null || t == null || t.ticksSinceSeen() > 100) {
			return 0;
		}
		return t.visible() ? Scores.MELEE : Scores.MELEE - 0.2;
	}

	/**
	 * The simulator it trained in has flat ground and no blocks or fluids: when either fighter is webbed,
	 * in water or lava, or the bot is on fire, the scripted melee knows better.
	 */
	private static boolean unfamiliar(BrainContext c) {
		SelfState self = c.self;
		TargetState t = c.seen();
		return self.inWeb() || self.inWater() || self.inLava() || self.onFire() || t != null && (t.inWeb() || t.inWater());
	}

	@Override
	public Inputs act(BrainContext c) {
		if (unfamiliar(c)) {
			return scripted.act(c);
		}
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
		if (m.shieldDisabledTicks > 0) {
			m.shieldDisabledTicks--;
		}
		MeleeFeatures.Decision d = MeleeFeatures.decode(net.forward(MeleeFeatures.encode(c, version)));

		io.github.flick256.sparbot.core.item.InventoryState inv = self.inventory();
		int axe = inv.bestHotbarWeapon(io.github.flick256.sparbot.core.item.ItemKind.AXE);
		int weapon = d.axe() && axe >= 0 ? axe : Hands.preferredMeleeSlot(inv);
		int press = m.hands.request(c, weapon);
		boolean armed = weapon < 0 || inv.selectedSlot() == weapon;
		boolean shieldReady = inv.offhand().is(io.github.flick256.sparbot.core.item.ItemKind.SHIELD) && !inv.offhand().onCooldown();
		boolean block = d.block() && shieldReady && c.allows(Technique.BLOCK_HIT);
		// Never into a block under the crosshair (a player sees the outline), and only with the opponent within
		// the reach the bot judges it has: a player can't tell 2.9 blocks from 3.1, so they don't swing from the
		// very edge of reach every time (the same rule the scripted melee follows).
		boolean inJudgedReach = c.targetDistance() <= self.attackReach() + m.reachError;
		boolean attack = d.attack() && !block && armed && self.holdingMeleeWeapon() && !inv.usingItem() && inJudgedReach
			&& !c.crosshairBlockedBeforeTarget(self.attackReach() + 0.5);
		int forward = d.forward();
		boolean sprint = d.sprint() && !block;
		// The scripted shield counter on top (the network never learned to walk in behind the shield).
		TargetState seen = c.seen();
		boolean axeThreat = seen != null && seen.mainHand() == io.github.flick256.sparbot.core.item.ItemKind.AXE
			&& c.rng.chance(c.profile.items().shieldSkill());
		int counter = EngageTactic.shieldCounter(c, armed && shieldReady, axeThreat || d.axe(), c.targetDistance(), self.attackReach() + m.reachError);
		if (counter != EngageTactic.NO_COUNTER) {
			attack = false;
			block = true;
			sprint = false;
			forward = counter;
		}
		if (attack) {
			m.ticksSinceOwnClick = 0;
			m.reachError = c.profile.reach().rangeErrorBlocks().sample(c.rng);
			TargetState t = c.seen();
			if (inv.mainHand().is(io.github.flick256.sparbot.core.item.ItemKind.AXE) && t != null && t.blocking()) {
				m.shieldDisabledTicks = 100; // an axe on a raised shield disables it for 5 s
			}
		}
		// Switched-off techniques stay off for a learned bot too.
		int strafe = c.allows(Technique.STRAFE) ? d.strafe() : 0;
		Inputs inputs = new Inputs(look[0], look[1], forward, strafe, d.jump(), false, sprint, attack, block, press);
		return aimAt.visible() ? Movement.guardEdges(c, inputs) : Movement.navigate(c, inputs);
	}
}

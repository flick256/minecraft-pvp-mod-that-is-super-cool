package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.TargetState;
import org.junit.jupiter.api.Test;

class SwordPlanTest {
	/** Pro whose reading, feints, combos and crits are as given, so assertions don't depend on dice rolls. */
	private static SkillProfile pro(double read, double feint, double combo, double crit) {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.Technique t = p.technique();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			new SkillProfile.Technique(crit, t.wTapSkill(), t.sTapSkill(), 0, t.jumpResetSkill(), t.spacingSkill(), read, feint, combo), p.items(),
			p.mistakeRate(), p.panicHealthFraction());
	}

	/** An opponent whose last swing was {@code ticksSinceSwing} ago. */
	private static TargetState opponent(Vec3 pos, int ticksSinceSwing) {
		return new TargetState(7, "Steve", pos, Vec3.ZERO, 180, 20, 20, true, 0, false, true, 0, 0.3, 1.8, ItemKind.SWORD, ItemKind.EMPTY,
			ItemKind.EMPTY, 15, false, false, false, ticksSinceSwing);
	}

	@Test
	void staysOutOfAChargedOpponentsReachWhileRecharging() {
		BrainHarness h = new BrainHarness(pro(1, 0, 0, 0), TestFixtures.inventory(), opponent(new Vec3(0, 0, 2.6), TargetState.NO_SWING));
		h.attackStrength = 0.2F; // just swung: recharging
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertTrue(h.history.subList(10, 30).stream().allMatch(in -> in.forward() < 0), "backs out of the reach of a charged opponent");
		assertFalse(h.any(Inputs::attack), "no swing while recharging");
	}

	@Test
	void walksInWhenTheOpponentIsRechargingToo() {
		// They swung a moment ago: their charge is low, so there's nothing to fear.
		BrainHarness h = new BrainHarness(pro(1, 0, 0, 0), TestFixtures.inventory(), opponent(new Vec3(0, 0, 2.6), 2));
		h.attackStrength = 0.2F;
		for (int i = 0; i < 6; i++) {
			h.tick();
		}
		assertFalse(h.history.subList(4, 6).stream().anyMatch(in -> in.forward() < 0), "no reason to back off");
	}

	@Test
	void withoutReadingItJustWalksIn() {
		BrainHarness h = new BrainHarness(pro(0, 0, 0, 0), TestFixtures.inventory(), opponent(new Vec3(0, 0, 2.6), TargetState.NO_SWING));
		h.attackStrength = 0.2F;
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.history.subList(10, 30).stream().allMatch(in -> in.forward() < 0), "a player who doesn't read swings doesn't space");
	}

	@Test
	void feintsAtTheEdgeOfReach() {
		BrainHarness h = new BrainHarness(pro(1, 1, 0, 0), TestFixtures.inventory(), opponent(new Vec3(0, 0, 3.9), TargetState.NO_SWING));
		h.attackStrength = 0.2F;
		boolean in = false;
		boolean outAfterIn = false;
		for (int i = 0; i < 40; i++) {
			Inputs inputs = h.tick();
			if (inputs.forward() > 0) {
				in = true;
			} else if (in && inputs.forward() < 0) {
				outAfterIn = true;
			}
		}
		assertTrue(outAfterIn, "stepped in and straight back out");
	}

	@Test
	void punishesAMissedSwing() {
		BrainHarness h = new BrainHarness(pro(1, 0, 0, 0), TestFixtures.inventory(), opponent(new Vec3(0, 0, 3.6), TargetState.NO_SWING));
		h.attackStrength = 0.5F; // recharging, would normally hover outside their reach
		for (int i = 0; i < 12; i++) {
			h.tick();
		}
		// They swing at nothing: their arm swings, the bot isn't hurt.
		for (int i = 0; i < 12; i++) {
			h.target = opponent(new Vec3(0, 0, 3.6), i);
			h.tick();
		}
		assertTrue(h.history.subList(16, 22).stream().anyMatch(in -> in.forward() > 0 && in.sprint()), "rushed in while they recharge");
	}

	@Test
	void jumpsInForACritWhenCharged() {
		BrainHarness h = new BrainHarness(pro(0, 0, 0, 1), TestFixtures.inventory(), opponent(new Vec3(0, 0, 4.2), TargetState.NO_SWING));
		boolean jumped = false;
		for (int i = 0; i < 30 && !jumped; i++) {
			Inputs in = h.tick();
			jumped = in.jump() && !in.sprint();
		}
		assertTrue(jumped, "jumped in unsprinted to land the hit as a crit");
	}
}

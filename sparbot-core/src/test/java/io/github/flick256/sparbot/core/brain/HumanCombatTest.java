package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.aim.AimController;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.Distribution;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class HumanCombatTest {
	private static final Vec3 NEAR = new Vec3(0, 0, 3);
	private static final ItemInfo LAVA = TestFixtures.item(ItemKind.LAVA_BUCKET, "minecraft:lava_bucket", 1, 1);
	private static final ItemInfo WATER = TestFixtures.item(ItemKind.WATER_BUCKET, "minecraft:water_bucket", 1, 1);

	private static BrainContext context(long tick, SelfState self, TargetState recognised, TargetState tracked, DuelMemory memory, long seed) {
		SkillProfile base = TestFixtures.flawless("pro");
		SkillProfile p = new SkillProfile(base.id(), base.displayName(), base.description(), Distribution.fixed(200), base.pingMs(),
			base.aim(), base.clicking(), base.reach(), base.technique(), base.items(), 0, base.panicHealthFraction());
		Rng rng = new Rng(seed);
		return new BrainContext(new Observation(tick, self, recognised), tracked, 3, p, Playstyle.BALANCED, rng,
			new AimController(p.aim(), rng), memory);
	}

	private static SelfState swordWith(ItemInfo item) {
		return TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround(), TestFixtures.inventory(1, item), 20);
	}

	@Test
	void doesNotReachForLavaBeforeRecognisingAnEatingOpponent() {
		TargetState ready = TestFixtures.target(NEAR, 0);
		TargetState eating = TestFixtures.target(NEAR, 0, ItemKind.GOLDEN_APPLE, ItemKind.EMPTY, ItemKind.GOLDEN_APPLE, false);
		BrainContext c = context(20, swordWith(LAVA), ready, eating, new DuelMemory(), 1);
		assertFalse(c.opponentHelpless());
		assertFalse(c.utilityWindow());
		assertEquals(0, new LavaTactic().score(c));
	}

	@Test
	void guardsOnlyAfterRecognisingTheBowDraw() {
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround(),
			TestFixtures.inventory(40, TestFixtures.SHIELD), 20);
		TargetState ready = TestFixtures.target(new Vec3(0, 0, 12), 0);
		TargetState bow = TestFixtures.target(new Vec3(0, 0, 12), 0, ItemKind.BOW, ItemKind.EMPTY, ItemKind.BOW, false);
		assertEquals(0, new GuardTactic().score(context(20, self, ready, bow, new DuelMemory(), 1)));
		assertTrue(new GuardTactic().score(context(24, self, bow, bow, new DuelMemory(), 1)) > 0);
	}

	@Test
	void doesNotSwitchToTheAxeOnAnUnrecognisedShieldRaise() {
		TargetState ready = TestFixtures.target(NEAR, 0);
		TargetState blocking = TestFixtures.target(NEAR, 0, ItemKind.SWORD, ItemKind.SHIELD, ItemKind.SHIELD, true);
		DuelMemory memory = new DuelMemory();
		new EngageTactic().act(context(20, swordWith(TestFixtures.AXE), ready, blocking, memory, 1));
		assertFalse(memory.axeMode);
	}

	@Test
	void aVisiblyRaisedShieldCancelsTheAssumedStun() {
		TargetState blocking = TestFixtures.target(NEAR, 0, ItemKind.SWORD, ItemKind.SHIELD, ItemKind.SHIELD, true);
		DuelMemory memory = new DuelMemory();
		memory.stunTicks = 8;
		memory.stunPlanned = true;
		new EngageTactic().act(context(20, swordWith(TestFixtures.AXE), blocking, blocking, memory, 1));
		assertTrue(memory.axeMode, "a voluntary shield drop must not make us ignore it for five seconds");
		assertEquals(0, memory.stunTicks);
	}

	@Test
	void doesNotPourAgainOntoAnOpponentAlreadyBurningInTheOpen() {
		TargetState burning = new TargetState(7, "Steve", NEAR, Vec3.ZERO, 0, 20, 20, true, 0, false, true, 0,
			0.3, 1.8, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, 15, true, false);
		assertEquals(0, new LavaTactic().score(context(20, swordWith(LAVA), burning, burning, new DuelMemory(), 1)));
	}

	@Test
	void waterNeedsTimeToNoticeFire() {
		SelfState base = swordWith(WATER);
		SelfState burning = new SelfState(base.position(), base.eyePosition(), base.velocity(), 0, 0, 20, 20, 0, 20,
			true, false, false, false, 0, 1, 3, 0, true, TestFixtures.flatGround(), base.inventory(), java.util.List.of(), true, false, false);
		WaterTactic tactic = new WaterTactic();
		DuelMemory memory = new DuelMemory();
		for (int tick = 0; tick < 4; tick++) {
			assertEquals(0, tactic.score(context(tick, burning, null, null, memory, 1)), "tick " + tick);
		}
		assertTrue(tactic.score(context(4, burning, null, null, memory, 1)) > 0);
		// A danger that disappeared cancels the pending reaction.
		assertEquals(0, tactic.score(context(5, base, null, null, memory, 1)));
		assertEquals(0, tactic.score(context(6, burning, null, null, memory, 1)));
	}

	@Test
	void swingTimingVariesAndDoesNotRerollWhileWaiting() {
		var delays = new HashSet<Long>();
		for (long seed = 0; seed < 80; seed++) {
			DuelMemory memory = new DuelMemory();
			BrainContext first = context(100, swordWith(LAVA), TestFixtures.target(NEAR, 0), TestFixtures.target(NEAR, 0), memory, seed);
			SwingTiming.ready(first, true);
			long due = memory.swingReadyAt;
			delays.add(due - 100);
			for (long tick = 100; tick <= due; tick++) {
				BrainContext c = context(tick, first.self, first.target, first.tracked, memory, seed + tick);
				assertEquals(tick == due, SwingTiming.ready(c, true));
				assertEquals(due, memory.swingReadyAt);
			}
			assertFalse(SwingTiming.ready(first, false));
			assertEquals(-1, memory.swingReadyAt, "leaving reach cancels that swing window");
		}
		assertEquals(java.util.Set.of(0L, 1L, 2L), delays);
	}
}

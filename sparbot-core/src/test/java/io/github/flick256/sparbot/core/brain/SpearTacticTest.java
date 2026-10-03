package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.TargetState;
import org.junit.jupiter.api.Test;

class SpearTacticTest {
	private static final ItemInfo SPEAR = TestFixtures.item(ItemKind.SPEAR, "minecraft:netherite_spear", 1, 5);

	/** Pro with every spear decision certain, so assertions don't depend on dice rolls. */
	private static SkillProfile spearPro() {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.rodSkill(), i.potHealthFraction(), i.maceSkill(), 1.0, i.crystalSkill(), i.cartSkill()),
			p.mistakeRate(), p.panicHealthFraction());
	}

	private static TargetState moving(Vec3 pos, Vec3 velocity) {
		return new TargetState(7, "Steve", pos, velocity, 0, 20, 20, true, 0, false, true, 0, 0.3, 1.8, ItemKind.SWORD, ItemKind.EMPTY,
			ItemKind.EMPTY, 15);
	}

	@Test
	void jabsAtJabRangeWithAFullCharge() {
		BrainHarness h = new BrainHarness(spearPro(), TestFixtures.inventory(0, SPEAR), TestFixtures.target(new Vec3(0, 0, 3.3), 0));
		boolean jabbed = false;
		for (int i = 0; i < 40 && !jabbed; i++) {
			jabbed = h.tick().attack();
		}
		assertTrue(jabbed, "jabbed at 3 blocks");
		assertEquals("spear", h.brain.lastTrace().tactic());
		assertEquals(0, h.selected, "with the spear");
	}

	@Test
	void waitsForAFullChargeBeforeJabbing() {
		BrainHarness h = new BrainHarness(spearPro(), TestFixtures.inventory(0, SPEAR), TestFixtures.target(new Vec3(0, 0, 3.3), 0));
		h.attackStrength = 0.95F;
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::attack), "a spear jab needs a full charge (minimum_attack_charge 1.0)");
	}

	@Test
	void backsOutOfTheDeadZoneInsteadOfJabbing() {
		BrainHarness h = new BrainHarness(spearPro(), TestFixtures.inventory(0, SPEAR), TestFixtures.target(new Vec3(0, 0, 1.0), 0));
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::attack), "nothing closer than 2 blocks can be jabbed");
		assertTrue(h.history.subList(10, 30).stream().allMatch(in -> in.forward() < 0), "backs off to jab range");
	}

	@Test
	void sprintsInHoldingAChargeFromAGap() {
		BrainHarness h = new BrainHarness(spearPro(), TestFixtures.inventory(0, SPEAR), TestFixtures.target(new Vec3(0, 0, 10), 0));
		Inputs last = null;
		for (int i = 0; i < 30; i++) {
			last = h.tick();
		}
		assertTrue(last.use() && last.sprint() && last.forward() > 0, "charging: use held, sprinting forward");
		assertTrue(h.useTicks > 5, "the charge is being held, not tapped");
		assertEquals("spear", h.brain.lastTrace().tactic());
	}

	@Test
	void doesNotChargeATargetRunningStraightAway() {
		BrainHarness h = new BrainHarness(spearPro(), TestFixtures.inventory(0, SPEAR), moving(new Vec3(0, 0, 10), new Vec3(0, 0, 0.28)));
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::use), "a fleeing target takes no charge damage (relative speed below 4.6 blocks/s)");
	}

	@Test
	void prefersTheSwordWhenTheSpearSkillIsZero() {
		SkillProfile p = spearPro();
		SkillProfile.ItemSkills i = p.items();
		SkillProfile noSpear = new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(),
			p.reach(), p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.rodSkill(), i.potHealthFraction(), i.maceSkill(), 0.0, i.crystalSkill(), i.cartSkill()),
			p.mistakeRate(), p.panicHealthFraction());
		BrainHarness h = new BrainHarness(noSpear, TestFixtures.inventory(0, SPEAR, 1, TestFixtures.SWORD), TestFixtures.target(new Vec3(0, 0, 3.3), 0));
		for (int i2 = 0; i2 < 40; i2++) {
			h.tick();
		}
		assertEquals("engage", h.brain.lastTrace().tactic());
		assertEquals(1, h.selected, "switched to the sword");
	}
}

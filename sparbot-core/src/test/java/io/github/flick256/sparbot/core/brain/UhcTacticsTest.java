package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import org.junit.jupiter.api.Test;

class UhcTacticsTest {
	private static final ItemInfo LAVA = TestFixtures.item(ItemKind.LAVA_BUCKET, "minecraft:lava_bucket", 1, 1);
	private static final ItemInfo WATER = TestFixtures.item(ItemKind.WATER_BUCKET, "minecraft:water_bucket", 1, 1);
	private static final ItemInfo BUCKET = TestFixtures.item(ItemKind.BUCKET, "minecraft:bucket", 1, 1);
	private static final ItemInfo WEBS = TestFixtures.item(ItemKind.COBWEB, "minecraft:cobweb", 8, 1);

	/** Pro with certain UHC decisions, so assertions don't depend on dice rolls. */
	private static SkillProfile uhcPro() {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.rodSkill(), i.potHealthFraction(), i.maceSkill(),
				i.spearSkill(), i.crystalSkill(), i.cartSkill(), 1.0),
			p.mistakeRate(), p.panicHealthFraction());
	}

	/** Runs until the bot right-clicks holding {@code kind}; returns whether it did within {@code ticks}. */
	private static boolean usesWithin(BrainHarness h, ItemKind kind, int ticks) {
		for (int i = 0; i < ticks; i++) {
			Inputs in = h.tick();
			if (in.use() && h.slots[h.selected].kind() == kind) {
				return true;
			}
		}
		return false;
	}

	@Test
	void poursLavaUnderTheOpponentAndScoopsItBackUp() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, LAVA), TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		assertTrue(usesWithin(h, ItemKind.LAVA_BUCKET, 40), "emptied the lava bucket");
		assertTrue(h.pitch > 15, "onto the ground at the opponent's feet, pitch " + h.pitch);
		h.slots[1] = BUCKET; // vanilla turns it into an empty bucket
		assertTrue(usesWithin(h, ItemKind.BUCKET, 80), "scooped the lava back up");
	}

	@Test
	void websTheOpponent() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WEBS), TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		assertTrue(usesWithin(h, ItemKind.COBWEB, 40), "placed a cobweb");
		assertEquals("web", h.brain.lastTrace().tactic());
	}

	@Test
	void putsItselfOutWithWaterAndTakesItBack() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WATER), TestFixtures.target(new Vec3(0, 0, 6.0), 0));
		h.onFire = true;
		assertTrue(usesWithin(h, ItemKind.WATER_BUCKET, 40), "emptied the water bucket");
		assertTrue(h.pitch >= 80, "at its own feet, pitch " + h.pitch);
		h.slots[1] = BUCKET;
		h.onFire = false;
		assertTrue(usesWithin(h, ItemKind.BUCKET, 40), "picked the water back up");
	}
}

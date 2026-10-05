package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Explosions;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.Surroundings;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrystalTacticTest {
	private static final ItemInfo CRYSTAL = TestFixtures.item(ItemKind.END_CRYSTAL, "minecraft:end_crystal", 64, 1);
	private static final ItemInfo OBSIDIAN = TestFixtures.item(ItemKind.BLOCK, "minecraft:obsidian", 64, 1);

	/** Pro that always goes for crystals, so assertions don't depend on dice rolls. */
	private static SkillProfile crystalPro() {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.potHealthFraction(), i.maceSkill(),
				i.spearSkill(), 1.0, i.cartSkill(), i.uhcSkill()),
			p.mistakeRate(), p.panicHealthFraction());
	}

	private static BrainHarness harness(Vec3 target, Surroundings world) {
		BrainHarness h = new BrainHarness(crystalPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, CRYSTAL, 2, OBSIDIAN),
			TestFixtures.target(target, 0));
		h.world = world;
		return h;
	}

	@Test
	void explosionDamageFollowsVanilla() {
		Vec3 c = Vec3.ZERO;
		// (p^2 + p) / 2 * 7 * 12 + 1 with p = 1 - distance / 12.
		assertEquals(85.0, Explosions.rawDamage(c, c, Explosions.CRYSTAL_POWER), 1e-9);
		assertEquals(1.0, Explosions.rawDamage(c, new Vec3(0, 0, 12), Explosions.CRYSTAL_POWER), 1e-9);
		assertEquals(0.0, Explosions.rawDamage(c, new Vec3(0, 0, 12.5), Explosions.CRYSTAL_POWER), 1e-9);
		assertEquals((0.25 + 0.5) / 2 * 84 + 1, Explosions.rawDamage(c, new Vec3(6, 0, 0), Explosions.CRYSTAL_POWER), 1e-9);
	}

	@Test
	void hitsACrystalBesideTheOpponent() {
		BrainHarness h = harness(new Vec3(0, 0, 3.0), new Surroundings(List.of(new Vec3(0, 0, 3.6)), List.of(), List.of()));
		boolean hit = false;
		for (int i = 0; i < 40 && !hit; i++) {
			hit = h.tick().attack();
		}
		assertTrue(hit, "hit the crystal");
		assertEquals("crystal", h.brain.lastTrace().tactic());
	}

	@Test
	void placesACrystalOnObsidianNextToTheOpponent() {
		BrainHarness h = harness(new Vec3(0, 0, 2.5), new Surroundings(List.of(), List.of(new BlockSpot(0, -1, 3)), List.of()));
		boolean placed = false;
		for (int i = 0; i < 40 && !placed; i++) {
			Inputs in = h.tick();
			placed = in.use() && h.slots[h.selected].kind() == ItemKind.END_CRYSTAL;
		}
		assertTrue(placed, "right-clicked the obsidian holding a crystal");
		assertTrue(h.pitch > 10, "looking down at the block's top, pitch " + h.pitch);
	}

	@Test
	void putsDownObsidianWhenThereIsNone() {
		BrainHarness h = harness(new Vec3(0, 0, 2.5), new Surroundings(List.of(), List.of(), List.of(new BlockSpot(0, -1, 3), new BlockSpot(5, -1, 5))));
		boolean placed = false;
		for (int i = 0; i < 40 && !placed; i++) {
			Inputs in = h.tick();
			placed = in.use() && "minecraft:obsidian".equals(h.slots[h.selected].id());
		}
		assertTrue(placed, "right-clicked the ground holding obsidian");
	}

	@Test
	void onlyPlacesCrystalsItCanReachToHit() {
		// Blocks are reachable to 4.5 but a crystal only to 3: a crystal there could never be detonated.
		BrainHarness h = harness(new Vec3(0, 0, 3.5), new Surroundings(List.of(), List.of(new BlockSpot(0, -1, 4)), List.of()));
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::use), "no crystal placed out of hitting reach");
	}

	@Test
	void doesNotBlowItselfUp() {
		// The crystal is as close to the bot as to the opponent: a careful player leaves it.
		BrainHarness h = harness(new Vec3(0, 0, 3.0), new Surroundings(List.of(new Vec3(0, 0, 1.5)), List.of(), List.of()));
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertFalse(h.any(in -> in.attack() && h.brain.lastTrace().tactic().equals("crystal")), "never detonated a crystal at its own feet");
	}

	@Test
	void fightsWithTheSwordWithoutCrystals() {
		BrainHarness h = new BrainHarness(crystalPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 2, OBSIDIAN), TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		h.world = new Surroundings(List.of(), List.of(), List.of(new BlockSpot(0, -1, 4)));
		for (int i = 0; i < 20; i++) {
			h.tick();
		}
		assertEquals("engage", h.brain.lastTrace().tactic());
	}

	@Test
	void fightsWithTheSwordWhenRushedWithNothingToBlowUp() {
		// Opponent in its face, no obsidian or crystal spot that wouldn't hurt the bot as much: sword them.
		BrainHarness h = harness(new Vec3(0, 0, 2.0), Surroundings.EMPTY);
		boolean hit = false;
		for (int i = 0; i < 40 && !hit; i++) {
			Inputs in = h.tick();
			hit = in.attack() && h.slots[h.selected].kind() == ItemKind.SWORD;
		}
		assertTrue(hit, "hit back with the sword");
		assertEquals("engage", h.brain.lastTrace().tactic());
	}

	@Test
	void armorTakesBlastDamageDownAsVanillaDoes() {
		// 56 raw through 20 armor and 12 toughness: 56 x (1 - (20 - 56 / 5) / 25), then Protection IV x 4 (16 EPF): x 0.36.
		assertEquals(56 * (1 - 8.8 / 25) * 0.36, Explosions.afterArmor(56, BrainHarness.netherite()), 1e-9);
		assertEquals(56, Explosions.afterArmor(56, new ItemInfo[] {ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY}), 1e-9);
	}

	@Test
	void neverSetsOffACrystalThatWouldKillIt() {
		// About 11 damage through maxed netherite from 3.6 blocks: fine at full health, deadly at 6.
		BrainHarness h = harness(new Vec3(0, 0, 3.0), new Surroundings(List.of(new Vec3(0, 0, 3.6)), List.of(), List.of()));
		h.health = 6;
		for (int i = 0; i < 40; i++) {
			assertFalse(h.tick().attack() && "crystal".equals(h.brain.lastTrace().tactic()), "hit a crystal that would kill it");
		}
	}

	@Test
	void stepsOutOfTheOpponentsCrystal() {
		// Their crystal right by the bot, the opponent well away: the bot won't set it off, they will.
		BrainHarness h = harness(new Vec3(0, 0, 6.0), new Surroundings(List.of(new Vec3(1.5, 0, 0.5)), List.of(), List.of()));
		boolean dodged = false;
		for (int i = 0; i < 30 && !dodged; i++) {
			Inputs in = h.tick();
			dodged = "dodge".equals(h.brain.lastTrace().tactic()) && (in.forward() != 0 || in.strafe() != 0);
			assertFalse(in.attack() && "crystal".equals(h.brain.lastTrace().tactic()), "set off a crystal that hurts the bot most");
		}
		assertTrue(dodged, "moved out of the blast");
	}
}

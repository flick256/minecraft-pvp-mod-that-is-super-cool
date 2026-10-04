package io.github.flick256.sparbot.core.style;

import io.github.flick256.sparbot.core.profile.SkillProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a bot prefers to fight, independent of how well it plays. A playstyle is a weighted set of
 * tactics (each tactic's utility score is multiplied by its weight) plus small biases on the skill
 * profile (e.g. a W-tap player W-taps more often). Skill profile and playstyle combine freely: a
 * Beginner kiter and a Pro kiter play the same way, at different levels.
 *
 * @param tacticWeights tactic name to score multiplier (0-3); tactics not listed keep weight 1
 * @param skillBias skill name to an additive bias (-1 to 1), clamped to the skill's range afterwards
 * @param preferredRange distance in blocks a ranged playstyle tries to keep (0 = no preference)
 */
public record Playstyle(String id, String displayName, String description, Map<String, Double> tacticWeights, Map<String, Double> skillBias,
	double preferredRange) {
	/** Tactic names a weight can refer to (see the brain's tactic list). */
	public static final Set<String> TACTICS = Set.of("engage", "retreat", "search", "heal", "retotem", "ranged", "guard", "pearl", "kite", "pot", "refill", "buff", "mace", "spear", "crystal", "cart", "lava", "web", "water", "wall");
	/** Skills a bias can refer to. */
	public static final Set<String> SKILLS = Set.of("critSkill", "wTapSkill", "sTapSkill", "strafeSkill", "jumpResetSkill", "spacingSkill",
		"retotemSkill", "shieldSkill", "axeSkill", "bowSkill", "pearlSkill", "gappleHealthFraction", "panicHealthFraction",
		"cooldownDiscipline", "potHealthFraction", "maceSkill", "spearSkill", "crystalSkill", "cartSkill", "uhcSkill");

	public static final Playstyle BALANCED = new Playstyle("balanced", "Balanced", "No preferences: every tactic at face value.", Map.of(), Map.of(), 0);

	public double weight(String tactic) {
		return tacticWeights == null ? 1.0 : tacticWeights.getOrDefault(tactic, 1.0);
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (id == null || !id.matches("[a-z0-9_\\-+.]+")) {
			errors.add("id must be lowercase [a-z0-9_-]+, was " + id);
		}
		if (tacticWeights != null) {
			tacticWeights.forEach((tactic, w) -> {
				if (!TACTICS.contains(tactic)) {
					errors.add("unknown tactic '" + tactic + "' (known: " + TACTICS + ")");
				}
				if (w == null || w < 0 || w > 3) {
					errors.add("weight for " + tactic + " must be 0-3");
				}
			});
		}
		if (skillBias != null) {
			skillBias.forEach((skill, b) -> {
				if (!SKILLS.contains(skill)) {
					errors.add("unknown skill '" + skill + "' (known: " + SKILLS + ")");
				}
				if (b == null || b < -1 || b > 1) {
					errors.add("bias for " + skill + " must be -1 to 1");
				}
			});
		}
		if (preferredRange < 0 || preferredRange > 40) {
			errors.add("preferredRange must be 0-40");
		}
		return errors;
	}

	/** The profile as this playstyle plays it: skills shifted by the biases, clamped to [0, 1]. */
	public SkillProfile applyTo(SkillProfile p) {
		if (skillBias == null || skillBias.isEmpty()) {
			return p;
		}
		SkillProfile.Technique t = p.technique();
		SkillProfile.Technique technique = new SkillProfile.Technique(b("critSkill", t.critSkill()), b("wTapSkill", t.wTapSkill()),
			b("sTapSkill", t.sTapSkill()), b("strafeSkill", t.strafeSkill()), b("jumpResetSkill", t.jumpResetSkill()),
			b("spacingSkill", t.spacingSkill()));
		SkillProfile.ItemSkills i = p.items();
		SkillProfile.ItemSkills items = new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), b("retotemSkill", i.retotemSkill()),
			b("gappleHealthFraction", i.gappleHealthFraction()), i.eatHungerBelow(), b("shieldSkill", i.shieldSkill()), b("axeSkill", i.axeSkill()),
			b("bowSkill", i.bowSkill()), b("pearlSkill", i.pearlSkill()), b("potHealthFraction", i.potHealthFraction()), b("maceSkill", i.maceSkill()),
			b("spearSkill", i.spearSkill()), b("crystalSkill", i.crystalSkill()),
			b("cartSkill", i.cartSkill()), b("uhcSkill", i.uhcSkill()));
		SkillProfile.Clicking clicking = new SkillProfile.Clicking(p.clicking().cps(), b("cooldownDiscipline", p.clicking().cooldownDiscipline()));
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), clicking, p.reach(), technique,
			items, p.mistakeRate(), b("panicHealthFraction", p.panicHealthFraction()));
	}

	private double b(String skill, double value) {
		return Math.max(0, Math.min(1, value + skillBias.getOrDefault(skill, 0.0)));
	}

	/** One playstyle in a mix, with its share. */
	public record Share(Playstyle style, double share) {
	}

	/**
	 * Mixes playstyles by share (e.g. 70% rusher, 30% kiter): weights, biases and preferred range are
	 * the share-weighted averages (a tactic a style does not mention counts as weight 1 / bias 0 for it).
	 */
	public static Playstyle blend(List<Share> shares) {
		if (shares.size() == 1) {
			return shares.get(0).style();
		}
		double total = shares.stream().mapToDouble(Share::share).sum();
		if (total <= 0) {
			throw new IllegalArgumentException("Playstyle shares must add up to more than 0");
		}
		Map<String, Double> weights = new LinkedHashMap<>();
		Map<String, Double> biases = new LinkedHashMap<>();
		double range = 0;
		List<String> ids = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (String tactic : TACTICS) {
			double w = 0;
			for (Share s : shares) {
				w += s.style().weight(tactic) * s.share() / total;
			}
			if (Math.abs(w - 1.0) > 1e-9) {
				weights.put(tactic, w);
			}
		}
		for (String skill : SKILLS) {
			double bias = 0;
			for (Share s : shares) {
				bias += (s.style().skillBias() == null ? 0 : s.style().skillBias().getOrDefault(skill, 0.0)) * s.share() / total;
			}
			if (Math.abs(bias) > 1e-9) {
				biases.put(skill, bias);
			}
		}
		for (Share s : shares) {
			range += s.style().preferredRange() * s.share() / total;
			ids.add(s.style().id() + ":" + Math.round(s.share() / total * 100));
			names.add(s.style().displayName());
		}
		return new Playstyle(String.join("+", ids).replace(':', '.'), String.join(" / ", names), "Mix of " + String.join(", ", ids), weights, biases, range);
	}
}

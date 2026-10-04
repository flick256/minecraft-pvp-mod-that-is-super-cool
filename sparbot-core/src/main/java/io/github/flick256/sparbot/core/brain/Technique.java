package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.profile.SkillProfile;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * The movement and clicking techniques a bot uses, each of which can be switched off per bot (for
 * practising against a bot that never W-taps, say). A switched-off technique is never used, whatever
 * the skill profile says; a switched-on one is used as often and as well as the profile's skill says.
 */
public enum Technique {
	STRAFE("strafe", p -> p.technique().strafeSkill()),
	W_TAP("wtap", p -> p.technique().wTapSkill()),
	S_TAP("stap", p -> p.technique().sTapSkill()),
	JUMP_RESET("jumpreset", p -> p.technique().jumpResetSkill()),
	CRITS("crits", p -> p.technique().critSkill()),
	SPACING("spacing", p -> p.technique().spacingSkill()),
	/** Judging the opponent's attack charge from their swings: spacing out of their reach, punishing misses, dodging. */
	READING("reading", p -> p.technique().readSkill()),
	/** Stepping into the opponent's reach and back out to draw a swing. */
	FEINTS("feints", p -> p.technique().feintSkill()),
	/** W/S-taps after a hit so the next one lands at full charge from the edge of reach. */
	COMBOS("combos", p -> p.technique().comboSkill()),
	/** Raising the shield between swings (blocking against incoming threats is separate). */
	BLOCK_HIT("blockhit", p -> p.items().shieldSkill()),
	/** Following an axe hit on a shield with an immediate weapon hit (and a web under the opponent in UHC). */
	SHIELD_STUN("shieldstun", p -> p.items().axeSkill()),
	/** Hitting back, sidestepping and jump-resetting while in the middle of utility (buckets, blocks, crystals). */
	REFLEXES("reflexes", p -> p.technique().jumpResetSkill());

	private final String id;
	private final ToDoubleFunction<SkillProfile> skill;

	Technique(String id, ToDoubleFunction<SkillProfile> skill) {
		this.id = id;
		this.skill = skill;
	}

	public String id() {
		return id;
	}

	/** The profile's skill at this technique (0-1). */
	public double skill(SkillProfile profile) {
		return skill.applyAsDouble(profile);
	}

	public static Optional<Technique> byId(String id) {
		return Arrays.stream(values()).filter(t -> t.id.equals(id.toLowerCase(Locale.ROOT).trim())).findFirst();
	}

	/** Parses a comma-separated list of technique ids; unknown ids are ignored. */
	public static Set<Technique> parse(String list) {
		EnumSet<Technique> set = EnumSet.noneOf(Technique.class);
		if (list != null) {
			for (String id : list.split(",")) {
				if (!id.isBlank()) {
					byId(id).ifPresent(set::add);
				}
			}
		}
		return set;
	}
}

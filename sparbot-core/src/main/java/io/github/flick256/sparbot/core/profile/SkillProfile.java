package io.github.flick256.sparbot.core.profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven description of how well a bot plays. Every field is consumed by the brain or the input
 * shaper; nothing here grants abilities a human could not have, it only limits or degrades play.
 *
 * <p>Loaded from JSON (see {@link SkillProfiles}); presets live in {@code /sparbot/profiles/}.
 */
public record SkillProfile(
	String id,
	String displayName,
	String description,
	/** Delay between the world changing and the bot reacting to it. */
	Distribution reactionTimeMs,
	/** Simulated network latency; half is applied to perception, half to outgoing inputs. */
	Distribution pingMs,
	Aim aim,
	Clicking clicking,
	Reach reach,
	Technique technique,
	/** Chance per decision window (~1s) of a deliberate-looking blunder (whiff, ignoring an edge, over-chasing). */
	double mistakeRate,
	/** Health fraction (0-1) below which the bot starts to panic and disengage. */
	double panicHealthFraction
) {
	/**
	 * @param maxTurnDegPerTick hard cap on rotation per tick (applies to yaw and pitch combined)
	 * @param smoothing fraction of the remaining aim error corrected per tick (0-1)
	 * @param jitterDeg standard deviation of per-tick hand jitter
	 * @param overshootChance chance that a large flick overshoots the target
	 * @param overshootFactor how far past the target an overshoot goes, as a fraction of the flick
	 * @param trackingLead fraction of the target's velocity the bot leads its aim by (0 = aims where target was)
	 */
	public record Aim(double maxTurnDegPerTick, double smoothing, double jitterDeg, double overshootChance, double overshootFactor, double trackingLead) {
	}

	/**
	 * @param cps clicks-per-second the bot's finger is capable of while spamming
	 * @param cooldownDiscipline 0 = spams regardless of attack cooldown, 1 = always waits for a full charge
	 */
	public record Clicking(Distribution cps, double cooldownDiscipline) {
	}

	/**
	 * @param rangeErrorBlocks how far off the bot's judgement of its reach is. Positive values make it
	 *     click when the target is actually out of reach (whiffs); negative values make it wait too long.
	 */
	public record Reach(Distribution rangeErrorBlocks) {
	}

	/** Technique skills, each the probability (0-1) of executing the technique when it applies. */
	public record Technique(double critSkill, double wTapSkill, double sTapSkill, double strafeSkill, double jumpResetSkill, double spacingSkill) {
	}

	/** Returns a list of human-readable problems; empty when the profile is valid. */
	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (id == null || !id.matches("[a-z0-9_\\-]+")) {
			errors.add("id must be lowercase [a-z0-9_-]+, was " + id);
		}
		if (displayName == null || displayName.isBlank()) {
			errors.add("displayName is required");
		}
		if (reactionTimeMs == null || pingMs == null || aim == null || clicking == null || reach == null || technique == null) {
			errors.add("reactionTimeMs, pingMs, aim, clicking, reach and technique are all required");
			return errors;
		}
		// 100 ms is roughly the fastest visual reaction a human can sustain.
		reactionTimeMs.validate("reactionTimeMs", 100, 2000, errors);
		pingMs.validate("pingMs", 0, 1000, errors);
		clicking.cps().validate("clicking.cps", 1, 20, errors);
		reach.rangeErrorBlocks().validate("reach.rangeErrorBlocks", -3, 3, errors);
		range("aim.maxTurnDegPerTick", aim.maxTurnDegPerTick(), 1, 180, errors);
		range("aim.smoothing", aim.smoothing(), 0.01, 1, errors);
		range("aim.jitterDeg", aim.jitterDeg(), 0, 15, errors);
		range("aim.overshootChance", aim.overshootChance(), 0, 1, errors);
		range("aim.overshootFactor", aim.overshootFactor(), 0, 1, errors);
		range("aim.trackingLead", aim.trackingLead(), 0, 1, errors);
		range("clicking.cooldownDiscipline", clicking.cooldownDiscipline(), 0, 1, errors);
		range("technique.critSkill", technique.critSkill(), 0, 1, errors);
		range("technique.wTapSkill", technique.wTapSkill(), 0, 1, errors);
		range("technique.sTapSkill", technique.sTapSkill(), 0, 1, errors);
		range("technique.strafeSkill", technique.strafeSkill(), 0, 1, errors);
		range("technique.jumpResetSkill", technique.jumpResetSkill(), 0, 1, errors);
		range("technique.spacingSkill", technique.spacingSkill(), 0, 1, errors);
		range("mistakeRate", mistakeRate, 0, 1, errors);
		range("panicHealthFraction", panicHealthFraction, 0, 1, errors);
		return errors;
	}

	private static void range(String name, double value, double min, double max, List<String> errors) {
		if (!(value >= min && value <= max)) {
			errors.add(name + " must be within [" + min + ", " + max + "], was " + value);
		}
	}
}

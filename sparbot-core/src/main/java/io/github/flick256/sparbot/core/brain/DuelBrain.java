package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.AimController;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.PerceptionDelay;
import io.github.flick256.sparbot.core.sense.TargetState;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility-AI brain for a melee duel. Each tick every tactic is scored and the best one acts; the
 * currently active tactic gets a small bonus so the bot does not dither between two close scores.
 */
public final class DuelBrain implements Policy {
	private static final double HYSTERESIS = 0.05;
	private static final int MISTAKE_WINDOW_TICKS = 20;
	private static final int REACTION_RESAMPLE_TICKS = 20;
	/** Visual-motor latency of continuous tracking (about one tick on top of network delay). */
	private static final int TRACKING_BASE_DELAY_TICKS = 1;

	private final SkillProfile profile;
	private final Playstyle style;
	private final Rng rng;
	private final AimController aim;
	private final PerceptionDelay perception = new PerceptionDelay();
	private final List<Tactic> tactics = List.of(new EngageTactic(), new RetreatTactic(), new SearchTactic(), new HealTactic(),
		new RetotemTactic(), new RangedTactic(), new GuardTactic(), new PearlTactic(), new RodTactic(), new KiteTactic());
	private DuelMemory memory = new DuelMemory();
	private Tactic active;
	private DecisionTrace trace = DecisionTrace.NONE;

	public DuelBrain(SkillProfile profile, long seed) {
		this(profile, Playstyle.BALANCED, seed);
	}

	/** @param profile how well the bot plays; @param style how it prefers to fight (biases are applied to the profile) */
	public DuelBrain(SkillProfile profile, Playstyle style, long seed) {
		this.profile = style.applyTo(profile);
		this.style = style;
		this.rng = new Rng(seed);
		this.aim = new AimController(this.profile.aim(), rng.fork());
	}

	/** The effective profile: the skill profile with the playstyle's biases applied. */
	public SkillProfile profile() {
		return profile;
	}

	public Playstyle style() {
		return style;
	}

	public DuelMemory memory() {
		return memory;
	}

	@Override
	public Inputs act(Observation raw) {
		perception.push(raw.target());
		if (--memory.ticksUntilReactionResample <= 0) {
			double networkMs = profile.pingMs().sample(rng) / 2.0;
			memory.reactionDelayTicks = (int) Math.round((profile.reactionTimeMs().sample(rng) + networkMs) / 50.0);
			memory.trackingDelayTicks = TRACKING_BASE_DELAY_TICKS + (int) Math.round(networkMs / 50.0);
			memory.ticksUntilReactionResample = REACTION_RESAMPLE_TICKS;
		}
		TargetState seen = perception.delayed(memory.reactionDelayTicks);
		TargetState tracked = perception.delayed(memory.trackingDelayTicks);
		Observation observation = new Observation(raw.tick(), raw.self(), seen);

		if (--memory.ticksUntilMistakeRoll <= 0) {
			memory.ticksUntilMistakeRoll = MISTAKE_WINDOW_TICKS;
			memory.mistake = rng.chance(profile.mistakeRate()) ? pickMistake() : Mistake.NONE;
		}

		BrainContext context = new BrainContext(observation, tracked, memory.trackingDelayTicks, profile, style, rng, aim, memory);
		Map<String, Double> scores = new LinkedHashMap<>();
		Tactic best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Tactic tactic : tactics) {
			// The playstyle reshapes priorities; "search" is the idle fallback and is never weighted.
			double score = tactic.score(context);
			if (score > 0 && !(tactic instanceof SearchTactic)) {
				score *= style.weight(tactic.name());
			}
			scores.put(tactic.name(), score);
			double effective = tactic == active ? score + HYSTERESIS : score;
			if (effective > bestScore) {
				bestScore = effective;
				best = tactic;
			}
		}
		if (best != active) {
			if (active != null) {
				active.onExit(context);
			}
			best.onEnter(context);
			active = best;
		}

		Inputs inputs = active.act(context);

		memory.ticksSinceOwnClick++;
		if (memory.retreatCooldown > 0) {
			memory.retreatCooldown--;
		}
		memory.lastSelfHurtTime = raw.self().hurtTime();
		memory.lastTargetHurtTime = seen == null ? 0 : seen.hurtTime();

		float goalYaw = raw.self().yaw() + inputs.yawDelta();
		float goalPitch = raw.self().pitch() + inputs.pitchDelta();
		trace = new DecisionTrace(active.name(), scores, describe(), Angles.wrapDegrees(goalYaw), goalPitch, context.targetDistance());
		return inputs;
	}

	@Override
	public DecisionTrace lastTrace() {
		return trace;
	}

	@Override
	public void reset() {
		memory = new DuelMemory();
		tactics.forEach(Tactic::reset);
		perception.clear();
		active = null;
		trace = DecisionTrace.NONE;
	}

	private Mistake pickMistake() {
		Mistake[] kinds = {Mistake.WHIFF, Mistake.EDGE_BLIND, Mistake.OVERCHASE};
		return kinds[rng.nextInt(0, kinds.length - 1)];
	}

	private String describe() {
		return "style=" + style.id() + " mistake=" + memory.mistake + " crit=" + memory.critPhase + " reactionTicks=" + memory.reactionDelayTicks + " trackTicks=" + memory.trackingDelayTicks
			+ " clickAt=" + String.format("%.2f", memory.cooldownThreshold);
	}
}

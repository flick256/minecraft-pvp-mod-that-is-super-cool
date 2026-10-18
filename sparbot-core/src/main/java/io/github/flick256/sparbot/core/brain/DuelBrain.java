package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.AimController;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
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
	/**
	 * A real client never shows an opponent where the server has them: it eases each other player towards
	 * their latest position over three ticks (entity interpolation), so what a player sees, aims at and reacts
	 * to is about two ticks old on top of their ping. The bot sees the world that late too.
	 */
	public static final int INTERPOLATION_TICKS = 2;

	private final SkillProfile profile;
	private final Playstyle style;
	private final Rng rng;
	private final AimController aim;
	private final PerceptionDelay perception = new PerceptionDelay();
	private final List<Tactic> tactics;
	private DuelMemory memory = new DuelMemory();
	private java.util.Set<Technique> disabled = java.util.EnumSet.noneOf(Technique.class);
	private Tactic active;
	private int focusDelayTicks;
	private final SwightPlan swightPlan = new SwightPlan();
	private DecisionTrace trace = DecisionTrace.NONE;

	public DuelBrain(SkillProfile profile, long seed) {
		this(profile, Playstyle.BALANCED, seed);
	}

	/** @param profile how well the bot plays; @param style how it prefers to fight (biases are applied to the profile) */
	public DuelBrain(SkillProfile profile, Playstyle style, long seed) {
		this(profile, style, seed, new EngageTactic());
	}

	/** @param melee what fights in melee (the scripted {@link EngageTactic}, or a test's stand-in) */
	public DuelBrain(SkillProfile profile, Playstyle style, long seed, Tactic melee) {
		this.tactics = List.of(melee, new RetreatTactic(), new SearchTactic(), new HealTactic(),
			new RetotemTactic(), new RangedTactic(), new GuardTactic(), new PearlTactic(), new KiteTactic(),
			new PotTactic(), new RefillTactic(), new BuffTactic(), new MaceTactic(), new SpearTactic(), new CrystalTactic(), new AnchorTactic(), new CartTactic(), new DefuseTactic(), new DodgeTactic(),
			new LavaTactic(), new WebTactic(), new WaterTactic(), new WallTactic(), new BacklineTactic(), new CleanupTactic(), new BoostTactic(), new BreakWebTactic());
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

	/** Switches techniques off (the rest on). */
	public void setDisabledTechniques(java.util.Set<Technique> techniques) {
		this.disabled = techniques.isEmpty() ? java.util.EnumSet.noneOf(Technique.class) : java.util.EnumSet.copyOf(techniques);
	}

	public java.util.Set<Technique> disabledTechniques() {
		return java.util.Collections.unmodifiableSet(disabled);
	}

	@Override
	public Inputs act(Observation raw) {
		perception.push(raw.target());
		Vec3 here = raw.self().position();
		memory.selfMotion = memory.lastSelfPosition == null ? Vec3.ZERO : here.subtract(memory.lastSelfPosition);
		memory.lastSelfPosition = here;
		if (--memory.ticksUntilReactionResample <= 0) {
			double networkMs = profile.pingMs().sample(rng) / 2.0;
			memory.reactionDelayTicks = INTERPOLATION_TICKS + (int) Math.round((profile.reactionTimeMs().sample(rng) + networkMs) / 50.0);
			memory.trackingDelayTicks = INTERPOLATION_TICKS + TRACKING_BASE_DELAY_TICKS + (int) Math.round(networkMs / 50.0);
			memory.ticksUntilReactionResample = REACTION_RESAMPLE_TICKS;
		}
		// While looking down to place utility, noticing an opponent change takes another reaction.
		// Build and release this cost gradually so switching tactics cannot instantly restore tracking.
		int focusGoal = active != null && active.requiresFocus() ? Math.max(1, memory.reactionDelayTicks - memory.trackingDelayTicks) : 0;
		focusDelayTicks += Integer.compare(focusGoal, focusDelayTicks);
		int trackingTicks = memory.trackingDelayTicks + focusDelayTicks;
		TargetState seen = perception.delayed(memory.reactionDelayTicks + focusDelayTicks);
		TargetState tracked = perception.delayed(trackingTicks);
		Observation observation = new Observation(raw.tick(), raw.self(), seen, raw.surroundings());
		if (seen != null && seen.onGround()) {
			memory.targetGroundY = seen.position().y();
		}

		if (--memory.ticksUntilMistakeRoll <= 0) {
			memory.ticksUntilMistakeRoll = MISTAKE_WINDOW_TICKS;
			memory.mistake = rng.chance(profile.mistakeRate()) ? pickMistake() : Mistake.NONE;
		}

		memory.wanderAim(rng, profile.aim());
		BrainContext context = new BrainContext(observation, tracked, trackingTicks, profile, style, rng, aim, memory, disabled);
		Map<String, Double> scores = new LinkedHashMap<>();
		double[] tacticScores = new double[tactics.size()];
		for (int i = 0; i < tacticScores.length; i++) {
			Tactic tactic = tactics.get(i);
			// The playstyle reshapes priorities; "search" is the idle fallback and is never weighted.
			double score = tactic.score(context);
			if (score > 0 && !(tactic instanceof SearchTactic)) {
				score *= style.weight(tactic.name()) * SwightPlan.weight(context, tactic.name());
				// A clear opening (scored as high as a webbed or eating opponent gives) is taken whatever the restraint.
				// Nor is a play already under way: dropping it half done (a web half placed) is the worst of both.
				if (restrained && score < RESTRAINT_ABOVE && tactic != active) {
					score *= restraint(tactic.name());
				}
			}
			tacticScores[i] = score;
		}
		Tactic best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < tacticScores.length; i++) {
			Tactic tactic = tactics.get(i);
			scores.put(tactic.name(), tacticScores[i]);
			double effective = tactic == active ? tacticScores[i] + HYSTERESIS : tacticScores[i];
			if (effective > bestScore) {
				bestScore = effective;
				best = tactic;
			}
		}
		if (best != active) {
			memory.swingReadyAt = -1;
			if (active != null) {
				active.onExit(context);
			}
			best.onEnter(context);
			active = best;
		}

		Inputs inputs = swightPlan.limitShield(context, Unstuck.apply(context, Reflexes.apply(context, active, active.act(context))));

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
		swightPlan.reset();
		active = null;
		focusDelayTicks = 0;
		trace = DecisionTrace.NONE;
	}

	/**
	 * How readily the UHC utility plays are reached for, against the scores their tactics give. Tuned in the UHC
	 * simulator (UhcLab, tactic-weight entrants): reaching for a web, lava, a block boost or a wall whenever one
	 * is possible cost fights, mostly by stopping to place something with the opponent in reach. Clear openings
	 * (scored at {@link Scores#SURVIVAL} or more: a webbed or eating opponent, say) are still taken at once. Held
	 * back like this, a demon beats the old, eager demon about 69% of the time; holding back water, healing or melee instead
	 * loses (32-39%), and bringing any of them forward loses too.
	 */
	/** Openings scored at least this (a webbed or eating opponent, a wall to heal behind) are never held back. */
	static final double RESTRAINT_ABOVE = Scores.SPECIALIST + 0.09;
	private boolean restrained = true;

	/** The 2.0 brain's eager utility (no {@link #restraint}): for comparing in the simulator. */
	public DuelBrain eager() {
		restrained = false;
		return this;
	}

	static double restraint(String tactic) {
		return switch (tactic) {
			case "web", "lava", "boost" -> 0.7;
			case "wall" -> 0.5;
			default -> 1.0;
		};
	}

	private Mistake pickMistake() {
		Mistake[] kinds = {Mistake.WHIFF, Mistake.EDGE_BLIND, Mistake.OVERCHASE};
		return kinds[rng.nextInt(0, kinds.length - 1)];
	}

	private String describe() {
		return "style=" + style.id() + " mistake=" + memory.mistake + " crit=" + memory.critPhase + " reactionTicks=" + memory.reactionDelayTicks + " trackTicks=" + memory.trackingDelayTicks
			+ " focusTicks=" + focusDelayTicks + " clickAt=" + String.format("%.2f", memory.cooldownThreshold) + (active == null || active.detail().isEmpty() ? "" : " step=" + active.detail());
	}
}

package io.github.flick256.sparbot.core.brain;

import java.util.Map;

/**
 * Snapshot of one decision: which tactic won, every tactic's utility score, and what the bot was
 * aiming at. Shown in the debug overlay and the {@code /sparbot info} command.
 */
public record DecisionTrace(String tactic, Map<String, Double> scores, String note, float goalYaw, float goalPitch, double targetDistance) {
	public static final DecisionTrace NONE = new DecisionTrace("none", Map.of(), "", 0, 0, Double.NaN);
}

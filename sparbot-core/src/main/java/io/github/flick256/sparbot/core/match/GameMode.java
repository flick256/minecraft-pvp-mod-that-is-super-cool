package io.github.flick256.sparbot.core.match;

import java.util.ArrayList;
import java.util.List;

/**
 * A game mode: the rules a match is played under. Defined in JSON so any mode can be added by config.
 *
 * @param kit kit both sides receive at the start of every round
 * @param roundsToWin first side to win this many rounds wins the match (best of 2n-1)
 * @param roundSeconds a round still running after this long ends by timeout
 * @param countdownSeconds fighters are held at their spawns this long before each round
 * @param timeoutRule how a timed-out round is decided: "draw" or "health" (higher health fraction wins)
 */
public record GameMode(String id, String displayName, String description, String kit, int roundsToWin, int roundSeconds, int countdownSeconds,
	String timeoutRule) {
	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (id == null || !id.matches("[a-z0-9_\\-]+")) {
			errors.add("id must be lowercase [a-z0-9_-]+, was " + id);
		}
		if (kit == null || kit.isBlank()) {
			errors.add("kit is required");
		}
		if (roundsToWin < 1 || roundsToWin > 25) {
			errors.add("roundsToWin must be 1-25");
		}
		if (roundSeconds < 10 || roundSeconds > 3600) {
			errors.add("roundSeconds must be 10-3600");
		}
		if (countdownSeconds < 0 || countdownSeconds > 30) {
			errors.add("countdownSeconds must be 0-30");
		}
		if (!"draw".equals(timeoutRule) && !"health".equals(timeoutRule)) {
			errors.add("timeoutRule must be draw or health");
		}
		return errors;
	}
}

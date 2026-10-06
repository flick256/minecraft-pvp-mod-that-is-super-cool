package io.github.flick256.sparbot.core.drill;

import java.util.List;
import java.util.Map;

/**
 * One of your fights against a bot, as the You page shows it: the mode, who you fought, how it ended,
 * your numbers and the coach's lines.
 *
 * @param result "won", "lost", "draw" or "stopped", with the score
 */
public record FightReport(String mode, String opponent, String result, long time, Map<String, String> numbers, List<String> coach) {
	/** The report for a finished fight. */
	public static FightReport of(String mode, String opponent, String result, long time, DrillTally tally) {
		return new FightReport(mode, opponent, result, time, Coach.numbers(tally), Coach.lines(tally));
	}
}

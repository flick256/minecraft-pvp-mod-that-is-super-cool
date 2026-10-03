package io.github.flick256.sparbot.core.sense;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Delays what the bot sees of its opponent. Humans react to the world as it was a reaction time ago,
 * and with ping the server state reaches the client late too; so the brain is handed the target
 * snapshot from {@code delayTicks} ago, while its own body state stays current (a client predicts
 * its own movement locally).
 */
public final class PerceptionDelay {
	private static final int MAX_DELAY_TICKS = 60;
	private final Deque<TargetState> history = new ArrayDeque<>();

	/** Records this tick's (undelayed) target snapshot, or null when there is none. */
	public void push(TargetState current) {
		history.addFirst(current == null ? NONE : current);
		while (history.size() > MAX_DELAY_TICKS + 1) {
			history.removeLast();
		}
	}

	/** The snapshot {@code delayTicks} ago, or the oldest one available. Null means no target then. */
	public TargetState delayed(int delayTicks) {
		int wanted = Math.max(0, Math.min(MAX_DELAY_TICKS, delayTicks));
		TargetState result = null;
		int i = 0;
		for (TargetState state : history) {
			result = state;
			if (i++ == wanted) {
				break;
			}
		}
		return result == NONE ? null : result;
	}

	public void clear() {
		history.clear();
	}

	// Sentinel so "no target" can be stored in an ArrayDeque (which rejects nulls).
	private static final TargetState NONE = new TargetState(-1, "", null, null, 0, 0, 0, false, 0, false, false, 0, 0, 0);
}

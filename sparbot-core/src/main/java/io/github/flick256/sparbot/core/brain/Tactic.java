package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;

/** One behaviour the utility brain can choose. Scores are in [0, 1]; the highest score wins. */
public interface Tactic {
	String name();

	double score(BrainContext context);

	Inputs act(BrainContext context);

	default void onEnter(BrainContext context) {
	}

	default void onExit(BrainContext context) {
	}

	/** Forget any per-fight state held by the tactic itself (respawn, new round). */
	default void reset() {
	}
}

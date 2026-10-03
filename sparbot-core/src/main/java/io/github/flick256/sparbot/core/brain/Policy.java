package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.sense.Observation;

/**
 * Anything that can drive a bot: the scripted utility brain today, a learned policy later. A policy
 * only maps observations to human inputs; the input shaper then enforces human limits on its output.
 */
public interface Policy {
	Inputs act(Observation observation);

	/** Why the last decision was made, for the debug overlay and logs. */
	DecisionTrace lastTrace();

	/** Forget per-fight memory (called on respawn or when a new round starts). */
	void reset();
}

package io.github.flick256.sparbot.core.brain;

/** Blunders a bot can make for one decision window. How often depends on the profile's mistakeRate. */
public enum Mistake {
	NONE,
	/** Clicks whenever ready, even when the crosshair is not on the target. */
	WHIFF,
	/** Stops checking for drops while moving, so it can walk off ledges. */
	EDGE_BLIND,
	/** Ignores spacing and keeps running into the opponent. */
	OVERCHASE
}

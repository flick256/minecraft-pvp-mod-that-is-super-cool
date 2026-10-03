package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;

/** Fallback when there is no opponent in awareness: stand still and glance around. */
public final class SearchTactic implements Tactic {
	@Override
	public String name() {
		return "search";
	}

	@Override
	public double score(BrainContext c) {
		return 0.1;
	}

	@Override
	public Inputs act(BrainContext c) {
		float yaw = c.rng.chance(0.03) ? (float) c.rng.gaussian(0, 40) : 0.0F;
		float pitch = -c.self.pitch() * 0.1F;
		return new Inputs(yaw, pitch, 0, 0, false, false, false, false, false, -1);
	}
}

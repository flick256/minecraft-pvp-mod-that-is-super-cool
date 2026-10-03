package io.github.flick256.sparbot.core.record;

import java.util.List;

/**
 * A recorded fight: who was in it and every tick of their state and inputs.
 *
 * @param mode game mode id, or empty for a free fight
 * @param dimension the world it was recorded in (positions are world coordinates)
 */
public record Recording(String label, String mode, String dimension, long startedAtMillis, List<FighterInfo> fighters, List<Frame> frames) {
	public int durationTicks() {
		return frames.size();
	}
}

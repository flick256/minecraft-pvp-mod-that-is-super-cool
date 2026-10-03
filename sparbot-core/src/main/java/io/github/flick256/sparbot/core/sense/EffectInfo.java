package io.github.flick256.sparbot.core.sense;

/** An active status effect on the bot (shown on a player's HUD). @param durationTicks -1 when infinite */
public record EffectInfo(String id, int amplifier, int durationTicks) {
}

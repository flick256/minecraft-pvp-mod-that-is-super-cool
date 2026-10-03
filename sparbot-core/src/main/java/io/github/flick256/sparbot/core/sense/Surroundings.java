package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.math.Vec3;
import java.util.List;

/**
 * Blocks and objects around the bot that block-based modes need, limited to what a player standing
 * there can see and reach. Only filled in when the bot carries something to use them with.
 *
 * @param crystals positions (bottom centre) of end crystals within the awareness radius and in sight
 * @param crystalBases obsidian or bedrock blocks within block reach with room for a crystal on top
 *     (air above and no entity in the 1x2x1 space, as EndCrystalItem#useOn requires)
 * @param obsidianSpots solid blocks within block reach whose top face can take a block of obsidian
 *     with room for a crystal above it
 */
public record Surroundings(List<Vec3> crystals, List<BlockSpot> crystalBases, List<BlockSpot> obsidianSpots) {
	public static final Surroundings EMPTY = new Surroundings(List.of(), List.of(), List.of());
}

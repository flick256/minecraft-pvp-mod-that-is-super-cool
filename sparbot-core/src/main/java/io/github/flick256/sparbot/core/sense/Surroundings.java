package io.github.flick256.sparbot.core.sense;

import io.github.flick256.sparbot.core.math.Vec3;
import java.util.List;

/**
 * Blocks and objects around the bot that block-based modes need, limited to what a player standing
 * there can see and reach. Only filled in when the bot carries something to use them with (end
 * crystals, TNT minecarts).
 *
 * @param crystals positions (bottom centre) of end crystals within the awareness radius and in sight
 * @param crystalBases obsidian or bedrock blocks within block reach with room for a crystal on top
 *     (air above and no entity in the 1x2x1 space, as EndCrystalItem#useOn requires)
 * @param groundSpots solid blocks within block reach whose top face can take a block (obsidian, a
 *     rail), with two free blocks above
 * @param tntCarts positions of TNT minecarts within the awareness radius and in sight
 * @param rails rail blocks within block reach with no minecart on them
 * @param waterSources water source blocks within block reach (filled in when the bot carries a bucket)
 * @param lavaSources lava source blocks within block reach (filled in when the bot carries a bucket)
 * @param webs cobwebs within block reach (filled in when the bot carries a bucket or blocks)
 */
public record Surroundings(List<Vec3> crystals, List<BlockSpot> crystalBases, List<BlockSpot> groundSpots, List<Vec3> tntCarts, List<BlockSpot> rails,
	List<BlockSpot> waterSources, List<BlockSpot> lavaSources, List<BlockSpot> webs) {
	public static final Surroundings EMPTY = new Surroundings(List.of(), List.of(), List.of(), List.of(), List.of());

	/** No cobwebs known. */
	public Surroundings(List<Vec3> crystals, List<BlockSpot> crystalBases, List<BlockSpot> groundSpots, List<Vec3> tntCarts, List<BlockSpot> rails,
		List<BlockSpot> waterSources, List<BlockSpot> lavaSources) {
		this(crystals, crystalBases, groundSpots, tntCarts, rails, waterSources, lavaSources, List.of());
	}

	/** No fluids known. */
	public Surroundings(List<Vec3> crystals, List<BlockSpot> crystalBases, List<BlockSpot> groundSpots, List<Vec3> tntCarts, List<BlockSpot> rails) {
		this(crystals, crystalBases, groundSpots, tntCarts, rails, List.of(), List.of());
	}

	/** Crystal PvP surroundings only. */
	public Surroundings(List<Vec3> crystals, List<BlockSpot> crystalBases, List<BlockSpot> groundSpots) {
		this(crystals, crystalBases, groundSpots, List.of(), List.of());
	}
}

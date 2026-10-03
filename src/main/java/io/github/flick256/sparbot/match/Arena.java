package io.github.flick256.sparbot.match;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * A fighting area: a box of blocks (snapshotted so it can be reset between rounds) and one spawn
 * point per side. Stored as JSON (this record) plus an NBT block snapshot.
 *
 * @param dimension dimension id, e.g. minecraft:overworld
 */
public record Arena(String id, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Spawn spawnA, Spawn spawnB) {
	public record Spawn(double x, double y, double z, float yaw) {
	}

	public BlockPos min() {
		return new BlockPos(minX, minY, minZ);
	}

	public BlockPos max() {
		return new BlockPos(maxX, maxY, maxZ);
	}

	/** The arena volume as an entity search box (full blocks). */
	public AABB box() {
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	public boolean ready() {
		return spawnA != null && spawnB != null;
	}

	public Arena withSpawn(boolean sideA, Spawn spawn) {
		return sideA ? new Arena(id, dimension, minX, minY, minZ, maxX, maxY, maxZ, spawn, spawnB)
			: new Arena(id, dimension, minX, minY, minZ, maxX, maxY, maxZ, spawnA, spawn);
	}
}

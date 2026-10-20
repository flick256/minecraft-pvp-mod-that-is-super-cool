package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.practice.PracticeLayout.Site;
import io.github.flick256.sparbot.core.practice.PracticeTerrain;
import io.github.flick256.sparbot.match.Arena;
import io.github.flick256.sparbot.match.ArenaRegistry;
import io.github.flick256.sparbot.practice.PracticeBuilderTestAccess;
import io.github.flick256.sparbot.practice.PracticeWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Covers the actual practice terrain builder and the guard around shared arena edits. */
public class TerrainGameTests {
	private static Site localSite(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(new BlockPos(16, 0, 16));
		return new Site("terrain_test", "Terrain test", center.getX(), center.getZ(), 8, 4, 8, 3, List.of("test"), 10, 1, 4, false);
	}

	@GameTest(maxTicks = 2, padding = 24)
	public void flatUnevenAndObstacleLayoutsKeepSpawnPadsSafe(GameTestHelper helper) {
		Site site = localSite(helper);
		var level = helper.getLevel();
		int floor = PracticeLayout.FLOOR;
		PracticeBuilderTestAccess.build(level, site, PracticeTerrain.FLAT);
		BlockPos flat = new BlockPos(site.centerX(), floor - 1, site.centerZ());
		helper.assertTrue(level.getBlockState(flat).is(Blocks.SANDSTONE), "flat terrain uses a level sandstone floor");

		PracticeBuilderTestAccess.build(level, site, PracticeTerrain.UNEVEN);
		int differentHeights = 0;
		int[][] heights = new int[17][17];
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				int h = PracticeBuilderTestAccess.height(site, dx, dz);
				heights[dx + 8][dz + 8] = h;
				if (h != 0) differentHeights++;
				BlockPos surface = new BlockPos(site.centerX() + dx, floor + h - 1, site.centerZ() + dz);
				helper.assertTrue(level.getBlockState(surface).is(Blocks.GRASS_BLOCK), "uneven surface follows its bounded height map");
			}
		}
		helper.assertTrue(differentHeights > 20, "uneven terrain has visible elevation changes");
		for (int dx = -8; dx < 8; dx++) {
			for (int dz = -8; dz < 8; dz++) {
				helper.assertTrue(Math.abs(heights[dx + 8][dz + 8] - heights[dx + 9][dz + 8]) <= 1, "x slopes change by at most one block");
				helper.assertTrue(Math.abs(heights[dx + 8][dz + 8] - heights[dx + 8][dz + 9]) <= 1, "z slopes change by at most one block");
			}
		}
		assertSafeSpawn(helper, site, floor);

		PracticeBuilderTestAccess.build(level, site, PracticeTerrain.OBSTACLES);
		for (int sx : new int[] {-4, 4}) {
			for (int sz : new int[] {-6, 6}) {
				for (int y = floor; y < floor + 3; y++) {
					helper.assertTrue(level.getBlockState(new BlockPos(site.centerX() + sx, y, site.centerZ() + sz)).is(Blocks.COBBLESTONE),
						"obstacle cover stands on the flat floor");
				}
			}
		}
		assertSafeSpawn(helper, site, floor);
		helper.succeed();
	}

	private static void assertSafeSpawn(GameTestHelper helper, Site site, int floor) {
		for (int z : new int[] {-site.spawnOffset(), site.spawnOffset()}) {
			BlockPos ground = new BlockPos(site.centerX(), floor - 1, site.centerZ() + z);
			helper.assertTrue(helper.getLevel().getBlockState(ground).is(Blocks.GRASS_BLOCK), "spawn ground remains flat grass");
			helper.assertTrue(helper.getLevel().getBlockState(ground.above()).isAir(), "spawn has headroom");
			helper.assertTrue(helper.getLevel().getBlockState(ground.above(2)).isAir(), "spawn has two blocks of headroom");
		}
	}

	@GameTest(maxTicks = 2, padding = 24)
	public void terrainChangesAreBlockedWhileAnotherPlayerIsNearCourt(GameTestHelper helper) {
		var center = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 4.5));
		Arena court = new Arena("practice_test", helper.getLevel().dimension().identifier().toString(),
			(int) center.x - 2, (int) center.y - 1, (int) center.z - 2, (int) center.x + 2, (int) center.y + 3,
			(int) center.z + 2, new Arena.Spawn(center.x, center.y + 1, center.z - 1, 0),
			new Arena.Spawn(center.x, center.y + 1, center.z + 1, 180));
		var nearby = TestSupport.spawnRealPlayer(helper, 5.5, 1, 4.5);
		try {
			helper.assertTrue(PracticeWorld.hasNearbyPlayers(court, helper.getLevel().players(), null), "nearby players prevent shared terrain replacement");
			helper.assertFalse(PracticeWorld.hasNearbyPlayers(court, helper.getLevel().players(), nearby), "the caller may be excluded after it is moved");
		} finally {
			TestSupport.removeRealPlayer(nearby);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 2, padding = 24)
	public void arenaResetRestoresTheSelectedObstacleSnapshot(GameTestHelper helper) {
		Site site = localSite(helper);
		PracticeBuilderTestAccess.build(helper.getLevel(), site, PracticeTerrain.OBSTACLES);
		BlockPos pillar = new BlockPos(site.centerX() - 4, PracticeLayout.FLOOR, site.centerZ() - 6);
		BlockPos min = new BlockPos(site.centerX() - site.radius() - 2, PracticeLayout.FLOOR - site.depth(), site.centerZ() - site.radius() - 2);
		BlockPos max = new BlockPos(site.centerX() + site.radius() + 2, PracticeLayout.FLOOR + site.height(), site.centerZ() + site.radius() + 2);
		Path dir = Path.of(System.getProperty("java.io.tmpdir"), "sparbot-terrain-test-" + UUID.randomUUID());
		try {
			ArenaRegistry registry = new ArenaRegistry();
			registry.reload(dir, helper.getLevel().getServer());
			Arena arena = registry.create("terrain_test", helper.getLevel(), min, max);
			helper.getLevel().setBlock(pillar, Blocks.AIR.defaultBlockState(), 3);
			registry.reset(helper.getLevel().getServer(), arena);
			helper.assertTrue(helper.getLevel().getBlockState(pillar).is(Blocks.COBBLESTONE), "reset restored the chosen obstacle layout");
		} catch (Exception e) {
			throw new AssertionError("could not exercise terrain snapshot reset", e);
		} finally {
			try (var files = Files.walk(dir)) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
			} catch (Exception ignored) { }
		}
		helper.succeed();
	}
}

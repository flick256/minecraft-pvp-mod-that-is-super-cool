package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.core.practice.PracticeLayout.Site;
import io.github.flick256.sparbot.core.practice.PracticeTerrain;
import net.minecraft.server.level.ServerLevel;

/** Small bridge so GameTests can exercise the package-private practice arena builder directly. */
public final class PracticeBuilderTestAccess {
	private PracticeBuilderTestAccess() { }

	public static void build(ServerLevel level, Site site, PracticeTerrain terrain) {
		new PracticeBuilder(level).buildCombatTerrain(site, terrain);
	}

	public static int height(Site site, int dx, int dz) {
		return PracticeBuilder.terrainHeight(site, dx, dz);
	}
}

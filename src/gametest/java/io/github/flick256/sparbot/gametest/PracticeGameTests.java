package io.github.flick256.sparbot.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.flick256.sparbot.practice.PracticeWorld;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The practice world's data: its dimension type is loaded, and the dimension and the world type decode
 * against the game's own registries. (The GameTest server only makes the vanilla dimensions, so the world
 * itself is built and fought in by the client GameTest, in a real singleplayer world.)
 */
public class PracticeGameTests {
	private static JsonElement json(String path) {
		try (var in = PracticeGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	@GameTest
	public void thePracticeDimensionAndWorldTypeDecode(GameTestHelper helper) {
		var access = helper.getLevel().getServer().registryAccess();
		helper.assertTrue(access.lookupOrThrow(Registries.DIMENSION_TYPE).get(PracticeWorld.TYPE).isPresent(), "the practice dimension type is loaded");
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
		LevelStem stem = LevelStem.CODEC.parse(ops, json("data/sparbot/dimension/practice.json")).getOrThrow();
		helper.assertTrue(stem.type().is(PracticeWorld.TYPE), "the dimension uses the practice type");
		WorldPreset preset = WorldPreset.DIRECT_CODEC.parse(ops, json("data/sparbot/worldgen/world_preset/practice.json")).getOrThrow();
		helper.assertTrue(preset.createWorldDimensions().get(LevelStem.OVERWORLD).map(s -> s.type().is(PracticeWorld.TYPE)).orElse(false),
			"the SparBot Practice world type's overworld is the practice desert");
		helper.succeed();
	}
}

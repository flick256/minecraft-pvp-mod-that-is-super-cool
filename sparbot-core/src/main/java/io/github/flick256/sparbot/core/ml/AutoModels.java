package io.github.flick256.sparbot.core.ml;

import java.util.Collection;

/**
 * Which learned brain a bot gets when nobody chose one (the {@code autoModels} setting). UHC bots get the
 * learned UHC brain held to their own tier's limits: in the simulator it beats the scripted bot of the
 * same tier about 9 times in 10 (advanced 93%, pro 91%) with as much lava, web and water play, so every
 * tier plays stronger without any tier's limits changing. Demon bots get the brain trained against the
 * scripted demon when it is there. Other kits keep the scripted brain.
 */
public final class AutoModels {
	public static final String UHC = "uhc";
	public static final String UHC_DEMON = "uhc_demon";

	private AutoModels() {
	}

	/** The model id for a bot with a kit of {@code kitMode} at {@code profileId}, or null for the scripted brain. */
	public static String choose(String kitMode, String profileId, Collection<String> available) {
		if (!"uhc".equals(kitMode)) {
			return null;
		}
		if ("demon".equals(profileId) && available.contains(UHC_DEMON)) {
			return UHC_DEMON;
		}
		return available.contains(UHC) ? UHC : null;
	}
}

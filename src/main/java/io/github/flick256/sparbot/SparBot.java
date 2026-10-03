package io.github.flick256.sparbot;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SparBot implements ModInitializer {
	public static final String MOD_ID = "sparbot";
	public static final Logger LOGGER = LoggerFactory.getLogger("SparBot");

	@Override
	public void onInitialize() {
		LOGGER.info("SparBot initialising");
	}
}

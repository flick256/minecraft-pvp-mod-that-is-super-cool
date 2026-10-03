package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.act.Inputs;

/**
 * GameTest-only bridge into package-private bot internals (lives in the same package, in the
 * gametest source set). Lets tests drive the client emulator with exact inputs.
 */
public final class BotTestAccess {
	private BotTestAccess() {
	}

	/** Sends one tick of inputs through the same client emulation the brain's inputs go through. */
	public static void press(Bot bot, Inputs inputs) {
		bot.client().apply(bot.body(), bot, inputs);
	}

	/** Stops the brain so a test can drive the bot by hand. */
	public static void pauseBrain(Bot bot, boolean paused) {
		bot.setBrainPaused(paused);
	}
}

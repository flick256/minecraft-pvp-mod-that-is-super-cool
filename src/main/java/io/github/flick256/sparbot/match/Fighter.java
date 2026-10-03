package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.core.stats.EloLadder;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** One side of a match: a bot or a human player. */
public record Fighter(String name, UUID uuid, @Nullable Bot bot) {
	public boolean isBot() {
		return bot != null;
	}

	/** The fighter's current player entity (a new one after every respawn), or null when offline. */
	public @Nullable ServerPlayer resolve(MinecraftServer server) {
		return server.getPlayerList().getPlayer(uuid);
	}

	/** Ladder key: bots are rated per skill profile + playstyle, humans per name. */
	public String eloKey() {
		return bot != null ? EloLadder.botKey(bot.profile().id(), bot.playstyle().id()) : EloLadder.playerKey(name);
	}
}

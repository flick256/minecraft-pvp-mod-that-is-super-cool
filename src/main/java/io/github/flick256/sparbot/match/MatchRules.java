package io.github.flick256.sparbot.match;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Rules a game mode applies to its two fighters only, so a match never changes the game for anyone
 * else on the server (26.2 game rules are server-wide). Both fighters, human or bot, get exactly the
 * same rules.
 */
public final class MatchRules {
	/** Fighters in a match without natural regeneration (UHC). */
	private static final Set<UUID> NO_REGENERATION = ConcurrentHashMap.newKeySet();

	private MatchRules() {
	}

	/** Switches natural regeneration off (or back on) for one player: a match's fighters, or a test's. */
	public static void withoutRegeneration(UUID player, boolean on) {
		if (on) {
			NO_REGENERATION.add(player);
		} else {
			NO_REGENERATION.remove(player);
		}
	}

	/**
	 * Whether natural regeneration applies to {@code player} (FoodDataMixin): a match's rule for its
	 * fighters, the naturalRegeneration setting for everyone else.
	 */
	public static boolean regenerates(Player player) {
		if (NO_REGENERATION.contains(player.getUUID())) {
			return false;
		}
		io.github.flick256.sparbot.config.SparBotConfig config = io.github.flick256.sparbot.SparBot.config();
		return config == null || config.naturalRegeneration || io.github.flick256.sparbot.SparBot.matches().inMatch(player.getUUID());
	}
}

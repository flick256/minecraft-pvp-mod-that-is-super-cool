package io.github.flick256.sparbot.bot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

/**
 * Hard rule: a bot is a fully mortal survival player. This checks every tick that nothing (another
 * mod, an operator command, a bug) has given the bot protection a sparring partner must not have.
 * Any violation removes the bot instead of letting an unfair fight continue.
 */
public final class MortalityGuard {
	private MortalityGuard() {
	}

	/** Returns every violated rule; empty when the bot is a plain mortal player. */
	public static List<String> violations(BotPlayer bot) {
		List<String> problems = new ArrayList<>();
		Abilities abilities = bot.getAbilities();
		if (abilities.invulnerable) {
			problems.add("abilities.invulnerable is set");
		}
		if (abilities.mayfly || abilities.flying) {
			problems.add("flight ability is set");
		}
		if (abilities.instabuild) {
			problems.add("instabuild (creative) ability is set");
		}
		GameType mode = bot.gameMode();
		if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) {
			problems.add("game mode is " + mode.getName());
		}
		if (bot.isInvulnerable()) {
			problems.add("entity invulnerable flag is set");
		}
		if (bot.isAlive() && !bot.connection.hasClientLoaded()) {
			// A "not loaded" player is immune to damage (ServerPlayer#isInvulnerableTo). Bots load instantly.
			problems.add("client-not-loaded protection is active");
		}
		return problems;
	}
}

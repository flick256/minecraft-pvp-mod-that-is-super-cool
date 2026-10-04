package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.brain.DecisionTrace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Watches a bot-vs-bot fight tick by tick: logs every change of tactic, and measures the longest
 * stretch a bot spent frozen (no movement keys, no click, no right click, not moving) while its
 * opponent was alive and close. That is the "locks up until you hit it" bug, caught without a human.
 */
final class FightDiagnostics {
	/** A bot standing still for longer than this, with its opponent close, has locked up. */
	static final int STALL_LIMIT = 30;
	private static final double CLOSE = 10.0;

	private final Bot bot;
	private final Bot opponent;
	private final List<String> timeline = new ArrayList<>();
	private String lastTactic = "";
	private Vec3 lastPosition;
	private int stall;
	private int longestStall;
	private String longestStallAt = "";
	private final java.util.Map<String, Integer> ticksPerTactic = new java.util.TreeMap<>();

	FightDiagnostics(Bot bot, Bot opponent) {
		this.bot = bot;
		this.opponent = opponent;
	}

	void tick(GameTestHelper helper) {
		BotPlayer self = bot.body();
		BotPlayer other = opponent.body();
		if (self == null || other == null || !self.isAlive() || !other.isAlive()) {
			stall = 0;
			return;
		}
		long tick = helper.getTick();
		DecisionTrace trace = bot.trace();
		String tactic = trace.tactic() + (trace.note().contains("step=") ? "/" + trace.note().substring(trace.note().indexOf("step=") + 5) : "");
		ticksPerTactic.merge(trace.tactic(), 1, Integer::sum);
		if (!tactic.equals(lastTactic)) {
			timeline.add(String.format("%4d %-18s hp=%4.1f d=%4.1f", tick, tactic, self.getHealth(), self.distanceTo(other)));
			lastTactic = tactic;
		}
		Inputs in = bot.lastInputs();
		Vec3 pos = self.position();
		boolean moved = lastPosition != null && pos.distanceTo(lastPosition) > 0.01;
		lastPosition = pos;
		boolean idle = in.forward() == 0 && in.strafe() == 0 && !in.attack() && !in.use() && !in.jump() && !moved;
		if (idle && self.distanceTo(other) < CLOSE) {
			if (++stall > longestStall) {
				longestStall = stall;
				longestStallAt = "tick " + tick + " in " + tactic;
			}
		} else {
			stall = 0;
		}
	}

	int longestStall() {
		return longestStall;
	}

	String report() {
		StringBuilder sb = new StringBuilder();
		sb.append(bot.name()).append(" (").append(bot.profile().id()).append(", ").append(bot.kit().id()).append("): longest stall ").append(longestStall)
			.append(" ticks").append(longestStall > 0 ? " ending " + longestStallAt : "").append("; ticks per tactic ").append(ticksPerTactic)
			.append("; ").append(bot.stats().summary());
		for (String line : timeline) {
			sb.append("\n    ").append(line);
		}
		return sb.toString();
	}

	void log() {
		SparBot.LOGGER.info("Fight diagnostics: {}", report());
	}
}

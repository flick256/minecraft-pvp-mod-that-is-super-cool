package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.match.MatchState;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.style.Playstyle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plays a series of bot-vs-bot matches between two configurations in one arena and feeds every result
 * into the Elo ladder, to measure how strong each skill level / playstyle really is.
 */
public final class Benchmark {
	private final Arena arena;
	private final GameMode mode;
	private final SkillProfile profileA;
	private final Playstyle styleA;
	private final SkillProfile profileB;
	private final Playstyle styleB;
	private final int total;
	private final @Nullable CommandSourceStack reportTo;
	private int played;
	private int winsA;
	private int winsB;
	private boolean matchRunning;
	private Bot botA;
	private Bot botB;

	public Benchmark(Arena arena, GameMode mode, SkillProfile profileA, Playstyle styleA, SkillProfile profileB, Playstyle styleB, int total,
		@Nullable CommandSourceStack reportTo) {
		this.arena = arena;
		this.mode = mode;
		this.profileA = profileA;
		this.styleA = styleA;
		this.profileB = profileB;
		this.styleB = styleB;
		this.total = total;
		this.reportTo = reportTo;
	}

	/** Returns true when the series is over. */
	boolean tick(MinecraftServer server, MatchManager manager) {
		if (matchRunning) {
			return false;
		}
		if (played >= total) {
			finish(manager);
			return true;
		}
		try {
			ensureBots(server, manager);
			matchRunning = true;
			manager.start(server, mode, arena, MatchManager.fighterFor(botA), MatchManager.fighterFor(botB), this::onMatchOver);
		} catch (Exception e) {
			SparBot.LOGGER.error("Benchmark in {} aborted: {}", arena.id(), e.getMessage());
			report("Benchmark aborted: " + e.getMessage());
			cleanup();
			return true;
		}
		return false;
	}

	private void ensureBots(MinecraftServer server, MatchManager manager) {
		if (botA != null && SparBot.bots().get(botA.name()).isPresent() && botB != null && SparBot.bots().get(botB.name()).isPresent()) {
			return;
		}
		ServerLevel level = manager.arenas().level(server, arena);
		String tag = Integer.toString(Math.abs(arena.id().hashCode()) % 1000);
		botA = SparBot.bots().spawn(server, "BenchA" + tag, level, new Vec3(arena.spawnA().x(), arena.spawnA().y(), arena.spawnA().z()),
			arena.spawnA().yaw(), profileA, SparBot.kits().get(mode.kit()).orElseThrow());
		botA.setPlaystyle(styleA);
		botB = SparBot.bots().spawn(server, "BenchB" + tag, level, new Vec3(arena.spawnB().x(), arena.spawnB().y(), arena.spawnB().z()),
			arena.spawnB().yaw(), profileB, SparBot.kits().get(mode.kit()).orElseThrow());
		botB.setPlaystyle(styleB);
	}

	private void onMatchOver(Match match) {
		matchRunning = false;
		played++;
		if (match.state().winner() == MatchState.Side.A) {
			winsA++;
		} else if (match.state().winner() == MatchState.Side.B) {
			winsB++;
		}
		if (played % 5 == 0 && played < total) {
			report(progress());
		}
	}

	private void finish(MatchManager manager) {
		var ladder = manager.elo().ladder();
		String a = io.github.flick256.sparbot.core.stats.EloLadder.botKey(profileA.id(), styleA.id());
		String b = io.github.flick256.sparbot.core.stats.EloLadder.botKey(profileB.id(), styleB.id());
		report(progress() + String.format(" | Elo %s %.0f, %s %.0f", a, ladder.get(a).rating, b, ladder.get(b).rating));
		cleanup();
	}

	private String progress() {
		return String.format("Benchmark %s vs %s: %d/%d played, %d-%d", label(profileA, styleA), label(profileB, styleB), played, total, winsA, winsB);
	}

	private static String label(SkillProfile p, Playstyle s) {
		return p.id() + "/" + s.id();
	}

	private void cleanup() {
		for (Bot bot : new Bot[] {botA, botB}) {
			if (bot != null) {
				SparBot.bots().get(bot.name()).ifPresent(b -> SparBot.bots().remove(b, "benchmark finished"));
			}
		}
	}

	private void report(String message) {
		SparBot.LOGGER.info(message);
		if (reportTo != null) {
			reportTo.sendSuccess(() -> Component.literal(message), false);
		}
	}

	public int played() {
		return played;
	}

	public int winsA() {
		return winsA;
	}

	public int winsB() {
		return winsB;
	}

	public Arena arena() {
		return arena;
	}
}

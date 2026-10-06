package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.match.GameMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runs matches (one per arena) and benchmarks; owns arenas, modes, the Elo ladder and player backups. */
public final class MatchManager {
	private final ArenaRegistry arenas = new ArenaRegistry();
	private final GameModeRegistry modes = new GameModeRegistry();
	private final EloStore elo = new EloStore();
	private PlayerBackup backups;
	private final Map<String, Match> running = new LinkedHashMap<>();
	private final List<Benchmark> benchmarks = new ArrayList<>();
	private final Map<String, Consumer<Match>> onFinish = new LinkedHashMap<>();
	/** Humans whose match ended while they were dead: restored as soon as they have respawned. */
	private final java.util.Set<java.util.UUID> pendingRestore = new java.util.HashSet<>();

	public void reload(MinecraftServer server, java.nio.file.Path configDir) {
		arenas.reload(configDir.resolve("arenas"), server);
		modes.reload(configDir.resolve("modes"));
		elo.load(configDir.resolve("elo.json"));
		backups = new PlayerBackup(configDir.resolve("match-backups"));
	}

	/**
	 * Starts a match. Humans' own inventories are backed up first (the kit replaces them for the match).
	 * {@code finished} (may be null) is called with the match when it ends.
	 */
	public Match start(MinecraftServer server, GameMode mode, Arena arena, Fighter a, Fighter b, Consumer<Match> finished) throws IOException {
		if (!arena.ready()) {
			throw new IllegalArgumentException("Arena " + arena.id() + " needs both spawns: /sparbot arena spawn " + arena.id() + " a|b");
		}
		if (running.containsKey(arena.id())) {
			throw new IllegalArgumentException("Arena " + arena.id() + " is already in use");
		}
		if (a.uuid().equals(b.uuid())) {
			throw new IllegalArgumentException("A fighter cannot fight itself");
		}
		for (Fighter f : new Fighter[] {a, b}) {
			if (inMatch(f.uuid())) {
				throw new IllegalArgumentException(f.name() + " is already in a match");
			}
			if (f.resolve(server) == null) {
				throw new IllegalArgumentException(f.name() + " is not online");
			}
		}
		Kit kit = SparBot.kits().get(mode.kit()).orElseThrow(() -> new IllegalArgumentException("Mode " + mode.id() + " uses unknown kit " + mode.kit()));
		for (Fighter f : new Fighter[] {a, b}) {
			if (!f.isBot()) {
				ServerPlayer player = f.resolve(server);
				if (backups.has(player.getUUID())) {
					throw new IllegalArgumentException(f.name() + " still has an unrestored match backup; rejoin first");
				}
				backups.save(player);
			} else {
				f.bot().setInMatch(true);
			}
		}
		Match match = new Match(arena, mode, kit, a, b, this);
		running.put(arena.id(), match);
		watch(server, match);
		if (finished != null) {
			onFinish.put(arena.id(), finished);
		}
		SparBot.LOGGER.info("Match started in {}: {} vs {} ({})", arena.id(), a.name(), b.name(), mode.id());
		return match;
	}

	public void tick(MinecraftServer server) {
		for (Match match : new ArrayList<>(running.values())) {
			if (match.tick(server)) {
				running.remove(match.arena().id());
				report(server, match, false);
				Consumer<Match> callback = onFinish.remove(match.arena().id());
				if (callback != null) {
					callback.accept(match);
				}
			}
		}
		pendingRestore.removeIf(uuid -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				return true; // restored on their next join instead
			}
			return player.isAlive() && backups.restore(player);
		});
		for (Benchmark benchmark : new ArrayList<>(benchmarks)) {
			if (benchmark.tick(server, this)) {
				benchmarks.remove(benchmark);
			}
		}
	}

	/** Ends a match early as a draw-less stop: nobody is rated, humans get their inventory back. */
	public boolean stop(MinecraftServer server, String arenaId) {
		Match match = running.remove(arenaId);
		if (match == null) {
			return false;
		}
		onFinish.remove(arenaId);
		report(server, match, true);
		for (Fighter f : new Fighter[] {match.a(), match.b()}) {
			ServerPlayer player = f.resolve(server);
			if (f.isBot()) {
				f.bot().setInMatch(false);
				f.bot().setBrainPaused(false);
				f.bot().setAssignedTarget(null);
			} else {
				restoreWhenAlive(player, f);
			}
		}
		arenas.reset(server, match.arena());
		return true;
	}

	/** A human against a bot: their fight is measured for the report and coach lines at the end. */
	private static void watch(MinecraftServer server, Match match) {
		Fighter human = !match.a().isBot() ? match.a() : !match.b().isBot() ? match.b() : null;
		Fighter bot = match.a().isBot() ? match.a() : match.b().isBot() ? match.b() : null;
		ServerPlayer player = human == null ? null : human.resolve(server);
		if (player != null && bot != null) {
			SparBot.drills().watchMatch(player, bot.bot());
		}
	}

	private static void report(MinecraftServer server, Match match, boolean stopped) {
		Fighter human = !match.a().isBot() ? match.a() : !match.b().isBot() ? match.b() : null;
		Fighter bot = match.a().isBot() ? match.a() : match.b().isBot() ? match.b() : null;
		if (human == null || bot == null) {
			return;
		}
		io.github.flick256.sparbot.core.match.MatchState.Side mine = human == match.a() ? io.github.flick256.sparbot.core.match.MatchState.Side.A
			: io.github.flick256.sparbot.core.match.MatchState.Side.B;
		var winner = match.state().winner();
		String score = match.state().score();
		String result = stopped ? "stopped at " + score : winner == null ? "draw " + score : winner == mine ? "won " + score : "lost " + score;
		SparBot.drills().endMatch(server, human.uuid(), match.state().mode().id(), bot.bot().name() + " (" + bot.bot().profile().id() + ")", result);
	}

	/** Gives a human their own inventory back now, or as soon as they respawn. */
	void restoreWhenAlive(net.minecraft.server.level.ServerPlayer player, Fighter fighter) {
		if (player != null && player.isAlive()) {
			backups.restore(player);
		} else {
			pendingRestore.add(fighter.uuid());
		}
	}

	/** Stops every match (server shutdown): humans get their inventory back, nobody is rated. */
	public void stopAll(MinecraftServer server) {
		benchmarks.clear();
		for (String arenaId : new ArrayList<>(running.keySet())) {
			stop(server, arenaId);
		}
	}

	/** A human who joins with a leftover backup (server stopped mid-match) gets their inventory back. */
	public void onJoin(ServerPlayer player) {
		if (!inMatch(player.getUUID()) && backups.restore(player)) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[SparBot] Your inventory from before the interrupted match was restored."));
		}
	}

	public boolean inMatch(java.util.UUID uuid) {
		return running.values().stream().anyMatch(m -> m.involves(uuid));
	}

	public void addBenchmark(Benchmark benchmark) {
		benchmarks.add(benchmark);
	}

	public Optional<Match> match(String arenaId) {
		return Optional.ofNullable(running.get(arenaId));
	}

	public Collection<Match> matches() {
		return running.values();
	}

	public Collection<Benchmark> benchmarks() {
		return benchmarks;
	}

	public ArenaRegistry arenas() {
		return arenas;
	}

	public GameModeRegistry modes() {
		return modes;
	}

	public EloStore elo() {
		return elo;
	}

	public PlayerBackup backups() {
		return backups;
	}

	public static Fighter fighterFor(Bot bot) {
		return new Fighter(bot.name(), bot.uuid(), bot);
	}

	public static Fighter fighterFor(ServerPlayer player) {
		return new Fighter(player.getPlainTextName(), player.getUUID(), null);
	}
}

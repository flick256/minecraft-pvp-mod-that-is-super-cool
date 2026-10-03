package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotManager;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.match.MatchState;
import io.github.flick256.sparbot.kit.KitApplier;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import org.jspecify.annotations.Nullable;

/**
 * One running match in one arena: drives the {@link MatchState} rules with what happens in the world.
 * Every round starts the same way for both sides: arena reset, teleport to spawn, full kit reset,
 * a countdown hold, then the fight. A death (or falling out of the arena) ends the round.
 */
public final class Match {
	private final Arena arena;
	private final GameMode mode;
	private final Kit kit;
	private final Fighter a;
	private final Fighter b;
	private final MatchState state;
	private final MatchManager manager;
	private boolean roundPrepared;
	private MatchState.Event result = MatchState.Event.NONE;
	private final long startedAt = System.currentTimeMillis();
	private boolean recording;

	Match(Arena arena, GameMode mode, Kit kit, Fighter a, Fighter b, MatchManager manager) {
		this.arena = arena;
		this.mode = mode;
		this.kit = kit;
		this.a = a;
		this.b = b;
		this.state = new MatchState(mode);
		this.manager = manager;
		MatchRules.withoutRegeneration(a.uuid(), !mode.regenerates());
		MatchRules.withoutRegeneration(b.uuid(), !mode.regenerates());
	}

	/** The recording of this match, while {@code recordMatches} is on. */
	private String recordingLabel() {
		return ("match-" + arena.id() + "-" + startedAt).toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
	}

	/** Returns true once the match has finished and cleaned up. */
	boolean tick(MinecraftServer server) {
		ServerPlayer pa = a.resolve(server);
		ServerPlayer pb = b.resolve(server);
		if (pa == null || pb == null) {
			finish(server, state.forfeit(pa == null ? MatchState.Side.A : MatchState.Side.B), pa, pb);
			return true;
		}

		MatchState.Phase before = state.phase();
		MatchState.Event event = MatchState.Event.NONE;
		switch (before) {
			case COUNTDOWN -> {
				if (!roundPrepared) {
					prepareRound(server, pa, pb);
					roundPrepared = true;
				} else {
					hold(pa, arena.spawnA());
					hold(pb, arena.spawnB());
				}
			}
			case FIGHTING -> {
				boolean downA = isDown(pa);
				boolean downB = isDown(pb);
				if (downA && downB) {
					event = state.onDoubleDeath();
				} else if (downA) {
					event = state.onDeath(MatchState.Side.A);
				} else if (downB) {
					event = state.onDeath(MatchState.Side.B);
				}
			}
			case ROUND_OVER -> {
				respawnIfBot(server, a, pa);
				respawnIfBot(server, b, pb);
			}
			case FINISHED -> {
			}
		}
		if (event == MatchState.Event.NONE && state.phase() != MatchState.Phase.FINISHED) {
			pa = a.resolve(server);
			pb = b.resolve(server);
			boolean ready = pa != null && pb != null && pa.isAlive() && pb.isAlive();
			event = state.tick(health(pa), health(pb), ready);
		}
		handle(server, event);
		if (before == MatchState.Phase.ROUND_OVER && state.phase() == MatchState.Phase.COUNTDOWN) {
			roundPrepared = false;
		}
		if (state.phase() == MatchState.Phase.FINISHED) {
			finish(server, event, a.resolve(server), b.resolve(server));
			return true;
		}
		return false;
	}

	private void handle(MinecraftServer server, MatchState.Event event) {
		switch (event) {
			case ROUND_STARTED -> {
				announce(server, Component.literal("Fight!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				release(a, b);
				release(b, a);
			}
			case ROUND_WON_A, ROUND_WON_B -> {
				String winner = event == MatchState.Event.ROUND_WON_A ? a.name() : b.name();
				announce(server, Component.literal("Round " + state.round() + " to " + winner + " (" + a.name() + " " + state.score() + " " + b.name() + ")"));
				pauseBots();
			}
			case ROUND_DRAWN -> {
				announce(server, Component.literal("Round " + state.round() + " drawn (" + a.name() + " " + state.score() + " " + b.name() + ")"));
				pauseBots();
			}
			default -> {
			}
		}
	}

	private void prepareRound(MinecraftServer server, ServerPlayer pa, ServerPlayer pb) {
		manager.arenas().reset(server, arena);
		if (!recording && SparBot.config().recordMatches) {
			try {
				SparBot.recorder().start(recordingLabel(), mode.id(), List.of(pa, pb));
				recording = true;
			} catch (IllegalArgumentException | IllegalStateException e) {
				SparBot.LOGGER.warn("Could not record the match in {}: {}", arena.id(), e.getMessage());
			}
		}
		ServerLevel level = manager.arenas().level(server, arena);
		if (level == null) {
			return;
		}
		place(level, pa, arena.spawnA(), a);
		place(level, pb, arena.spawnB(), b);
		announce(server, Component.literal("Round " + state.round() + ": " + a.name() + " vs " + b.name() + " (" + mode.displayName() + ", "
			+ a.name() + " " + state.score() + " " + b.name() + ")").withStyle(ChatFormatting.GOLD));
	}

	/** Teleport to the spawn facing the arena, full kit reset (a new round for both sides alike). */
	private void place(ServerLevel level, ServerPlayer player, Arena.Spawn spawn, Fighter fighter) {
		player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), 0, true);
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		KitApplier.apply(player, kit);
		if (fighter.isBot()) {
			Bot bot = fighter.bot();
			bot.onKitReset();
			bot.setBrainPaused(true);
		}
	}

	/** During the countdown fighters stay on their spawn (they may look around). */
	private static void hold(ServerPlayer player, Arena.Spawn spawn) {
		if (player.position().distanceToSqr(spawn.x(), spawn.y(), spawn.z()) > 0.25) {
			player.teleportTo(player.level(), spawn.x(), spawn.y(), spawn.z(), Relative.ROTATION, 0, 0, true);
		}
	}

	private static void release(Fighter self, Fighter opponent) {
		if (self.isBot()) {
			self.bot().resetMindForRound();
			self.bot().setAssignedTarget(opponent.uuid());
			self.bot().setBrainPaused(false);
		}
	}

	private void pauseBots() {
		if (a.isBot()) {
			a.bot().setBrainPaused(true);
		}
		if (b.isBot()) {
			b.bot().setBrainPaused(true);
		}
	}

	private boolean isDown(ServerPlayer player) {
		// Dead, or fell out of the arena (ring-out).
		return player.isDeadOrDying() || player.getY() < arena.minY() - 2;
	}

	private static void respawnIfBot(MinecraftServer server, Fighter fighter, @Nullable ServerPlayer player) {
		if (fighter.isBot() && player != null && player.isDeadOrDying()) {
			SparBot.bots().respawn(server, fighter.bot());
		}
	}

	private static double health(@Nullable ServerPlayer player) {
		return player == null || player.isDeadOrDying() ? 0 : player.getHealth() / player.getMaxHealth();
	}

	private void finish(MinecraftServer server, MatchState.Event event, @Nullable ServerPlayer pa, @Nullable ServerPlayer pb) {
		result = event;
		MatchState.Side winner = state.winner();
		if (winner != null) {
			Fighter w = winner == MatchState.Side.A ? a : b;
			announce(server, Component.literal(w.name() + " wins " + a.name() + " " + state.score() + " " + b.name()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			manager.elo().ladder().record(a.eloKey(), b.eloKey(), winner == MatchState.Side.A ? 1 : 0);
			manager.elo().save();
		}
		for (Fighter fighter : new Fighter[] {a, b}) {
			MatchRules.withoutRegeneration(fighter.uuid(), false);
			ServerPlayer player = fighter == a ? pa : pb;
			if (fighter.isBot()) {
				Bot bot = fighter.bot();
				bot.setInMatch(false);
				bot.setBrainPaused(false);
				bot.setAssignedTarget(null);
				if (player != null && player.isDeadOrDying()) {
					SparBot.bots().respawn(server, bot);
				}
			} else {
				manager.restoreWhenAlive(player, fighter);
			}
		}
		manager.arenas().reset(server, arena);
		if (recording) {
			try {
				SparBot.recorder().stop(recordingLabel());
			} catch (IOException | IllegalStateException e) {
				SparBot.LOGGER.error("Could not save the recording of the match in {}", arena.id(), e);
			}
		}
		SparBot.LOGGER.info("Match in arena {} finished: {} {} {} ({})", arena.id(), a.name(), state.score(), b.name(), event);
	}

	private void announce(MinecraftServer server, Component message) {
		for (Fighter fighter : new Fighter[] {a, b}) {
			ServerPlayer player = fighter.resolve(server);
			if (player != null && !fighter.isBot()) {
				player.sendSystemMessage(Component.literal("[SparBot] ").withStyle(ChatFormatting.DARK_AQUA).append(message));
			}
		}
		SparBot.LOGGER.info("[{}] {}", arena.id(), message.getString());
	}

	public Arena arena() {
		return arena;
	}

	public MatchState state() {
		return state;
	}

	public Fighter a() {
		return a;
	}

	public Fighter b() {
		return b;
	}

	public MatchState.Event result() {
		return result;
	}

	public boolean involves(java.util.UUID uuid) {
		return a.uuid().equals(uuid) || b.uuid().equals(uuid);
	}
}

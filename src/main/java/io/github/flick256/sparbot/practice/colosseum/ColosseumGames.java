package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.kit.KitApplier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Games in the Grand Bowl, you against bots:
 * <ul>
 * <li><b>Waves:</b> two beginners, then three intermediates, then a pro with two advanced bots, then a
 * Demon with two pros; your kit and health come back between waves; how far you get is your score.</li>
 * <li><b>King of the hill:</b> the compass rose's heart is the hill; a second there with nobody else on it
 * scores one; first to sixty wins. The bots fight you for it and get up again five seconds after dying.</li>
 * </ul>
 * One game at a time; your inventory is kept safe as in a match and comes back at the end.
 */
public final class ColosseumGames {
	public enum Kind {
		WAVES,
		HILL
	}

	/** Each wave's bots, by tier. */
	private static final String[][] WAVES = {{"beginner", "beginner"}, {"intermediate", "intermediate", "intermediate"}, {"pro", "advanced", "advanced"},
		{"demon", "pro", "pro"}};
	private static final int HILL_RADIUS = 5;
	private static final int HILL_TARGET = 60;
	private static final int HILL_BOTS = 3;
	private static final int RESPAWN_TICKS = 100;
	private static final int BREAK_TICKS = 100;

	private @Nullable Game game;

	private static final class Game {
		final Kind kind;
		final UUID player;
		final String kitId;
		final String profileId;
		final List<String> bots = new ArrayList<>();
		final Map<String, Integer> score = new HashMap<>();
		final Map<String, Integer> down = new HashMap<>();
		int wave;
		int breakTicks;
		int ticks;

		Game(Kind kind, UUID player, String kitId, String profileId) {
			this.kind = kind;
			this.player = player;
			this.kitId = kitId;
			this.profileId = profileId;
		}
	}

	public boolean running() {
		return game != null;
	}

	/** Starts a game for the player in the Grand Bowl ({@code profile}: the hill's bots' tier; waves set their own). */
	public String start(ServerPlayer player, Kind kind, String kitId, String profileId) throws IOException {
		MinecraftServer server = player.level().getServer();
		if (game != null) {
			throw new IllegalArgumentException("A colosseum game is already on: /sparbot colosseum stop first");
		}
		if (SparBot.matches().inMatch(player.getUUID()) || SparBot.drills().inDrill(player.getUUID())) {
			throw new IllegalArgumentException("You are in a match or a drill: finish it first");
		}
		Kit kit = SparBot.kits().get(kitId).orElseThrow(() -> new IllegalArgumentException("Unknown kit " + kitId));
		SparBot.profiles().get(profileId).orElseThrow(() -> new IllegalArgumentException("Unknown profile " + profileId));
		ServerLevel level = SparBot.practice().ensure(server);
		if (!SparBot.practice().colosseumReady()) {
			throw new IllegalStateException("The Celestial Colosseum is still being built");
		}
		if (SparBot.matches().backups().has(player.getUUID())) {
			throw new IllegalArgumentException("You still have an unrestored match backup; rejoin first");
		}
		SparBot.matches().backups().save(player);
		Game g = new Game(kind, player.getUUID(), kitId, profileId);
		game = g;
		player.teleportTo(level, CelestialColosseum.CX + 0.5, PracticeLayout.FLOOR, CelestialColosseum.CZ + 12.5, Set.of(), 180, 0, true);
		KitApplier.applyForPlayer(player, kit);
		if (kind == Kind.WAVES) {
			player.sendSystemMessage(Component.literal("Waves: four waves of bots, each harder than the last. Your kit and health come back between waves.")
				.withStyle(ChatFormatting.GOLD));
			g.wave = 0;
			g.breakTicks = BREAK_TICKS;
		} else {
			player.sendSystemMessage(Component.literal("King of the hill: stand on the heart of the compass rose with nobody else on it. First to "
				+ HILL_TARGET + " seconds wins.").withStyle(ChatFormatting.GOLD));
			for (int i = 0; i < HILL_BOTS; i++) {
				spawn(server, level, g, "Hill" + (i + 1), profileId, i, HILL_BOTS);
			}
		}
		return kind == Kind.WAVES ? "Waves in the Grand Bowl" : "King of the hill in the Grand Bowl";
	}

	private void spawn(MinecraftServer server, ServerLevel level, Game g, String name, String profileId, int i, int of) {
		SparBot.bots().get(name).ifPresent(old -> SparBot.bots().remove(old, "replaced by a colosseum game"));
		double a = Math.PI * 2 * i / of - Math.PI / 2;
		Vec3 at = new Vec3(CelestialColosseum.CX + 0.5 + Math.cos(a) * 30, PracticeLayout.FLOOR, CelestialColosseum.CZ + 0.5 + Math.sin(a) * 30);
		SkillProfile profile = SparBot.profiles().get(profileId).orElseThrow();
		Kit kit = SparBot.kits().get(g.kitId).orElseThrow();
		float yaw = (float) Math.toDegrees(Math.atan2(-(CelestialColosseum.CX - at.x), CelestialColosseum.CZ - at.z));
		Bot bot = SparBot.bots().spawn(server, name, level, at, yaw, profile, kit);
		bot.setAssignedTarget(g.player);
		g.bots.add(name);
	}

	/** Ends the game: bots gone, your inventory back. */
	public boolean stop(MinecraftServer server, String why) {
		Game g = game;
		if (g == null) {
			return false;
		}
		game = null;
		for (String name : g.bots) {
			SparBot.bots().get(name).ifPresent(b -> SparBot.bots().remove(b, "colosseum game over"));
		}
		ServerPlayer player = server.getPlayerList().getPlayer(g.player);
		if (player != null) {
			player.sendSystemMessage(Component.literal("Colosseum game over: " + why).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			if (player.isAlive()) {
				SparBot.matches().backups().restore(player);
			} else {
				pendingRestore = g.player;
			}
		}
		return true;
	}

	private @Nullable UUID pendingRestore;

	public void tick(MinecraftServer server) {
		if (pendingRestore != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(pendingRestore);
			if (p == null || p.isAlive() && SparBot.matches().backups().restore(p)) {
				pendingRestore = null;
			}
		}
		Game g = game;
		if (g == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(g.player);
		ServerLevel level = io.github.flick256.sparbot.practice.PracticeWorld.level(server);
		if (player == null || level == null) {
			stop(server, "you left");
			return;
		}
		if (player.isDeadOrDying()) {
			stop(server, g.kind == Kind.WAVES ? "you fell in wave " + (g.wave) + " of " + WAVES.length : "you died");
			return;
		}
		if (player.level() != level || !ColosseumWorks.inside(player.getX(), player.getZ(), 0)
			|| Math.hypot(player.getX() - CelestialColosseum.CX, player.getZ() - CelestialColosseum.CZ) > 60) {
			stop(server, "you left the Grand Bowl");
			return;
		}
		g.ticks++;
		if (g.kind == Kind.WAVES) {
			waves(server, level, g, player);
		} else {
			hill(server, level, g, player);
		}
	}

	private void waves(MinecraftServer server, ServerLevel level, Game g, ServerPlayer player) {
		if (g.breakTicks > 0) {
			if (--g.breakTicks == 0) {
				if (g.wave >= WAVES.length) {
					stop(server, "you beat all " + WAVES.length + " waves!");
					return;
				}
				String[] tiers = WAVES[g.wave];
				g.wave++;
				player.sendSystemMessage(Component.literal("Wave " + g.wave + ": " + String.join(", ", tiers)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				for (int i = 0; i < tiers.length; i++) {
					spawn(server, level, g, "Wave" + g.wave + (char) ('a' + i), tiers[i], i, tiers.length);
				}
			} else if (g.breakTicks % 20 == 0) {
				player.sendOverlayMessage(Component.literal("Next wave in " + g.breakTicks / 20 + " s").withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		// The wave is over when every bot of it is dead (they stay dead: one life each).
		boolean left = false;
		for (String name : new ArrayList<>(g.bots)) {
			Bot bot = SparBot.bots().get(name).orElse(null);
			BotPlayer body = bot == null ? null : bot.body();
			if (body != null && body.isAlive()) {
				left = true;
			} else if (bot != null) {
				SparBot.bots().remove(bot, "beaten in the waves");
				g.bots.remove(name);
			}
		}
		if (!left) {
			player.sendSystemMessage(Component.literal("Wave " + g.wave + " beaten.").withStyle(ChatFormatting.GREEN));
			KitApplier.applyForPlayer(player, SparBot.kits().get(g.kitId).orElseThrow());
			g.breakTicks = BREAK_TICKS;
		}
	}

	private void hill(MinecraftServer server, ServerLevel level, Game g, ServerPlayer player) {
		List<String> on = new ArrayList<>();
		if (onHill(player)) {
			on.add(player.getGameProfile().name());
		}
		for (String name : g.bots) {
			Bot bot = SparBot.bots().get(name).orElse(null);
			BotPlayer body = bot == null ? null : bot.body();
			if (bot != null && (body == null || body.isDeadOrDying())) {
				// Back up after five seconds, on the field's edge.
				int t = g.down.merge(name, 1, Integer::sum);
				if (t >= RESPAWN_TICKS) {
					g.down.remove(name);
					SparBot.bots().respawn(server, bot);
				}
			} else if (body != null && onHill(body)) {
				on.add(name);
			}
		}
		if (g.ticks % 20 == 0) {
			if (on.size() == 1) {
				int s = g.score.merge(on.get(0), 1, Integer::sum);
				if (s >= HILL_TARGET) {
					stop(server, on.get(0) + " is king of the hill!");
					return;
				}
			}
			int mine = g.score.getOrDefault(player.getGameProfile().name(), 0);
			int best = g.bots.stream().mapToInt(n -> g.score.getOrDefault(n, 0)).max().orElse(0);
			player.sendOverlayMessage(Component.literal("Hill: you " + mine + " s, best bot " + best + " s, first to " + HILL_TARGET
				+ (on.size() > 1 ? "  (contested)" : "")).withStyle(on.size() == 1 && on.get(0).equals(player.getGameProfile().name())
					? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
	}

	private static boolean onHill(net.minecraft.world.entity.player.Player p) {
		return Math.hypot(p.getX() - CelestialColosseum.CX - 0.5, p.getZ() - CelestialColosseum.CZ - 0.5) <= HILL_RADIUS
			&& Math.abs(p.getY() - PracticeLayout.FLOOR) < 3;
	}
}

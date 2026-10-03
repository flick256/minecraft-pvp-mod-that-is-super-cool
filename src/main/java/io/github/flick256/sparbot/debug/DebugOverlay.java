package io.github.flick256.sparbot.debug;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.ClientEmulator;
import io.github.flick256.sparbot.core.brain.DecisionTrace;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side debug view of a bot's mind for players who ask for it (works with an unmodded client):
 * the action bar shows the active tactic, the top utility scores and key state, and a particle marks
 * where the bot's crosshair is pointing. Only the watching player receives any of it.
 */
public final class DebugOverlay {
	private static final int TEXT_EVERY_TICKS = 4;
	private static final int TOP_SCORES = 4;

	private final Map<UUID, String> watching = new HashMap<>();

	public void watch(ServerPlayer viewer, Bot bot) {
		watching.put(viewer.getUUID(), bot.name());
	}

	public boolean stop(ServerPlayer viewer) {
		return watching.remove(viewer.getUUID()) != null;
	}

	public void tick(MinecraftServer server) {
		if (watching.isEmpty()) {
			return;
		}
		watching.entrySet().removeIf(entry -> {
			ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());
			if (viewer == null) {
				return true;
			}
			Bot bot = SparBot.bots().get(entry.getValue()).orElse(null);
			if (bot == null || bot.body() == null) {
				viewer.sendOverlayMessage(Component.literal("SparBot " + entry.getValue() + " is gone").withStyle(ChatFormatting.RED));
				return true;
			}
			BotPlayer body = bot.body();
			if (server.getTickCount() % TEXT_EVERY_TICKS == 0) {
				viewer.sendOverlayMessage(line(bot, body));
			}
			if (body.isAlive() && body.level() == viewer.level()) {
				HitResult aim = ClientEmulator.raycast(body);
				Vec3 at = aim.getLocation();
				body.level().sendParticles(viewer, ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
			return false;
		});
	}

	/** The action bar text: "Name [profile/style] TACTIC | top scores | distance hp charge". */
	public static MutableComponent line(Bot bot, BotPlayer body) {
		DecisionTrace trace = bot.trace();
		String scores = trace.scores().entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
			.limit(TOP_SCORES)
			.map(e -> String.format("%s %.2f", e.getKey(), e.getValue()))
			.collect(Collectors.joining("  "));
		MutableComponent text = Component.literal(bot.name()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(" [" + bot.profile().id() + "/" + bot.playstyle().id() + "] ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(trace.tactic().toUpperCase()).withStyle(ChatFormatting.GREEN))
			.append(Component.literal(" | " + scores + " | ").withStyle(ChatFormatting.WHITE));
		String state = body.isDeadOrDying() ? "DEAD" : String.format("dist %.1f  hp %.0f+%.0f  charge %.0f%%", trace.targetDistance(),
			body.getHealth(), body.getAbsorptionAmount(), body.getAttackStrengthScale(0.5F) * 100);
		return text.append(Component.literal(state).withStyle(ChatFormatting.AQUA));
	}
}

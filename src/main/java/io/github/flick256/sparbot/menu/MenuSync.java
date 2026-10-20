package io.github.flick256.sparbot.menu;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.command.SparBotCommand;
import io.github.flick256.sparbot.config.ConfigEditor;
import io.github.flick256.sparbot.core.ui.MenuState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Feeds the client menu. The server sends {@link MenuStatePayload} when {@code /sparbot menu} runs or
 * when the client asks ({@link MenuRequestPayload}), and only to players allowed to use /sparbot. The
 * menu never changes anything itself: its buttons send ordinary /sparbot commands.
 */
public final class MenuSync {
	private MenuSync() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MenuStatePayload.TYPE, MenuStatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MenuRequestPayload.TYPE, MenuRequestPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MenuRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (SparBotCommand.allowed(player.createCommandSourceStack())) {
				send(player, payload.open());
			}
		});
	}

	/** Whether the player's client has SparBot installed (and so can show the menu). */
	public static boolean canShow(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, MenuStatePayload.TYPE);
	}

	public static void send(ServerPlayer player, boolean open) {
		ServerPlayNetworking.send(player, new MenuStatePayload(state(open, player).toJson()));
	}

	public static MenuState state(boolean open) {
		return state(open, null);
	}

	/** @param viewer the player the menu is for (their own kit layouts), or null */
	public static MenuState state(boolean open, ServerPlayer viewer) {
		List<MenuState.BotEntry> bots = new ArrayList<>();
		for (Bot bot : SparBot.bots().all()) {
			BotPlayer body = bot.body();
			boolean alive = body != null && body.isAlive();
			bots.add(new MenuState.BotEntry(bot.name(), bot.profile().id(), bot.playstyle().id(), alive, alive ? body.getHealth() : 0,
				bot.disabledTechniques().stream().map(io.github.flick256.sparbot.core.brain.Technique::id).sorted().toList(),
				""));
		}
		List<MenuState.Setting> settings = new ArrayList<>(ConfigEditor.settings(SparBot.config()).stream()
			.map(s -> new MenuState.Setting(s.key(), s.type(), s.value())).toList());
		if (viewer != null) {
			settings.add(new MenuState.Setting("practiceTerrain", "preference", SparBot.practice().terrain(viewer.getUUID()).id()));
			settings.add(new MenuState.Setting("adaptivePractice", "preference", Boolean.toString(SparBot.careers().adaptivePractice(viewer.getUUID()))));
		}
		return new MenuState(open, sorted(SparBot.profiles().ids()), sorted(SparBot.kits().ids()), sorted(SparBot.playstyles().ids()),
			sorted(SparBot.matches().modes().ids()), bots, settings, sorted(SparBot.kits().ids()).stream()
				.flatMap(id -> SparBot.kits().get(id).stream())
				.map(kit -> new MenuState.KitEntry(kit.id(), kit.displayName(), kit.provenance().verified())).toList(),
			List.of(), viewer == null ? List.of() : SparBot.playerLayouts().kits(viewer.getUUID()),
			viewer == null ? java.util.Map.of() : SparBot.drills().medalsOf(viewer.getUUID()),
			viewer == null || SparBot.drills().reports() == null ? List.of() : SparBot.drills().reports().of(viewer.getUUID()),
			viewer == null ? List.of() : SparBot.careers().menuRivals(viewer.getUUID()),
			viewer == null ? List.of() : SparBot.careers().menuCareers(viewer.getUUID()), replayEntries(),
			viewer == null || SparBot.drills().reports() == null ? List.of() : io.github.flick256.sparbot.core.drill.TrainingPlan
				.recommendations(SparBot.drills().reports().of(viewer.getUUID())).stream()
				.map(r -> new MenuState.TrainingEntry(r.drillId(), r.title(), r.reason(), r.priority())).toList());
	}

	/** Keep the regularly refreshed menu below the payload budget even with long recordings. */
	private static List<MenuState.ReplayEntry> replayEntries() {
		java.util.Map<String, MenuState.ReplayEntry> entries = new java.util.LinkedHashMap<>();
		for (var replay : SparBot.replays().status().stream().limit(12).toList()) {
			entries.put(replay.name(), new MenuState.ReplayEntry(replay.name(), replay.durationTicks(), true, replay.paused(),
				replay.frame(), replay.speed(), reviewEvents(replay.events(), replay.frame())));
		}
		try {
			for (var saved : SparBot.recorder().metadata()) {
				if (entries.size() >= 12) { break; }
				entries.putIfAbsent(saved.name(), new MenuState.ReplayEntry(saved.name(), saved.durationTicks(), false, false, 0, 1,
					reviewEvents(saved.events(), 0)));
			}
		} catch (java.io.IOException e) {
			SparBot.LOGGER.debug("Could not list replay metadata", e);
		}
		return List.copyOf(entries.values());
	}

	private static List<MenuState.ReplayEventEntry> reviewEvents(List<io.github.flick256.sparbot.core.record.ReplayEvent> events, int frame) {
		// Keep only the nearest sixteen while scanning; long fights can contain 100,000 events.
		var byDistance = java.util.Comparator.<io.github.flick256.sparbot.core.record.ReplayEvent>comparingLong(e -> Math.abs((long)e.tick() - frame))
			.thenComparingInt(io.github.flick256.sparbot.core.record.ReplayEvent::tick);
		var nearest = new java.util.PriorityQueue<>(16, byDistance.reversed());
		for (var event : events) {
			if (nearest.size() < 16) nearest.add(event);
			else if (byDistance.compare(event, nearest.peek()) < 0) { nearest.remove(); nearest.add(event); }
		}
		return nearest.stream().sorted(java.util.Comparator.comparingInt(io.github.flick256.sparbot.core.record.ReplayEvent::tick))
			.map(e -> new MenuState.ReplayEventEntry(e.tick(), e.kind(), e.text().length() > 160 ? e.text().substring(0, 160) : e.text())).toList();
	}

	private static List<String> sorted(java.util.Collection<String> ids) {
		return ids.stream().sorted().toList();
	}
}

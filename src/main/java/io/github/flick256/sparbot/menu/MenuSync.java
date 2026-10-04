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
		ServerPlayNetworking.send(player, new MenuStatePayload(state(open).toJson()));
	}

	public static MenuState state(boolean open) {
		List<MenuState.BotEntry> bots = new ArrayList<>();
		for (Bot bot : SparBot.bots().all()) {
			BotPlayer body = bot.body();
			boolean alive = body != null && body.isAlive();
			bots.add(new MenuState.BotEntry(bot.name(), bot.profile().id(), bot.playstyle().id(), alive, alive ? body.getHealth() : 0));
		}
		List<MenuState.Setting> settings = ConfigEditor.settings(SparBot.config()).stream()
			.map(s -> new MenuState.Setting(s.key(), s.type(), s.value())).toList();
		return new MenuState(open, sorted(SparBot.profiles().ids()), sorted(SparBot.kits().ids()), sorted(SparBot.playstyles().ids()),
			sorted(SparBot.matches().modes().ids()), bots, settings);
	}

	private static List<String> sorted(java.util.Collection<String> ids) {
		return ids.stream().sorted().toList();
	}
}

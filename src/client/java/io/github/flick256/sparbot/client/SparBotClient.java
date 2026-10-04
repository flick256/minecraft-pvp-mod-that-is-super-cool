package io.github.flick256.sparbot.client;

import com.google.gson.JsonParseException;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.ui.MenuState;
import io.github.flick256.sparbot.menu.MenuRequestPayload;
import io.github.flick256.sparbot.menu.MenuStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The optional client half: a key (B by default) and {@code /sparbot menu} open the SparBot menu.
 * Without SparBot on the client everything still works through commands.
 */
public final class SparBotClient implements ClientModInitializer {
	private static final KeyMapping OPEN_MENU = new KeyMapping("key.sparbot.menu", GLFW.GLFW_KEY_B,
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sparbot", "sparbot")));

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(OPEN_MENU);
		ClientPlayNetworking.registerGlobalReceiver(MenuStatePayload.TYPE, (payload, context) -> {
			MenuState state;
			try {
				state = MenuState.fromJson(payload.json());
			} catch (JsonParseException e) {
				SparBot.LOGGER.warn("Ignoring a bad SparBot menu update: {}", e.getMessage());
				return;
			}
			Minecraft minecraft = context.client();
			if (minecraft.gui.screen() instanceof SparBotMenuScreen open) {
				open.update(state);
			} else if (state.open()) {
				minecraft.gui.setScreen(new SparBotMenuScreen(state));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (OPEN_MENU.consumeClick()) {
				if (minecraft.player == null) {
					continue;
				}
				if (ClientPlayNetworking.canSend(MenuRequestPayload.TYPE)) {
					ClientPlayNetworking.send(new MenuRequestPayload(true));
				} else {
					minecraft.player.sendOverlayMessage(Component.literal("SparBot isn't installed on this server"));
				}
			}
		});
	}
}

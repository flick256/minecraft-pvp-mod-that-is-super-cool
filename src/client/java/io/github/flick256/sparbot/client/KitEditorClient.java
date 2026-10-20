package io.github.flick256.sparbot.client;

import io.github.flick256.sparbot.kit.editor.KitEditorPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only packet receiver, separate from the dedicated-server-safe command package. */
public final class KitEditorClient {
	private KitEditorClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(KitEditorPayloads.Open.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			if (payload.kitJson().isEmpty()) {
				if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.literal(payload.status()));
				if (minecraft.gui.screen() instanceof KitEditorScreen screen) screen.setStatus(payload.status());
				return;
			}
			minecraft.gui.setScreen(new KitEditorScreen(payload.kitJson(), payload.status()));
		});
	}
}

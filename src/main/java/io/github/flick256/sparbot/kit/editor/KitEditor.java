package io.github.flick256.sparbot.kit.editor;

import com.google.gson.JsonParseException;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.command.SparBotCommand;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.kit.KitRegistry;
import java.io.StringReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server authority and persistence for editor drafts. */
public final class KitEditor {
	private static final long SESSION_LIFETIME_MS = 10 * 60 * 1000L;
	private static final ConcurrentMap<java.util.UUID, Long> SESSIONS = new ConcurrentHashMap<>();
	private KitEditor() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(KitEditorPayloads.Open.TYPE, KitEditorPayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(KitEditorPayloads.Save.TYPE, KitEditorPayloads.Save.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KitEditorPayloads.Save.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> save(player, payload.kitJson()));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
	}

	public static void open(ServerPlayer player, String baseKitId) {
		if (!SparBotCommand.allowed(player.createCommandSourceStack())) {
			player.sendSystemMessage(Component.literal("You do not have permission to edit kits."));
			return;
		}
		Kit kit = SparBot.kits().get(baseKitId).orElse(null);
		if (kit == null) {
			player.sendSystemMessage(Component.literal("Unknown kit: " + baseKitId));
			return;
		}
		String json = Kits.toJson(kit);
		if (json.length() > 65536) {
			message(player, "That preset is too large for the editor. Choose a smaller kit.");
			return;
		}
		SESSIONS.put(player.getUUID(), System.currentTimeMillis() + SESSION_LIFETIME_MS);
		ServerPlayNetworking.send(player, new KitEditorPayloads.Open(json, "Draft loaded: " + kit.displayName()));
	}

	private static void save(ServerPlayer player, String json) {
		if (!SparBotCommand.allowed(player.createCommandSourceStack())) {
			message(player, "You do not have permission to save kits.");
			return;
		}
		Long expires = SESSIONS.get(player.getUUID());
		if (expires == null || expires < System.currentTimeMillis()) {
			SESSIONS.remove(player.getUUID());
			message(player, "Editor session expired. Open /sparbot kiteditor again.");
			return;
		}
		if (SparBot.matches().inMatch(player.getUUID()) || SparBot.drills().inDrill(player.getUUID())) {
			message(player, "Leave your active match or drill before saving a kit.");
			return;
		}
		if (json == null || json.length() > 65536) {
			message(player, "Draft is too large.");
			return;
		}
		try {
			Kit draft = Kits.parse(new StringReader(json));
			if (SparBot.kits().get(draft.id()).isPresent()) {
				message(player, "Kit ID already exists; choose a new custom ID.");
				return;
			}
			List<String> draftErrors = KitEditorDraftValidator.validate(draft);
			if (!draftErrors.isEmpty()) {
				message(player, "Invalid editor draft: " + String.join("; ", draftErrors));
				return;
			}
			List<String> errors = KitApplier.resolveErrors(draft, player.level().registryAccess());
			if (!errors.isEmpty()) {
				message(player, "Cannot save: " + String.join("; ", errors));
				return;
			}
			SparBot.kits().saveNew(draft, player.level().registryAccess());
			SESSIONS.remove(player.getUUID());
			message(player, "Saved custom kit '" + draft.id() + "'. Your inventory was not changed.");
		} catch (IllegalArgumentException | IOException | JsonParseException e) {
			message(player, "Could not save kit: " + e.getMessage());
		}
	}

	private static void message(ServerPlayer player, String text) {
		String safe = text.length() > 900 ? text.substring(0, 897) + "…" : text;
		player.sendSystemMessage(Component.literal(safe));
		ServerPlayNetworking.send(player, new KitEditorPayloads.Open("", safe));
	}
}

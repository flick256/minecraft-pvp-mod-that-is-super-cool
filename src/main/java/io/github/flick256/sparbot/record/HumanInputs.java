package io.github.flick256.sparbot.record;

import io.github.flick256.sparbot.core.act.Inputs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

/**
 * Reads a human's inputs back from what their client told the server: the movement keys it reports
 * (ServerboundPlayerInputPacket), rotation changes, arm swings (ServerboundSwingPacket, seen through
 * ServerGamePacketListenerImplMixin), item use and hotbar changes. The inventory screen of a player's
 * own inventory never reaches the server, so it reads as closed.
 */
public final class HumanInputs {
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private static final class State {
		boolean seen;
		float yaw;
		float pitch;
		int slot;
		boolean swung;
	}

	private HumanInputs() {
	}

	/** The client sent an arm swing (a left click, whether or not it hit anything). */
	public static void noteSwing(ServerPlayer player) {
		STATES.computeIfAbsent(player.getUUID(), id -> new State()).swung = true;
	}

	/** This tick's inputs; call once per tick per player. */
	static Inputs read(ServerPlayer player) {
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		Input keys = player.getLastClientInput();
		int selected = player.getInventory().getSelectedSlot();
		float yawDelta = s.seen ? Mth.wrapDegrees(player.getYRot() - s.yaw) : 0;
		float pitchDelta = s.seen ? player.getXRot() - s.pitch : 0;
		int forward = keys.forward() == keys.backward() ? 0 : keys.forward() ? 1 : -1;
		int strafe = keys.left() == keys.right() ? 0 : keys.left() ? 1 : -1;
		int hotbar = s.seen && selected != s.slot ? selected : -1;
		Inputs inputs = new Inputs(yawDelta, pitchDelta, forward, strafe, keys.jump(), keys.shift(), keys.sprint() || player.isSprinting(), s.swung,
			player.isUsingItem(), hotbar);
		s.seen = true;
		s.yaw = player.getYRot();
		s.pitch = player.getXRot();
		s.slot = selected;
		s.swung = false;
		return inputs;
	}

	static void forget(UUID player) {
		STATES.remove(player);
	}
}

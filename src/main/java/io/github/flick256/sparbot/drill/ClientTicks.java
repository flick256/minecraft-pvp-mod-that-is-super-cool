package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.drill.ClientFrame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * Each real player's last couple of seconds of client ticks ({@link ClientFrame}), built from the packets
 * their client sends every tick: the key state ({@code ServerboundPlayerInputPacket}, sent whenever a key
 * changes), the move, and the end of the tick ({@code ServerboundClientTickEndPacket}). The drills read
 * jump resets, S-taps and W-taps from these, timed in the client's own ticks. Server thread only.
 */
public final class ClientTicks {
	private static final int KEEP = 80;

	private static final class Track {
		int tick;
		Input keys = Input.EMPTY;
		final ArrayDeque<ClientFrame> frames = new ArrayDeque<>();
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private ClientTicks() {
	}

	private static Track track(ServerPlayer player) {
		return TRACKS.computeIfAbsent(player.getUUID(), u -> new Track());
	}

	/** The client's key state changed (it applies to the tick it was sent in). */
	public static void input(ServerPlayer player, Input keys) {
		if (!(player instanceof BotPlayer)) {
			track(player).keys = keys;
		}
	}

	/** The client finished a tick: its frame is complete. */
	public static void tickEnd(ServerPlayer player) {
		if (player instanceof BotPlayer) {
			return;
		}
		Track t = track(player);
		Input k = t.keys;
		t.frames.addLast(new ClientFrame(t.tick, k.forward(), k.backward(), k.left(), k.right(), k.jump(), k.sprint(), player.isSprinting(), player.getX(),
			player.getY(), player.getZ(), player.onGround()));
		while (t.frames.size() > KEEP) {
			t.frames.removeFirst();
		}
		t.tick++;
		io.github.flick256.sparbot.SparBot.drills().onClientTick(player);
	}

	/** The client tick being played now (its frame arrives at its end): the tick of a click handled now. */
	public static int now(ServerPlayer player) {
		return track(player).tick;
	}

	/** Whether this player's client sends tick ends (every 26.2 client does; a test player may not). */
	public static boolean tracked(ServerPlayer player) {
		Track t = TRACKS.get(player.getUUID());
		return t != null && !t.frames.isEmpty();
	}

	public static List<ClientFrame> frames(ServerPlayer player) {
		Track t = TRACKS.get(player.getUUID());
		return t == null ? List.of() : new ArrayList<>(t.frames);
	}

	public static void forget(UUID player) {
		TRACKS.remove(player);
	}
}

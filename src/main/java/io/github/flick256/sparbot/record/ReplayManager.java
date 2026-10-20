package io.github.flick256.sparbot.record;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.record.FighterInfo;
import io.github.flick256.sparbot.core.record.Frame;
import io.github.flick256.sparbot.core.record.Recording;
import io.github.flick256.sparbot.core.record.ReplayEvent;
import io.github.flick256.sparbot.core.record.Sample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Plays recordings back where they were recorded, as mannequins (player-shaped, with the fighter's
 * skin when their name is a real account) that follow each recorded tick: position, look, held item,
 * sneaking and arm swings. The mannequins are props: they don't fight, take no damage and vanish
 * when the replay ends.
 */
public final class ReplayManager {
	private final Map<String, Replay> replays = new LinkedHashMap<>();

	private static final class Replay {
		final Recording recording;
		final ServerLevel level;
		final List<Mannequin> actors = new ArrayList<>();
		int frame;
		boolean paused;
		double speed = 1.0;
		double remainder;
		final java.util.Set<String> enabledEvents = new java.util.HashSet<>();

		Replay(Recording recording, ServerLevel level) {
			this.recording = recording;
			this.level = level;
		}
	}

	public List<String> active() {
		return List.copyOf(replays.keySet());
	}
	public record ReplayStatus(String name, int durationTicks, boolean active, boolean paused, int frame, double speed, List<ReplayEvent> events) { }
	public List<ReplayStatus> status() {
		return replays.values().stream().map(r -> new ReplayStatus(r.recording.label(), r.recording.durationTicks(), true, r.paused, r.frame, r.speed, events(r.recording.label()))).toList();
	}
	public boolean pause(String label) { Replay r = replays.get(label); if (r == null) return false; r.paused = true; return true; }
	public boolean resume(String label) { Replay r = replays.get(label); if (r == null) return false; r.paused = false; return true; }
	public boolean speed(String label, double speed) {
		Replay r = replays.get(label);
		if (r == null || !Double.isFinite(speed) || (speed != .25 && speed != .5 && speed != 1 && speed != 2)) return false;
		r.speed = speed; return true;
	}
	public boolean seek(String label, int tick) {
		Replay r = replays.get(label); if (r == null || tick < 0 || r.recording.durationTicks() == 0) return false;
		r.frame = Math.min(tick, r.recording.durationTicks() - 1);
		r.paused = true;
		r.remainder = 0;
		showFrame(r, r.frame); return true;
	}
	public boolean step(String label, int amount) {
		Replay r = replays.get(label); if (r == null || r.recording.frames().isEmpty() || (amount != -1 && amount != 1)) return false;
		r.paused = true;
		r.remainder = 0;
		r.frame = Math.max(0, Math.min(r.recording.durationTicks() - 1, r.frame + amount));
		showFrame(r, r.frame); return true;
	}
	public boolean setEventEnabled(String label, String kind, boolean enabled) {
		Replay r = replays.get(label); if (r == null) return false;
		if (enabled) r.enabledEvents.add(kind); else r.enabledEvents.remove(kind);
		return true;
	}
	public List<ReplayEvent> events(String label) {
		Replay r = replays.get(label); if (r == null) return List.of();
		return r.recording.events().stream().filter(e -> r.enabledEvents.contains(e.kind())).toList();
	}
	public List<String> eventKinds(String label) {
		Replay r = replays.get(label); if (r == null) return List.of();
		return r.recording.events().stream().map(ReplayEvent::kind).distinct().sorted().toList();
	}

	/** Returns the mannequins standing in for the fighters (for tests and the debug overlay). */
	public List<Mannequin> actors(String label) {
		Replay replay = replays.get(label);
		return replay == null ? List.of() : List.copyOf(replay.actors);
	}

	public void start(MinecraftServer server, Recording recording) {
		if (replays.containsKey(recording.label())) {
			throw new IllegalStateException("Already replaying " + recording.label());
		}
		if (recording.frames().isEmpty()) throw new IllegalArgumentException("Recording " + recording.label() + " has no frames");
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(recording.dimension())));
		if (level == null) {
			throw new IllegalStateException("The world " + recording.dimension() + " it was recorded in isn't loaded");
		}
		Replay replay = new Replay(recording, level);
		recording.events().stream().map(ReplayEvent::kind).forEach(replay.enabledEvents::add);
		try { for (FighterInfo fighter : recording.fighters()) {
			Mannequin actor = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
			if (actor == null) {
				throw new IllegalStateException("Could not create a mannequin");
			}
			actor.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(fighter.name()));
			actor.setCustomName(Component.literal(fighter.name() + " (replay)"));
			actor.setCustomNameVisible(true);
			actor.setNoGravity(true);
			actor.setInvulnerable(true);
			actor.setSilent(true);
			actor.setInvisible(true);
			replay.actors.add(actor);
			level.addFreshEntity(actor);
		} } catch (RuntimeException e) { replay.actors.forEach(Mannequin::discard); throw e; }
		replays.put(recording.label(), replay);
		showFrame(replay, 0);
		SparBot.LOGGER.info("Replaying {} ({} ticks)", recording.label(), recording.durationTicks());
	}

	public boolean stop(String label) {
		Replay replay = replays.remove(label);
		if (replay == null) {
			return false;
		}
		replay.actors.forEach(a -> a.discard());
		return true;
	}

	public void stopAll() {
		for (String label : List.copyOf(replays.keySet())) {
			stop(label);
		}
	}

	public void tick(MinecraftServer server) {
		for (Replay replay : List.copyOf(replays.values())) {
			if (replay.paused) continue;
			replay.remainder += replay.speed;
			int advances = (int) replay.remainder;
			replay.remainder -= advances;
			for (int n = 0; n < advances; n++) {
				if (replay.frame + 1 >= replay.recording.durationTicks()) {
					replay.paused = true;
					replay.remainder = 0;
					break;
				}
				showFrame(replay, ++replay.frame);
			}
		}
	}

	private static void showFrame(Replay replay, int index) {
		if (index < 0 || index >= replay.recording.frames().size()) return;
		Frame frame = replay.recording.frames().get(index);
		for (int i = 0; i < replay.actors.size(); i++) show(replay.actors.get(i), frame.samples()[i]);
	}

	private static void show(Mannequin actor, Sample s) {
		if (s == null) {
			actor.setInvisible(true);
			return;
		}
		actor.setInvisible(false);
		actor.snapTo(s.position().x(), s.position().y(), s.position().z(), s.yaw(), s.pitch());
		actor.setYHeadRot(s.yaw());
		actor.setYBodyRot(s.yaw());
		actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		actor.setShiftKeyDown(s.inputs().sneak());
		ItemStack held = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s.mainHandId())));
		if (!ItemStack.isSameItem(actor.getMainHandItem(), held)) {
			actor.setItemSlot(EquipmentSlot.MAINHAND, held);
		}
		if (s.inputs().attack()) {
			actor.swing(InteractionHand.MAIN_HAND, true);
		}
	}
}

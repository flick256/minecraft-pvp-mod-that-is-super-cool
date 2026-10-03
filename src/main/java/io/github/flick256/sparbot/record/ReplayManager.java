package io.github.flick256.sparbot.record;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.record.FighterInfo;
import io.github.flick256.sparbot.core.record.Frame;
import io.github.flick256.sparbot.core.record.Recording;
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

		Replay(Recording recording, ServerLevel level) {
			this.recording = recording;
			this.level = level;
		}
	}

	public List<String> active() {
		return List.copyOf(replays.keySet());
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
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(recording.dimension())));
		if (level == null) {
			throw new IllegalStateException("The world " + recording.dimension() + " it was recorded in isn't loaded");
		}
		Replay replay = new Replay(recording, level);
		for (FighterInfo fighter : recording.fighters()) {
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
		}
		replays.put(recording.label(), replay);
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
			if (replay.frame >= replay.recording.frames().size()) {
				stop(replay.recording.label());
				continue;
			}
			Frame frame = replay.recording.frames().get(replay.frame++);
			for (int i = 0; i < replay.actors.size(); i++) {
				show(replay.actors.get(i), frame.samples()[i]);
			}
		}
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

package io.github.flick256.sparbot.record;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.ItemClassifier;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.record.FighterInfo;
import io.github.flick256.sparbot.core.record.Frame;
import io.github.flick256.sparbot.core.record.Recording;
import io.github.flick256.sparbot.core.record.RecordingIO;
import io.github.flick256.sparbot.core.record.Sample;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Records fights to {@code config/sparbot/recordings/<label>.spbr}: every tick, each recorded player's
 * position, own state and inputs (see {@link Sample}). Recording only watches; it changes nothing.
 */
public final class Recorder {
	/** A recording stops by itself after 20 minutes. */
	public static final int MAX_TICKS = 20 * 60 * 20;

	private final Map<String, Session> sessions = new LinkedHashMap<>();
	private Path directory = Path.of("config", "sparbot", "recordings");

	private static final class Session {
		final String label;
		final String mode;
		final String dimension;
		final long started = System.currentTimeMillis();
		final List<UUID> players;
		final List<FighterInfo> fighters;
		final List<Frame> frames = new ArrayList<>();

		Session(String label, String mode, String dimension, List<UUID> players, List<FighterInfo> fighters) {
			this.label = label;
			this.mode = mode;
			this.dimension = dimension;
			this.players = players;
			this.fighters = fighters;
		}
	}

	public void setDirectory(Path directory) {
		this.directory = directory;
	}

	public static boolean validLabel(String label) {
		return label.matches("[a-z0-9_\\-]{1,64}");
	}

	public boolean isRecording(String label) {
		return sessions.containsKey(label);
	}

	public List<String> active() {
		return List.copyOf(sessions.keySet());
	}

	public void start(String label, String mode, List<ServerPlayer> players) {
		if (!validLabel(label)) {
			throw new IllegalArgumentException("Recording names are 1-64 lowercase letters, digits, _ or -");
		}
		if (sessions.containsKey(label)) {
			throw new IllegalStateException("Already recording " + label);
		}
		if (players.isEmpty()) {
			throw new IllegalArgumentException("Nobody to record");
		}
		List<UUID> ids = new ArrayList<>();
		List<FighterInfo> fighters = new ArrayList<>();
		for (ServerPlayer p : players) {
			ids.add(p.getUUID());
			fighters.add(p instanceof BotPlayer bot
				? new FighterInfo(bot.bot().name(), true, bot.bot().profile().id(), bot.bot().playstyle().id())
				: new FighterInfo(p.getPlainTextName(), false, "", ""));
		}
		String dimension = players.get(0).level().dimension().identifier().toString();
		sessions.put(label, new Session(label, mode, dimension, ids, fighters));
		SparBot.LOGGER.info("Recording {} started: {}", label, fighters.stream().map(FighterInfo::name).toList());
	}

	/** Stops a recording and writes it; returns the file. */
	public Path stop(String label) throws IOException {
		Session session = sessions.remove(label);
		if (session == null) {
			throw new IllegalStateException("Not recording " + label);
		}
		return save(session);
	}

	public void stopAll() {
		for (String label : List.copyOf(sessions.keySet())) {
			try {
				stop(label);
			} catch (IOException e) {
				SparBot.LOGGER.error("Could not save recording {}", label, e);
			}
		}
	}

	public void tick(MinecraftServer server) {
		if (sessions.isEmpty()) {
			return;
		}
		long tick = server.overworld().getGameTime();
		// Each player's inputs are read once per tick, however many recordings include them.
		Map<UUID, Sample> samples = new HashMap<>();
		for (Session session : List.copyOf(sessions.values())) {
			Sample[] frame = new Sample[session.players.size()];
			for (int i = 0; i < frame.length; i++) {
				UUID id = session.players.get(i);
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				frame[i] = player == null || !player.isAlive() ? null : samples.computeIfAbsent(id, unused -> sample(player));
			}
			session.frames.add(new Frame(tick, frame));
			if (session.frames.size() >= MAX_TICKS) {
				try {
					SparBot.LOGGER.info("Recording {} reached 20 minutes and was saved to {}", session.label, stop(session.label));
				} catch (IOException e) {
					SparBot.LOGGER.error("Could not save recording {}", session.label, e);
				}
			}
		}
	}

	private static Sample sample(ServerPlayer p) {
		Inputs inputs = p instanceof BotPlayer bot ? bot.bot().lastInputs() : HumanInputs.read(p);
		net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
		return new Sample(new Vec3(p.getX(), p.getY(), p.getZ()), new Vec3(v.x, v.y, v.z), p.getYRot(), p.getXRot(), p.getHealth(),
			p.getAbsorptionAmount(), p.getAttackStrengthScale(0.5F), p.onGround(), p.hurtTime, p.getInventory().getSelectedSlot(),
			ItemClassifier.classify(p.getMainHandItem()), ItemClassifier.classify(p.getOffhandItem()),
			p.isUsingItem() ? ItemClassifier.classify(p.getUseItem()) : ItemKind.EMPTY,
			BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString(), inputs);
	}

	private Path save(Session session) throws IOException {
		Files.createDirectories(directory);
		Path file = directory.resolve(session.label + ".spbr");
		Recording recording = new Recording(session.label, session.mode, session.dimension, session.started, session.fighters, session.frames);
		try (OutputStream out = Files.newOutputStream(file)) {
			RecordingIO.write(recording, out);
		}
		session.players.forEach(HumanInputs::forget);
		SparBot.LOGGER.info("Recording {} saved: {} ticks to {}", session.label, session.frames.size(), file);
		return file;
	}

	public List<String> saved() throws IOException {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".spbr")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
		}
	}

	public Optional<Recording> load(String label) throws IOException {
		if (!validLabel(label)) {
			return Optional.empty();
		}
		Path file = directory.resolve(label + ".spbr");
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (InputStream in = Files.newInputStream(file)) {
			return Optional.of(RecordingIO.read(in));
		}
	}
}

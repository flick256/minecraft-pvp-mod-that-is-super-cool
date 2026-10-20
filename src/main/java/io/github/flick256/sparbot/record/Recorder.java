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
import io.github.flick256.sparbot.core.record.ReplayEvent;
import io.github.flick256.sparbot.core.record.Sample;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
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
	public static final int MAX_EVENTS = 100_000;
	public static final int MAX_SESSIONS = 8;
	public static final int MAX_METADATA_RECORDINGS = 24;
	public static final long MAX_METADATA_SCAN_BYTES = 64L * 1024 * 1024;

	private final Map<String, Session> sessions = new LinkedHashMap<>();
	private Path directory = Path.of("config", "sparbot", "recordings");
	private List<ReplayMetadata> metadataCache;
	public record ReplayMetadata(String name, int durationTicks, List<ReplayEvent> events) { }

	private static final class Session {
		final String label;
		final String mode;
		final String dimension;
		final long started = System.currentTimeMillis();
		final List<UUID> players;
		final List<FighterInfo> fighters;
		final List<Frame> frames = new ArrayList<>();
		final List<ReplayEvent> events = new ArrayList<>();
		final Map<UUID, Boolean> blocking = new HashMap<>();
		final Map<UUID, Boolean> burning = new HashMap<>();
		boolean stopping;

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
		this.metadataCache = null;
	}

	public static boolean validLabel(String label) {
		return label.matches("[a-z0-9_\\-]{1,64}");
	}

	public boolean isRecording(String label) {
		return sessions.containsKey(label);
	}

	/** Add an authoritative event observed by a game hook. The limit keeps busy fights bounded. */
	public void event(String kind, String text, UUID... participants) {
		if (sessions.isEmpty() || participants == null || participants.length == 0) return;
		for (Session s : sessions.values()) {
			if (s.stopping || s.events.size() >= MAX_EVENTS || java.util.Arrays.stream(participants).noneMatch(s.players::contains)) continue;
			addEvent(s, kind, text);
		}
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
		if (sessions.size() >= MAX_SESSIONS) throw new IllegalStateException("At most " + MAX_SESSIONS + " recordings can run at once");
		if (players.isEmpty()) {
			throw new IllegalArgumentException("Nobody to record");
		}
		if (players.size() > RecordingIO.MAX_FIGHTERS) throw new IllegalArgumentException("A recording can include at most " + RecordingIO.MAX_FIGHTERS + " players");
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
		Session session = sessions.get(label);
		if (session == null) {
			throw new IllegalStateException("Not recording " + label);
		}
		session.stopping = true;
		try {
			Path file = save(session);
			sessions.remove(label, session);
			return file;
		} finally {
			session.players.forEach(HumanInputs::forget);
		}
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
			if (session.stopping) continue;
			Sample[] frame = new Sample[session.players.size()];
			for (int i = 0; i < frame.length; i++) {
				UUID id = session.players.get(i);
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				frame[i] = player == null || !player.isAlive() ? null : samples.computeIfAbsent(id, unused -> sample(player));
				if (player != null && player.isAlive()) {
					boolean raised = player.isBlocking();
					Boolean beforeRaised = session.blocking.put(id, raised);
					if ((beforeRaised == null && raised) || (beforeRaised != null && beforeRaised != raised))
						addEvent(session, "shield", player.getPlainTextName() + (raised ? " raised shield" : " lowered shield"));
					boolean fire = player.getRemainingFireTicks() > 0;
					Boolean beforeFire = session.burning.put(id, fire);
					if ((beforeFire == null && fire) || (beforeFire != null && beforeFire != fire))
						addEvent(session, "fire", player.getPlainTextName() + (fire ? " caught fire" : " fire went out"));
				}
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

	private static void addEvent(Session session, String kind, String text) {
		if (session.events.size() < MAX_EVENTS) session.events.add(new ReplayEvent(session.frames.size(), kind, text));
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
		Recording recording = new Recording(session.label, session.mode, session.dimension, session.started, session.fighters, session.frames, session.events);
		Path temp = Files.createTempFile(directory, "sparbot-" + session.label + "-", ".spbr.tmp");
		try {
			try (OutputStream out = Files.newOutputStream(temp)) { RecordingIO.write(recording, out); }
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			if (metadataCache != null) {
				ReplayMetadata summary = new ReplayMetadata(recording.label(), recording.durationTicks(),
					recording.events().stream().limit(RecordingIO.MAX_METADATA_EVENTS).toList());
				metadataCache = java.util.stream.Stream.concat(java.util.stream.Stream.of(summary), metadataCache.stream()
					.filter(entry -> !entry.name().equals(session.label))).limit(MAX_METADATA_RECORDINGS).toList();
			}
		} finally {
			Files.deleteIfExists(temp);
		}
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
		if (Files.size(file) > RecordingIO.MAX_FILE_BYTES) throw new IOException("Recording exceeds maximum file size");
		try (InputStream in = Files.newInputStream(file)) {
			return Optional.of(RecordingIO.read(in));
		}
	}

	/** Cached summaries for menus; the recording bodies are read once, not on every menu refresh. */
	public List<ReplayMetadata> metadata() throws IOException {
		if (metadataCache != null) return metadataCache;
		List<ReplayMetadata> result = new ArrayList<>();
		long remainingBytes = MAX_METADATA_SCAN_BYTES;
		if (Files.isDirectory(directory)) try (Stream<Path> files = Files.list(directory)) {
			List<Path> newest = files.filter(f -> f.getFileName().toString().endsWith(".spbr"))
				.sorted((a, b) -> {
					try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
					catch (IOException e) { return a.getFileName().toString().compareTo(b.getFileName().toString()); }
				}).limit(MAX_METADATA_RECORDINGS).toList();
			for (Path file : newest) {
				try (InputStream in = Files.newInputStream(file)) {
					if (Files.size(file) > RecordingIO.MAX_FILE_BYTES) throw new IOException("Recording exceeds maximum file size");
					if (remainingBytes <= 0) break;
					RecordingIO.Metadata r = RecordingIO.readMetadata(in, RecordingIO.MAX_METADATA_EVENTS,
						Math.min(remainingBytes, RecordingIO.MAX_UNCOMPRESSED_BYTES));
					remainingBytes -= r.uncompressedBytesRead();
					result.add(new ReplayMetadata(r.label(), r.durationTicks(), r.events()));
				} catch (IOException e) {
					SparBot.LOGGER.warn("Skipping unreadable recording metadata {}: {}", file, e.getMessage());
					break;
				}
			}
		}
		metadataCache = List.copyOf(result);
		return metadataCache;
	}
}

package io.github.flick256.sparbot.core.record;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The {@code .spbr} file format: a gzipped, versioned binary stream. A header (label, mode, world,
 * start time, fighters) is followed by the frame count and the frames; each sample stores its fields
 * in a fixed order. Item kinds are stored by name, so adding a kind never breaks old files.
 */
public final class RecordingIO {
	private static final int MAGIC = 0x53504252; // "SPBR"
	public static final int VERSION = 2;
	public static final int MAX_FIGHTERS = 16;
	public static final int MAX_FRAMES = 24 * 60 * 60 * 20;
	public static final int MAX_EVENTS = 200_000;
	public static final long MAX_UNCOMPRESSED_BYTES = 128L * 1024 * 1024;
	public static final long MAX_FILE_BYTES = 160L * 1024 * 1024;
	public static final int MAX_METADATA_EVENTS = 16;
	public record Metadata(String label, int durationTicks, List<ReplayEvent> events, long uncompressedBytesRead) { }

	private static final int JUMP = 1;
	private static final int SNEAK = 2;
	private static final int SPRINT = 4;
	private static final int ATTACK = 8;
	private static final int USE = 16;
	private static final int SWAP = 32;
	private static final int INVENTORY = 64;

	private RecordingIO() {
	}

	public static void write(Recording recording, OutputStream out) throws IOException {
		if (recording.fighters().isEmpty() || recording.fighters().size() > MAX_FIGHTERS) throw new IOException("Invalid fighter count " + recording.fighters().size());
		if (recording.frames().size() > MAX_FRAMES) throw new IOException("Invalid frame count " + recording.frames().size());
		if (recording.events().size() > MAX_EVENTS) throw new IOException("Invalid event count " + recording.events().size());
		GZIPOutputStream zip = new GZIPOutputStream(out);
		DataOutputStream data = new DataOutputStream(new BoundedOutputStream(zip, MAX_UNCOMPRESSED_BYTES));
		data.writeInt(MAGIC);
		data.writeInt(VERSION);
		data.writeUTF(recording.label());
		data.writeUTF(recording.mode());
		data.writeUTF(recording.dimension());
		data.writeLong(recording.startedAtMillis());
		data.writeInt(recording.fighters().size());
		for (FighterInfo f : recording.fighters()) {
			data.writeUTF(f.name());
			data.writeBoolean(f.bot());
			data.writeUTF(f.profile());
			data.writeUTF(f.style());
		}
		data.writeInt(recording.frames().size());
		for (Frame frame : recording.frames()) {
			if (frame.samples().length != recording.fighters().size()) throw new IOException("Frame fighter count does not match recording header");
			data.writeLong(frame.tick());
			for (Sample s : frame.samples()) {
				data.writeBoolean(s != null);
				if (s != null) {
					writeSample(data, s);
				}
			}
		}
		data.writeInt(recording.events().size());
		for (ReplayEvent event : recording.events()) {
			data.writeInt(event.tick()); data.writeUTF(event.kind()); data.writeUTF(event.text());
		}
		data.flush();
		zip.finish();
	}

	public static Recording read(InputStream in) throws IOException {
		DataInputStream data = new DataInputStream(new BoundedInputStream(new GZIPInputStream(in), MAX_UNCOMPRESSED_BYTES));
		if (data.readInt() != MAGIC) {
			throw new IOException("Not a SparBot recording");
		}
		int version = data.readInt();
		if (version != 1 && version != VERSION) {
			throw new IOException("Unsupported recording version " + version + " (this SparBot reads version " + VERSION + ")");
		}
		String label = data.readUTF();
		String mode = data.readUTF();
		String dimension = data.readUTF();
		long started = data.readLong();
		int fighterCount = data.readInt();
		if (fighterCount < 1 || fighterCount > MAX_FIGHTERS) throw new IOException("Invalid fighter count " + fighterCount);
		List<FighterInfo> fighters = new ArrayList<>(fighterCount);
		for (int i = 0; i < fighterCount; i++) {
			fighters.add(new FighterInfo(data.readUTF(), data.readBoolean(), data.readUTF(), data.readUTF()));
		}
		int frameCount = data.readInt();
		if (frameCount < 0 || frameCount > MAX_FRAMES) throw new IOException("Invalid frame count " + frameCount);
		List<Frame> frames = new ArrayList<>(frameCount);
		for (int i = 0; i < frameCount; i++) {
			long tick = data.readLong();
			Sample[] samples = new Sample[fighterCount];
			for (int f = 0; f < fighterCount; f++) {
				samples[f] = data.readBoolean() ? readSample(data) : null;
			}
			frames.add(new Frame(tick, samples));
		}
		List<ReplayEvent> events = new ArrayList<>();
		if (version >= 2) {
			int eventCount = data.readInt();
			if (eventCount < 0 || eventCount > MAX_EVENTS) throw new IOException("Invalid event count " + eventCount);
			for (int i = 0; i < eventCount; i++) events.add(new ReplayEvent(data.readInt(), data.readUTF(), data.readUTF()));
		}
		return new Recording(label, mode, dimension, started, fighters, frames, events);
	}

	/** Reads menu data while streaming past samples without allocating frame/sample objects. */
	public static Metadata readMetadata(InputStream in, int maxEvents) throws IOException {
		return readMetadata(in, maxEvents, MAX_UNCOMPRESSED_BYTES);
	}

	public static Metadata readMetadata(InputStream in, int maxEvents, long maxUncompressedBytes) throws IOException {
		if (maxEvents < 0 || maxEvents > MAX_METADATA_EVENTS) throw new IllegalArgumentException("metadata event limit out of range");
		if (maxUncompressedBytes < 1 || maxUncompressedBytes > MAX_UNCOMPRESSED_BYTES) throw new IllegalArgumentException("metadata byte limit out of range");
		BoundedInputStream bounded = new BoundedInputStream(new GZIPInputStream(in), maxUncompressedBytes);
		DataInputStream data = new DataInputStream(bounded);
		if (data.readInt() != MAGIC) throw new IOException("Not a SparBot recording");
		int version = data.readInt();
		if (version != 1 && version != VERSION) throw new IOException("Unsupported recording version " + version);
		String label = data.readUTF(); data.readUTF(); data.readUTF(); data.readLong();
		int fighterCount = data.readInt();
		if (fighterCount < 1 || fighterCount > MAX_FIGHTERS) throw new IOException("Invalid fighter count " + fighterCount);
		for (int i = 0; i < fighterCount; i++) { data.readUTF(); data.readBoolean(); data.readUTF(); data.readUTF(); }
		int frameCount = data.readInt();
		if (frameCount < 0 || frameCount > MAX_FRAMES) throw new IOException("Invalid frame count " + frameCount);
		for (int i = 0; i < frameCount; i++) {
			data.readLong();
			for (int f = 0; f < fighterCount; f++) if (data.readBoolean()) skipSample(data);
		}
		if (version == 1) return new Metadata(label, frameCount, List.of(), bounded.bytesRead());
		int eventCount = data.readInt();
		if (eventCount < 0 || eventCount > MAX_EVENTS) throw new IOException("Invalid event count " + eventCount);
		List<ReplayEvent> events = new ArrayList<>(Math.min(eventCount, maxEvents));
		for (int i = 0; i < eventCount; i++) {
			ReplayEvent event = new ReplayEvent(data.readInt(), data.readUTF(), data.readUTF());
			if (events.size() < maxEvents) events.add(event);
		}
		return new Metadata(label, frameCount, events, bounded.bytesRead());
	}

	private static void skipSample(DataInputStream data) throws IOException {
		for (int i = 0; i < 6; i++) data.readDouble();
		for (int i = 0; i < 5; i++) data.readFloat();
		data.readBoolean(); data.readByte(); data.readByte();
		data.readUTF(); data.readUTF(); data.readUTF(); data.readUTF();
		data.readFloat(); data.readFloat();
		for (int i = 0; i < 6; i++) data.readByte();
	}

	private static final class BoundedInputStream extends FilterInputStream {
		private final long limit;
		private long remaining;
		BoundedInputStream(InputStream in, long limit) { super(in); this.limit = limit; remaining = limit; }
		long bytesRead() { return limit - remaining; }
		@Override public int read() throws IOException {
			if (remaining == 0) { if (in.read() < 0) return -1; throw new IOException("Recording exceeds maximum uncompressed size"); }
			int value = in.read(); if (value >= 0) remaining--; return value;
		}
		@Override public int read(byte[] bytes, int offset, int length) throws IOException {
			if (length == 0) return 0;
			if (remaining == 0) { if (in.read() < 0) return -1; throw new IOException("Recording exceeds maximum uncompressed size"); }
			int count = in.read(bytes, offset, (int) Math.min(length, remaining));
			if (count > 0) remaining -= count;
			return count;
		}
	}
	private static final class BoundedOutputStream extends FilterOutputStream {
		private long remaining;
		BoundedOutputStream(OutputStream out, long limit) { super(out); remaining = limit; }
		@Override public void write(int value) throws IOException {
			if (remaining == 0) throw new IOException("Recording exceeds maximum uncompressed size");
			out.write(value); remaining--;
		}
		@Override public void write(byte[] bytes, int offset, int length) throws IOException {
			if (length > remaining) throw new IOException("Recording exceeds maximum uncompressed size");
			out.write(bytes, offset, length); remaining -= length;
		}
	}

	private static void writeSample(DataOutputStream data, Sample s) throws IOException {
		writeVec(data, s.position());
		writeVec(data, s.velocity());
		data.writeFloat(s.yaw());
		data.writeFloat(s.pitch());
		data.writeFloat(s.health());
		data.writeFloat(s.absorption());
		data.writeFloat(s.attackStrength());
		data.writeBoolean(s.onGround());
		data.writeByte(s.hurtTime());
		data.writeByte(s.selectedSlot());
		data.writeUTF(s.mainHand().name());
		data.writeUTF(s.offhand().name());
		data.writeUTF(s.usingKind().name());
		data.writeUTF(s.mainHandId());
		Inputs in = s.inputs();
		data.writeFloat(in.yawDelta());
		data.writeFloat(in.pitchDelta());
		data.writeByte(in.forward());
		data.writeByte(in.strafe());
		int flags = (in.jump() ? JUMP : 0) | (in.sneak() ? SNEAK : 0) | (in.sprint() ? SPRINT : 0) | (in.attack() ? ATTACK : 0) | (in.use() ? USE : 0)
			| (in.swapOffhand() ? SWAP : 0) | (in.inventoryOpen() ? INVENTORY : 0);
		data.writeByte(flags);
		data.writeByte(in.hotbarSlot());
		InventoryClick click = in.inventoryClick();
		data.writeByte(click == null ? -1 : click.slot());
		data.writeByte(click == null ? -1 : click.button());
	}

	private static Sample readSample(DataInputStream data) throws IOException {
		Vec3 position = readVec(data);
		Vec3 velocity = readVec(data);
		float yaw = data.readFloat();
		float pitch = data.readFloat();
		float health = data.readFloat();
		float absorption = data.readFloat();
		float attackStrength = data.readFloat();
		boolean onGround = data.readBoolean();
		int hurtTime = data.readByte();
		int selected = data.readByte();
		ItemKind mainHand = kind(data.readUTF());
		ItemKind offhand = kind(data.readUTF());
		ItemKind using = kind(data.readUTF());
		String mainHandId = data.readUTF();
		float yawDelta = data.readFloat();
		float pitchDelta = data.readFloat();
		int forward = data.readByte();
		int strafe = data.readByte();
		int flags = data.readByte();
		int hotbar = data.readByte();
		int clickSlot = data.readByte();
		int clickButton = data.readByte();
		InventoryClick click = clickSlot < 0 ? null : new InventoryClick(clickSlot, clickButton);
		Inputs inputs = new Inputs(yawDelta, pitchDelta, forward, strafe, (flags & JUMP) != 0, (flags & SNEAK) != 0, (flags & SPRINT) != 0,
			(flags & ATTACK) != 0, (flags & USE) != 0, hotbar, (flags & SWAP) != 0, (flags & INVENTORY) != 0, click);
		return new Sample(position, velocity, yaw, pitch, health, absorption, attackStrength, onGround, hurtTime, selected, mainHand, offhand, using,
			mainHandId, inputs);
	}

	private static void writeVec(DataOutputStream data, Vec3 v) throws IOException {
		data.writeDouble(v.x());
		data.writeDouble(v.y());
		data.writeDouble(v.z());
	}

	private static Vec3 readVec(DataInputStream data) throws IOException {
		return new Vec3(data.readDouble(), data.readDouble(), data.readDouble());
	}

	/** Unknown names (a kind removed in a later version) read as OTHER. */
	private static ItemKind kind(String name) {
		try {
			return ItemKind.valueOf(name);
		} catch (IllegalArgumentException e) {
			return ItemKind.OTHER;
		}
	}
}

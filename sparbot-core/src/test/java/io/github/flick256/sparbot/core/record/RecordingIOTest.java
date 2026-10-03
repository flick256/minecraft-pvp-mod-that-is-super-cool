package io.github.flick256.sparbot.core.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class RecordingIOTest {
	private static Sample sample(double x, Inputs inputs) {
		return new Sample(new Vec3(x, 64, -3.25), new Vec3(0.1, -0.08, 0), 12.5F, -7.0F, 17.5F, 4.0F, 0.93F, true, 9, 3, ItemKind.SWORD,
			ItemKind.TOTEM, ItemKind.EMPTY, "minecraft:diamond_sword", inputs);
	}

	@Test
	void roundTripsEveryField() throws IOException {
		Inputs a = new Inputs(1.5F, -0.25F, 1, -1, true, false, true, true, false, 4, true, false, null);
		Inputs b = Inputs.inInventory(new InventoryClick(21, 40));
		Recording original = new Recording("duel 1", "nodebuff", "minecraft:overworld", 1_700_000_000_000L,
			List.of(new FighterInfo("Steve", false, "", ""), new FighterInfo("Bot1", true, "pro", "wtap_combo")),
			List.of(new Frame(100, new Sample[] {sample(1, a), sample(2, b)}), new Frame(101, new Sample[] {null, sample(3, Inputs.IDLE)})));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RecordingIO.write(original, out);
		Recording read = RecordingIO.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(original, read);
		assertNull(read.frames().get(1).samples()[0], "an absent fighter stays absent");
		assertEquals(2, read.durationTicks());
	}

	@Test
	void rejectsOtherFiles() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
			zip.write(new byte[] {1, 2, 3, 4, 0, 0, 0, 1});
		}
		IOException e = assertThrows(IOException.class, () -> RecordingIO.read(new ByteArrayInputStream(out.toByteArray())));
		assertEquals("Not a SparBot recording", e.getMessage());
	}
}

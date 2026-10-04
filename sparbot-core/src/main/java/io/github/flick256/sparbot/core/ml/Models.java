package io.github.flick256.sparbot.core.ml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Learned models shipped with SparBot ({@code /sparbot/models/<id>.json}). */
public final class Models {
	/**
	 * sword: melee for sword duels; uhc: melee with shield, axe and golden apples, for UHC. Both trained
	 * by imitating the scripted pro, then self-play.
	 */
	public static final List<String> BUNDLED_IDS = List.of("sword", "uhc");

	private Models() {
	}

	public static Mlp parse(Reader reader) throws IOException {
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[8192];
		int n;
		while ((n = reader.read(buf)) > 0) {
			sb.append(buf, 0, n);
		}
		return Mlp.fromJson(sb.toString());
	}

	public static Map<String, Mlp> loadBundled() {
		Map<String, Mlp> models = new LinkedHashMap<>();
		for (String id : BUNDLED_IDS) {
			try (InputStream in = Models.class.getResourceAsStream("/sparbot/models/" + id + ".json")) {
				if (in == null) {
					throw new IllegalStateException("bundled model missing: " + id);
				}
				models.put(id, parse(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException e) {
				throw new IllegalStateException("bundled model unreadable: " + id, e);
			}
		}
		return models;
	}
}

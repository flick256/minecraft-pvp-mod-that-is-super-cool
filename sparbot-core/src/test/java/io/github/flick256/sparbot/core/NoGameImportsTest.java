package io.github.flick256.sparbot.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.math.Vec3;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the architecture rule: the core never references Minecraft or Fabric classes, so it can
 * be unit tested and reused for offline training without a game. Scans the constant pool of every
 * compiled class for the internal package names.
 */
class NoGameImportsTest {
	private static final List<String> FORBIDDEN = List.of("net/minecraft", "net/fabricmc", "com/mojang");

	@Test
	void coreClassesNeverReferenceGameClasses() throws IOException, URISyntaxException {
		Path classesRoot = Path.of(Vec3.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		List<String> violations = new ArrayList<>();
		int scanned = 0;
		try (Stream<Path> files = Files.walk(classesRoot)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
				scanned++;
				// Class files store referenced class names as UTF-8 in the constant pool.
				String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
				for (String forbidden : FORBIDDEN) {
					if (content.contains(forbidden)) {
						violations.add(classesRoot.relativize(file) + " references " + forbidden);
					}
				}
			}
		}
		assertTrue(scanned > 10, "expected to scan the core classes, found " + scanned + " in " + classesRoot);
		assertTrue(violations.isEmpty(), "Core must not depend on the game:\n" + String.join("\n", violations));
	}
}

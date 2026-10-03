package io.github.flick256.sparbot.match;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.flick256.sparbot.SparBot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Arenas from {@code config/sparbot/arenas/<id>.json} with a block snapshot in {@code <id>.nbt}
 * (vanilla structure format). Resetting an arena places the snapshot back, including air, so blocks
 * placed during a round (webs, obsidian...) disappear, and removes loose entities (drops, arrows,
 * pearls, hooks, xp, TNT) inside it.
 */
public final class ArenaRegistry {
	/** Largest arena volume, to keep snapshots and resets cheap. */
	public static final long MAX_VOLUME = 2_000_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Map<String, Arena> arenas = new LinkedHashMap<>();
	private final Map<String, StructureTemplate> snapshots = new LinkedHashMap<>();
	private Path dir;

	public void reload(Path dir, MinecraftServer server) {
		this.dir = dir;
		arenas.clear();
		snapshots.clear();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			SparBot.LOGGER.warn("Could not create {}: {}", dir, e.getMessage());
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				try {
					Arena arena = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Arena.class);
					Path nbt = dir.resolve(arena.id() + ".nbt");
					if (!Files.exists(nbt)) {
						SparBot.LOGGER.error("Arena {} has no snapshot {}; skipping", arena.id(), nbt.getFileName());
						continue;
					}
					StructureTemplate template = new StructureTemplate();
					template.load(server.registryAccess().lookupOrThrow(Registries.BLOCK), NbtIo.readCompressed(nbt, NbtAccounter.unlimitedHeap()));
					arenas.put(arena.id(), arena);
					snapshots.put(arena.id(), template);
				} catch (Exception e) {
					SparBot.LOGGER.error("Skipping arena {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not list {}: {}", dir, e.getMessage());
		}
	}

	/** Creates (or redefines) an arena from two corners, snapshotting the blocks as they are now. */
	public Arena create(String id, ServerLevel level, BlockPos a, BlockPos b) throws IOException {
		if (!id.matches("[a-z0-9_\\-]+")) {
			throw new IllegalArgumentException("Arena ids must be lowercase [a-z0-9_-]+");
		}
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
		long volume = (long) size.getX() * size.getY() * size.getZ();
		if (volume > MAX_VOLUME) {
			throw new IllegalArgumentException("Arena is " + volume + " blocks; the limit is " + MAX_VOLUME);
		}
		Arena old = arenas.get(id);
		Arena arena = new Arena(id, level.dimension().identifier().toString(), min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(),
			old != null ? old.spawnA() : null, old != null ? old.spawnB() : null);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, min, size, false, List.<Block>of());
		arenas.put(id, arena);
		snapshots.put(id, template);
		save(arena);
		NbtIo.writeCompressed(template.save(new CompoundTag()), dir.resolve(id + ".nbt"));
		return arena;
	}

	public Arena setSpawn(String id, boolean sideA, Arena.Spawn spawn) throws IOException {
		Arena arena = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown arena " + id));
		Arena updated = arena.withSpawn(sideA, spawn);
		arenas.put(id, updated);
		save(updated);
		return updated;
	}

	private void save(Arena arena) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(arena.id() + ".json"), GSON.toJson(arena), StandardCharsets.UTF_8);
	}

	public @Nullable ServerLevel level(MinecraftServer server, Arena arena) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(arena.dimension())));
	}

	/** Puts the snapshot back and clears loose entities. Returns the number of entities removed. */
	public int reset(MinecraftServer server, Arena arena) {
		ServerLevel level = level(server, arena);
		StructureTemplate snapshot = snapshots.get(arena.id());
		if (level == null || snapshot == null) {
			return 0;
		}
		int removed = clearLooseEntities(level, arena);
		snapshot.placeInWorld(level, arena.min(), arena.min(), new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), Block.UPDATE_CLIENTS);
		return removed;
	}

	/** Removes drops, projectiles (arrows, pearls in flight, hooks, tridents), xp orbs and primed TNT. */
	public static int clearLooseEntities(ServerLevel level, Arena arena) {
		List<Entity> loose = level.getEntities((Entity) null, arena.box().inflate(2),
			e -> e instanceof ItemEntity || e instanceof Projectile || e instanceof ExperienceOrb || e instanceof PrimedTnt);
		loose.forEach(Entity::discard);
		return loose.size();
	}

	public Optional<Arena> get(String id) {
		return Optional.ofNullable(arenas.get(id));
	}

	public Collection<String> ids() {
		return arenas.keySet();
	}
}

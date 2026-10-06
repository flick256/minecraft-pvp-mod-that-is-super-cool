package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.practice.PracticeLabels;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Builds the Celestial Colosseum and puts it back after a fight, a little each tick so the server never
 * stalls: about a twentieth of a second's work per tick at most, chunk by chunk from the middle outwards, all
 * the solid blocks first and then the ones that hang on them. A first build takes a minute or two (players
 * in the practice world see how far it has got); a reset only goes over the chunks that players and bots
 * were in, so it is usually done in a few seconds.
 */
public final class ColosseumWorks {
	/** Bumped when the blueprint changes, so worlds with an older colosseum get it built again. */
	public static final int VERSION = 3;
	/** At most this much building per tick. */
	private static final long BUDGET_NANOS = 25_000_000L;
	/** Chunks round each player or bot that count as visited (two each way: blasts, lava and water spread). */
	private static final int VISIT_RADIUS = 2;

	private final ServerLevel level;
	private final int minChunkX = (CelestialColosseum.CX - CelestialColosseum.REACH) >> 4;
	private final int maxChunkX = (CelestialColosseum.CX + CelestialColosseum.REACH) >> 4;
	private final int minChunkZ = (CelestialColosseum.CZ - CelestialColosseum.REACH) >> 4;
	private final int maxChunkZ = (CelestialColosseum.CZ + CelestialColosseum.REACH) >> 4;
	private boolean built;
	private int failures;
	/** The job running: its chunks, which one it is on, how far through it, and whether on the hanging blocks yet. */
	private Job job;
	/** Chunks players or bots were in since the last reset. */
	private final LongLinkedOpenHashSet visited = new LongLinkedOpenHashSet();

	private static final class Job {
		final boolean build;
		final LongList chunks;
		int chunk;
		int column;
		boolean attached;
		long started = System.currentTimeMillis();
		int changed;

		Job(boolean build, LongList chunks) {
			this.build = build;
			this.chunks = chunks;
		}

		double progress() {
			return ((attached ? chunks.size() : 0) + chunk) / (2.0 * Math.max(1, chunks.size()));
		}
	}

	public ColosseumWorks(ServerLevel level) {
		this.level = level;
		this.built = level.getBlockState(marker()).is(Blocks.LODESTONE);
		// The sculptures take a moment to work out: do it off the server thread.
		CompletableFuture.runAsync(Sculptures::get);
	}

	public ServerLevel level() {
		return level;
	}

	private static BlockPos marker() {
		return new BlockPos(CelestialColosseum.CX, CelestialColosseum.Y0 - 2, CelestialColosseum.CZ + VERSION);
	}

	/** Whether it is built and open. */
	public boolean ready() {
		return built;
	}

	/** Whether a first build is going on. */
	public boolean building() {
		return job != null && job.build;
	}

	/** How far the first build has got, 0 to 1. */
	public double progress() {
		return built ? 1 : job == null ? 0 : job.progress();
	}

	/** Starts building it, if it isn't built and isn't being built. */
	public void start() {
		if (built || building()) {
			return;
		}
		LongArrayList chunks = new LongArrayList();
		for (int x = minChunkX; x <= maxChunkX; x++) {
			for (int z = minChunkZ; z <= maxChunkZ; z++) {
				chunks.add(ChunkPos.pack(x, z));
			}
		}
		// From the middle outwards, so it rises round the field first.
		int cx = CelestialColosseum.CX >> 4;
		int cz = CelestialColosseum.CZ >> 4;
		chunks.sort((a, b) -> Long.compare(dist2(a, cx, cz), dist2(b, cx, cz)));
		job = new Job(true, chunks);
		SparBot.LOGGER.info("Building the Celestial Colosseum ({} chunks)", chunks.size());
	}

	private static long dist2(long chunk, int cx, int cz) {
		long dx = ChunkPos.getX(chunk) - cx;
		long dz = ChunkPos.getZ(chunk) - cz;
		return dx * dx + dz * dz;
	}

	/** Whether (x, z) is in the colosseum's square (with a margin). */
	public static boolean inside(double x, double z, double margin) {
		return Math.abs(x - CelestialColosseum.CX) <= CelestialColosseum.REACH + margin
			&& Math.abs(z - CelestialColosseum.CZ) <= CelestialColosseum.REACH + margin;
	}

	public void tick() {
		long tick = level.getServer().getTickCount();
		if (tick % 20 == 0) {
			watch();
		}
		if (job == null) {
			return;
		}
		work();
		if (job != null && job.build && tick % 40 == 0) {
			Component line = Component.literal("Raising the Celestial Colosseum: " + Math.round(100 * job.progress()) + "%")
				.withStyle(st -> st.withColor(0xC77DFF));
			for (ServerPlayer p : level.players()) {
				if (!(p instanceof BotPlayer)) {
					p.sendOverlayMessage(line);
				}
			}
		}
	}

	/**
	 * Once a second: which chunks people and bots are in, and whether any player is still here. When the last
	 * player has gone, loose items, arrows, crystals and carts are cleared and the chunks they were in put
	 * back; someone coming in again stops that (it carries on once they leave).
	 */
	private void watch() {
		boolean occupied = false;
		for (ServerPlayer p : level.players()) {
			if (!inside(p.getX(), p.getZ(), 8)) {
				continue;
			}
			if (!(p instanceof BotPlayer)) {
				occupied = true;
			}
			int px = p.getBlockX() >> 4;
			int pz = p.getBlockZ() >> 4;
			for (int x = Math.max(minChunkX, px - VISIT_RADIUS); x <= Math.min(maxChunkX, px + VISIT_RADIUS); x++) {
				for (int z = Math.max(minChunkZ, pz - VISIT_RADIUS); z <= Math.min(maxChunkZ, pz + VISIT_RADIUS); z++) {
					visited.add(ChunkPos.pack(x, z));
				}
			}
		}
		if (!built) {
			return;
		}
		if (occupied) {
			if (job != null && !job.build) {
				// Put back what's left of the reset for later.
				for (int i = job.chunk; i < job.chunks.size(); i++) {
					visited.add(job.chunks.getLong(i));
				}
				if (job.attached) {
					visited.addAll(job.chunks);
				}
				job = null;
			}
			return;
		}
		if (job == null && !visited.isEmpty()) {
			clearLoose();
			job = new Job(false, new LongArrayList(visited));
			visited.clear();
		}
	}

	private void clearLoose() {
		int r = CelestialColosseum.REACH;
		AABB box = new AABB(CelestialColosseum.CX - r, CelestialColosseum.Y0 - 20, CelestialColosseum.CZ - r, CelestialColosseum.CX + r + 1,
			CelestialColosseum.Y1 + 30, CelestialColosseum.CZ + r + 1);
		for (Entity e : level.getEntities((Entity) null, box, ColosseumWorks::loose)) {
			e.discard();
		}
	}

	private static boolean loose(Entity e) {
		return e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile
			|| e instanceof net.minecraft.world.entity.ExperienceOrb || e instanceof net.minecraft.world.entity.item.PrimedTnt
			|| e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
			|| e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
			|| e instanceof net.minecraft.world.entity.item.FallingBlockEntity;
	}

	/** A tick's worth of the job. */
	private void work() {
		long deadline = System.nanoTime() + BUDGET_NANOS;
		Job j = job;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (System.nanoTime() < deadline) {
			if (j.chunk >= j.chunks.size()) {
				if (!j.attached) {
					j.attached = true;
					j.chunk = 0;
					j.column = 0;
					continue;
				}
				finish(j);
				return;
			}
			long packed = j.chunks.getLong(j.chunk);
			int chunkX = ChunkPos.getX(packed);
			int chunkZ = ChunkPos.getZ(packed);
			long before = System.nanoTime();
			LevelChunk chunk = level.getChunk(chunkX, chunkZ);
			long loaded = System.nanoTime() - before;
			if (loaded > 200_000_000L) {
				SparBot.LOGGER.info("Loading chunk {}, {} for the colosseum took {} ms", chunkX, chunkZ, loaded / 1_000_000);
			}
			// A few columns at a time between looks at the clock.
			for (int n = 0; n < 8 && j.column < 256; n++, j.column++) {
				int x = (chunkX << 4) + (j.column & 15);
				int z = (chunkZ << 4) + (j.column >> 4);
				long before2 = System.nanoTime();
				j.changed += apply(chunk, x, z, j.attached, pos);
				long took = System.nanoTime() - before2;
				if (took > 200_000_000L) {
					SparBot.LOGGER.info("Column {}, {} of the colosseum took {} ms", x, z, took / 1_000_000);
				}
			}
			if (j.column >= 256) {
				if (j.build && !j.attached) {
					biome(chunkX, chunkZ);
				}
				j.column = 0;
				j.chunk++;
			}
		}
	}

	/** Puts column (x, z) as the blueprint has it: the solid blocks, or the hanging ones. Returns how many blocks changed. */
	private int apply(LevelChunk chunk, int x, int z, boolean attachedPass, BlockPos.MutableBlockPos pos) {
		int dx = x - CelestialColosseum.CX;
		int dz = z - CelestialColosseum.CZ;
		if (Math.abs(dx) > CelestialColosseum.REACH || Math.abs(dz) > CelestialColosseum.REACH) {
			return 0;
		}
		BlockState[] col = CelestialColosseum.column(dx, dz);
		if (col == null) {
			return 0;
		}
		// Above both the blueprint's top block and the world's, there is nothing to do.
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
		for (int i = col.length - 1; i >= 0; i--) {
			if (!col[i].isAir()) {
				top = Math.max(top, CelestialColosseum.Y0 + i);
				break;
			}
		}
		int changed = 0;
		for (int y = CelestialColosseum.Y0; y <= Math.min(top, CelestialColosseum.Y1); y++) {
			BlockState want = col[y - CelestialColosseum.Y0];
			if (CelestialColosseum.attached(want) != attachedPass) {
				continue;
			}
			pos.set(x, y, z);
			if (!chunk.getBlockState(pos).equals(want)) {
				try {
					level.setBlock(pos, want, Block.UPDATE_CLIENTS);
					if (want.getBlock() instanceof net.minecraft.world.level.block.SignBlock || want.is(Blocks.LECTERN)) {
						// What the sign says, or the book on the lectern.
						ColosseumLore.apply(level, pos, dx, y, dz);
					}
					changed++;
				} catch (RuntimeException e) {
					// One block that won't go in must never take the server down with it.
					if (failures++ < 5) {
						SparBot.LOGGER.warn("Could not place {} at {} in the colosseum: {}", want, pos, e.toString());
					}
				}
			}
		}
		return changed;
	}

	/** Cherry grove over the whole thing, so the grass and gardens are green (the desert tints them olive). */
	private void biome(int chunkX, int chunkZ) {
		Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.CHERRY_GROVE);
		int x0 = Math.max(chunkX << 4, CelestialColosseum.CX - CelestialColosseum.REACH);
		int z0 = Math.max(chunkZ << 4, CelestialColosseum.CZ - CelestialColosseum.REACH);
		int x1 = Math.min((chunkX << 4) + 15, CelestialColosseum.CX + CelestialColosseum.REACH);
		int z1 = Math.min((chunkZ << 4) + 15, CelestialColosseum.CZ + CelestialColosseum.REACH);
		FillBiomeCommand.fill(level, new BlockPos(x0, CelestialColosseum.Y0, z0), new BlockPos(x1, CelestialColosseum.Y1, z1), holder);
	}

	private void finish(Job j) {
		job = null;
		long ms = System.currentTimeMillis() - j.started;
		if (j.build) {
			built = true;
			level.setBlock(marker(), Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			labels();
			SparBot.LOGGER.info("Built the Celestial Colosseum in {} s ({} blocks)", ms / 1000, j.changed);
			Component done = Component.literal("The Celestial Colosseum is open: its pad is south of the hub's plaza.")
				.withStyle(st -> st.withColor(0xC77DFF).withBold(true));
			for (ServerPlayer p : level.players()) {
				if (!(p instanceof BotPlayer)) {
					p.sendSystemMessage(done);
				}
			}
		} else {
			SparBot.LOGGER.info("Reset the Celestial Colosseum ({} chunks, {} blocks) in {} ms", j.chunks.size(), j.changed, ms);
		}
	}

	/** The name over the great south gate, the satellite stadiums' names and the pads back to the hub. */
	private void labels() {
		int cx = CelestialColosseum.CX;
		int cz = CelestialColosseum.CZ;
		int f = PracticeLayout.FLOOR;
		PracticeLabels.put(level, cx + 0.5, f + 40, cz + 155.5, Component.literal("The Celestial Colosseum")
			.withStyle(st -> st.withColor(0xC77DFF).withBold(true)), 12.0F, 0);
		for (int i = 0; i < 4; i++) {
			double[] s = CelestialColosseum.satelliteCentre(i);
			double len = Math.hypot(s[0], s[1]);
			// Over its gate, which faces the bowl.
			double gx = s[0] - s[0] / len * 50;
			double gz = s[1] - s[1] / len * 50;
			String name = CelestialColosseum.THEMES[i].name();
			int color = new int[] {0xFF7A33, 0x9BE7FF, 0x8CE07A, 0xD08CFF}[i];
			PracticeLabels.put(level, cx + gx + 0.5, f + 22, cz + gz + 0.5, Component.literal("The " + name + " Stadium")
				.withStyle(st -> st.withColor(color).withBold(true)), 5.0F, 0);
		}
		for (int z : new int[] {CelestialColosseum.RETURN_ARRIVAL, CelestialColosseum.RETURN_TUNNEL}) {
			PracticeLabels.put(level, cx + 0.5, f + 1.6, cz + z + 0.5, Component.literal("Back to the hub")
				.withStyle(st -> st.withColor(0x55FFFF).withBold(true)), 0.8F, 0x60000000);
		}
	}
}

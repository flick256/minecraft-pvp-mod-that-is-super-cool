package io.github.flick256.sparbot.practice;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.practice.PracticeLayout.Site;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.match.Arena;
import io.github.flick256.sparbot.match.MatchManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The practice world: SparBot's own flat desert with a hub and an arena per kind of fight. It is the
 * {@code sparbot:practice} dimension in any world, or the whole overworld of a world made with the
 * "SparBot Practice" world type. It is built the first time it is needed (or at once, in a practice
 * world), the arenas are registered like any other (and reset after every round), and fights started from
 * the menu's Practice tab or {@code /sparbot practice fight} go to the arena for their mode.
 */
public final class PracticeWorld {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("sparbot", "practice"));
	public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("sparbot", "practice"));
	/** Standing on a pad this long takes you. */
	private static final int PAD_TICKS = 10;
	private static final int PAD_COOLDOWN = 60;

	private final Map<UUID, Integer> onPad = new HashMap<>();
	private final Map<UUID, Long> padUsed = new HashMap<>();
	/** Where each player was before they first came to the practice world, for /sparbot practice leave. */
	private final Map<UUID, Return> returns = new HashMap<>();

	private record Return(ResourceKey<Level> level, Vec3 pos, float yaw, float pitch) {
	}

	/** The overworld of a world made with the SparBot Practice world type, else the practice dimension. */
	public static @Nullable ServerLevel level(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld.dimensionTypeRegistration().is(TYPE)) {
			return overworld;
		}
		return server.getLevel(DIMENSION);
	}

	/** Whether this world was made with the SparBot Practice world type. */
	public static boolean isPracticeWorld(MinecraftServer server) {
		return server.overworld().dimensionTypeRegistration().is(TYPE);
	}

	/** On server start: a practice world is built straight away, so its players spawn in the hub. */
	public void onServerStarted(MinecraftServer server) {
		if (isPracticeWorld(server)) {
			try {
				ensure(server);
			} catch (IOException | IllegalStateException e) {
				SparBot.LOGGER.error("Could not build the practice world: {}", e.getMessage());
			}
		}
	}

	/** Builds the hub and arenas if this world doesn't have them yet (or has an older version), and registers the arenas. */
	public ServerLevel ensure(MinecraftServer server) throws IOException {
		ServerLevel level = level(server);
		if (level == null) {
			throw new IllegalStateException("The practice dimension isn't loaded (is SparBot's data pack enabled?)");
		}
		PracticeBuilder builder = new PracticeBuilder(level);
		builder.load(-PracticeLayout.HUB_RADIUS, -PracticeLayout.HUB_RADIUS, PracticeLayout.HUB_RADIUS, PracticeLayout.HUB_RADIUS);
		boolean fresh = !PracticeBuilder.built(level);
		if (fresh) {
			long start = System.currentTimeMillis();
			builder.buildAll();
			SparBot.LOGGER.info("Built the practice world (version {}) in {} ms", PracticeLayout.VERSION, System.currentTimeMillis() - start);
		}
		String dimension = level.dimension().identifier().toString();
		for (Site site : PracticeLayout.SITES) {
			Arena arena = SparBot.matches().arenas().get(site.arenaId()).orElse(null);
			if (fresh || arena == null || !arena.dimension().equals(dimension) || !arena.ready()) {
				builder.load(site.minX(), site.minZ(), site.maxX(), site.maxZ());
				SparBot.matches().arenas().create(site.arenaId(), level, new BlockPos(site.minX(), site.minY(), site.minZ()),
					new BlockPos(site.maxX(), site.maxY(), site.maxZ()));
				double y = PracticeLayout.FLOOR + (site == PracticeLayout.UHC ? PracticeLayout.spawnHeight() : 0);
				SparBot.matches().arenas().setSpawn(site.arenaId(), true,
					new Arena.Spawn(site.centerX() + 0.5, y, site.centerZ() - site.spawnOffset() + 0.5, 0));
				SparBot.matches().arenas().setSpawn(site.arenaId(), false,
					new Arena.Spawn(site.centerX() + 0.5, y, site.centerZ() + site.spawnOffset() + 0.5, 180));
			}
		}
		if (fresh && level == server.overworld()) {
			level.setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), new BlockPos(0, PracticeLayout.FLOOR, -4), 0, 0));
		}
		return level;
	}

	/** Takes a player to the hub, remembering where they came from. */
	public void toHub(ServerPlayer player) throws IOException {
		ServerLevel level = ensure(player.level().getServer());
		if (player.level() != level) {
			returns.put(player.getUUID(), new Return(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
		}
		player.teleportTo(level, 0.5, PracticeLayout.FLOOR, -3.5, Set.of(), 0, 0, true);
	}

	/** Back to where the player was before they came to the practice world. */
	public boolean leave(ServerPlayer player) {
		Return back = returns.remove(player.getUUID());
		MinecraftServer server = player.level().getServer();
		ServerLevel level = back == null ? server.overworld() : server.getLevel(back.level());
		if (level == null || isPracticeWorld(server) && back == null) {
			return false;
		}
		if (back == null) {
			BlockPos spawn = level.getRespawnData().pos();
			player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), 0, 0, true);
		} else {
			player.teleportTo(level, back.pos().x, back.pos().y, back.pos().z, Set.of(), back.yaw(), back.pitch(), true);
		}
		return true;
	}

	/** A player's lobby spot by an arena. */
	private static void toLobby(ServerPlayer player, ServerLevel level, Site site) {
		player.teleportTo(level, site.centerX() + 0.5, site.lobbyY(), site.lobbyZ() + 0.5, Set.of(), 180, 15, true);
		player.sendSystemMessage(Component.literal(site.displayName() + " (" + String.join(", ", site.modes())
			+ "). Fight here from the menu's Practice tab or /sparbot practice fight <mode>. The pad behind you goes back to the hub."));
	}

	/**
	 * Starts a match in the mode's practice arena: the player against a new bot with that tier's skills, in
	 * the mode's kit. The bot is removed when the match ends.
	 */
	public String fight(ServerPlayer player, GameMode mode, SkillProfile profile) throws IOException {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = ensure(server);
		Site site = PracticeLayout.siteFor(mode.id());
		Arena arena = SparBot.matches().arenas().get(site.arenaId()).orElseThrow(() -> new IllegalStateException("No arena " + site.arenaId()));
		if (SparBot.matches().match(arena.id()).isPresent()) {
			throw new IllegalArgumentException(site.displayName() + " is in use; stop that match first (/sparbot match stop " + arena.id() + ")");
		}
		Kit kit = SparBot.kits().get(mode.kit()).orElseThrow(() -> new IllegalArgumentException("Mode " + mode.id() + " has an unknown kit " + mode.kit()));
		if (player.level() != level) {
			returns.putIfAbsent(player.getUUID(), new Return(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
		}
		String name = botName(site);
		SparBot.bots().get(name).ifPresent(old -> SparBot.bots().remove(old, "replaced by a new practice fight"));
		Arena.Spawn spawn = arena.spawnB();
		Bot bot = SparBot.bots().spawn(server, name, level, new Vec3(spawn.x(), spawn.y(), spawn.z()), spawn.yaw(), profile, kit);
		bot.setAssignedTarget(player.getUUID());
		SparBot.matches().start(server, mode, arena, MatchManager.fighterFor(player), MatchManager.fighterFor(bot),
			match -> SparBot.bots().get(name).ifPresent(b -> SparBot.bots().remove(b, "practice fight over")));
		return mode.displayName() + " against " + profile.displayName() + " in the " + site.displayName() + ", first to " + mode.roundsToWin();
	}

	private static String botName(Site site) {
		String id = site.id();
		return "Spar" + Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	/** Takes a player to the Arcane Colosseum's south bridge, looking up at its gate. */
	public void toColosseum(ServerPlayer player) throws IOException {
		ServerLevel level = ensure(player.level().getServer());
		if (player.level() != level) {
			returns.putIfAbsent(player.getUUID(), new Return(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
		}
		player.teleportTo(level, GrandStadium.CX + 0.5, PracticeLayout.FLOOR, GrandStadium.CZ + PracticeLayout.GRAND_ARRIVAL + 0.5, Set.of(), 180, -12,
			true);
		player.sendSystemMessage(Component.literal("The Arcane Colosseum: free for all. Fight anyone here (spawn bots from the menu if you like). "
			+ "It resets itself once everyone has left. The pads behind you and in the south tunnel go back to the hub."));
	}

	/** Columns of the colosseum put back per tick while it resets (spread out, so the server never stalls). */
	private static final int RESET_COLUMNS_PER_TICK = 400;
	/** Players were in the colosseum since it was last reset. */
	private boolean colosseumDirty;
	/** The column a reset has got to (-1: no reset running), and whether it is on the hanging blocks yet. */
	private int resetCursor = -1;
	private boolean resetHanging;

	/**
	 * Puts the colosseum back the way it was built once every player has left it (bots don't count): every
	 * block the blueprint says, loose items, arrows, crystals and carts gone. Someone coming back stops it.
	 */
	private void colosseumTick(MinecraftServer server, ServerLevel level) {
		if (server.getTickCount() % 20 == 0) {
			boolean occupied = level.players().stream().anyMatch(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer)
				&& Math.abs(p.getX() - GrandStadium.CX) <= GrandStadium.REACH + 8 && Math.abs(p.getZ() - GrandStadium.CZ) <= GrandStadium.REACH + 8);
			if (occupied) {
				colosseumDirty = true;
				resetCursor = -1;
			} else if (colosseumDirty && resetCursor < 0) {
				resetCursor = 0;
				resetHanging = false;
				int r = GrandStadium.REACH;
				net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(GrandStadium.CX - r, GrandStadium.Y0 - 20, GrandStadium.CZ - r,
					GrandStadium.CX + r + 1, GrandStadium.Y1 + 30, GrandStadium.CZ + r + 1);
				for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box,
					e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile
						|| e instanceof net.minecraft.world.entity.ExperienceOrb || e instanceof net.minecraft.world.entity.item.PrimedTnt
						|| e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
						|| e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
						|| e instanceof net.minecraft.world.entity.item.FallingBlockEntity)) {
					e.discard();
				}
			}
		}
		if (resetCursor >= 0) {
			GrandStadium.apply(level, resetCursor, resetCursor + RESET_COLUMNS_PER_TICK, resetHanging);
			resetCursor += RESET_COLUMNS_PER_TICK;
			if (resetCursor >= GrandStadium.columns()) {
				if (!resetHanging) {
					resetHanging = true;
					resetCursor = 0;
				} else {
					resetCursor = -1;
					colosseumDirty = false;
					SparBot.LOGGER.info("The Arcane Colosseum has been reset");
				}
			}
		}
	}

	/** Pads: standing on one for half a second takes you to its arena (or from an arena's lobby back to the hub). */
	public void tick(MinecraftServer server) {
		ServerLevel practice = isPracticeWorld(server) ? server.overworld() : server.getLevel(DIMENSION);
		if (practice != null) {
			colosseumTick(server, practice);
		}
		if (server.getTickCount() % 2 != 0) {
			return;
		}
		ServerLevel level = practice;
		if (level == null || level.players().isEmpty()) {
			onPad.clear();
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : level.players()) {
			if (player instanceof io.github.flick256.sparbot.bot.BotPlayer || SparBot.matches().matches().stream()
				.anyMatch(m -> m.involves(player.getUUID()))) {
				continue;
			}
			Site target = null;
			boolean home = false;
			boolean colosseum = false;
			BlockPos feet = player.blockPosition();
			if (level.getBlockState(feet).is(net.minecraft.world.level.block.Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
				for (Site site : PracticeLayout.SITES) {
					int[] p = PracticeLayout.pad(site);
					if (feet.getY() == PracticeLayout.FLOOR && feet.getX() == p[0] && feet.getZ() == p[1]) {
						target = site;
					}
					if (feet.getY() == site.lobbyY() && feet.getX() == site.centerX() && feet.getZ() == site.lobbyZ() + 1) {
						home = true;
					}
				}
				int[] g = PracticeLayout.GRAND_PAD;
				if (feet.getY() == PracticeLayout.FLOOR && feet.getX() == g[0] && feet.getZ() == g[1]) {
					colosseum = true;
				}
				if (feet.getY() == PracticeLayout.FLOOR && feet.getX() == GrandStadium.CX && (feet.getZ() == GrandStadium.CZ + PracticeLayout.GRAND_RETURN_BRIDGE
					|| feet.getZ() == GrandStadium.CZ + PracticeLayout.GRAND_RETURN_TUNNEL)) {
					home = true;
				}
			}
			if (target == null && !home && !colosseum || now - padUsed.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < PAD_COOLDOWN) {
				onPad.remove(player.getUUID());
				continue;
			}
			int ticks = onPad.merge(player.getUUID(), 2, Integer::sum);
			if (ticks >= PAD_TICKS) {
				onPad.remove(player.getUUID());
				padUsed.put(player.getUUID(), now);
				if (home) {
					player.teleportTo(level, 0.5, PracticeLayout.FLOOR, -3.5, Set.of(), 0, 0, true);
				} else if (colosseum) {
					player.teleportTo(level, GrandStadium.CX + 0.5, PracticeLayout.FLOOR, GrandStadium.CZ + PracticeLayout.GRAND_ARRIVAL + 0.5, Set.of(), 180,
						-12, true);
				} else {
					toLobby(player, level, target);
				}
			}
		}
	}
}

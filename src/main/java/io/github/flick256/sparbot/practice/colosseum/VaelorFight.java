package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.kit.KitApplier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fight in the Heartwell. Whoever steps into the arena is the challenger: the way in is barred behind them,
 * the war horn sounds, Vaelor's theme starts, and he rises from his throne, with his name on the screen and a
 * boss bar. Both get the same kit (netherite, sword, axe, shield, bow, pearls, golden apples and two totems; the
 * challenger's own inventory is kept safe and given back afterwards), and Vaelor is the strongest bot there is.
 * He is mortal like every bot, with nothing the challenger doesn't have. He talks as the fight goes, mostly when
 * a totem breaks.
 *
 * <p>Beat him and he thanks you: his blade and the champion's laurel are yours, your name goes up in the Hall of
 * Champions, and the advancement "Unbroken No More". Lose and he sends you back up to train, and the way in opens
 * again for another try.
 */
public final class VaelorFight {
	/** Vaelor's theme (assets/sparbot/sounds/music/vaelor.ogg), and how long it is before it starts again. */
	static final Identifier THEME = Identifier.fromNamespaceAndPath("sparbot", "music.vaelor");
	static final int THEME_TICKS = 96 * 20;
	static final String KIT = "sparbot_vaelor";
	static final String PROFILE = "demon";
	static final String NAME = "Vaelor";
	/** Longest a fight may go on (ten minutes): then it is called off, no winner. */
	private static final int LIMIT = 20 * 60 * 10;
	private static final int INTRO = 140;

	enum Stage { WAITING, INTRO, FIGHT, OVER }

	private final HollowCrown crown;
	private Stage stage = Stage.WAITING;
	private @Nullable UUID challenger;
	private String challengerName = "";
	private @Nullable Bot vaelor;
	private @Nullable ServerBossEvent bar;
	private long started;
	private long overAt;
	private boolean won;
	private long musicAt;
	private int vaelorTotems;
	private int startTotems;
	private int playerTotems;
	private int lines;
	private boolean sealChecked;
	private final Set<UUID> pendingRestore = new HashSet<>();
	/** How many looks in a row each player has been inside the arena (a fight starts on the third). */
	private final java.util.Map<UUID, Integer> stepping = new java.util.HashMap<>();

	VaelorFight(HollowCrown crown) {
		this.crown = crown;
	}

	/** Whether a fight is on (from the doors closing until they open again). */
	public boolean running() {
		return stage != Stage.WAITING;
	}

	public @Nullable UUID challenger() {
		return challenger;
	}

	private static void say(ServerPlayer p, String line) {
		p.sendSystemMessage(Component.literal("Vaelor: ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
			.append(Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE)));
	}

	private static void sound(ServerLevel level, ServerPlayer p, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	private static void sound(ServerLevel level, ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, p.blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	/** Vaelor's theme, from the middle of the arena (a stereo track: it plays the same wherever you stand). */
	private static void music(ServerPlayer p) {
		double[] mid = {CelestialColosseum.CX + 0.5, Heartwell.FEET + 4, CelestialColosseum.CZ + 0.5};
		p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(THEME)), SoundSource.RECORDS, mid[0], mid[1], mid[2],
			1.0F, 1.0F, p.getRandom().nextLong()));
	}

	private static void stopMusic(ServerPlayer p) {
		p.connection.send(new ClientboundStopSoundPacket(THEME, SoundSource.RECORDS));
	}

	/** Where in the arena a world position is: its distance from the middle and height above the floor. */
	private static boolean inArena(Vec3 at, double margin) {
		double dx = at.x - (CelestialColosseum.CX + 0.5);
		double dz = at.z - (CelestialColosseum.CZ + 0.5);
		return Math.hypot(dx, dz) < Heartwell.RADIUS + margin && at.y >= Heartwell.FEET - 3 && at.y < Heartwell.FEET + 22;
	}

	private static Vec3 world(double[] blueprint) {
		return new Vec3(CelestialColosseum.CX + blueprint[0] + 0.5, blueprint[1], CelestialColosseum.CZ + blueprint[2] + 0.5);
	}

	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90);
	}

	// --- The way in ---

	/** The blocks across the way into the arena: iron bars while a fight is on. */
	private static List<BlockPos> seal() {
		List<BlockPos> out = new ArrayList<>();
		double mx = Heartwell.COS * (Heartwell.SEAL + 0.5);
		double mz = Heartwell.SIN * (Heartwell.SEAL + 0.5);
		for (int dx = (int) mx - 5; dx <= (int) mx + 5; dx++) {
			for (int dz = (int) mz - 5; dz <= (int) mz + 5; dz++) {
				double a = dx * Heartwell.COS + dz * Heartwell.SIN;
				double lat = -dx * Heartwell.SIN + dz * Heartwell.COS;
				if ((int) Math.floor(a) == Heartwell.SEAL && Math.abs(lat) < 2.5) {
					for (int y = Heartwell.FEET; y <= Heartwell.FEET + 5; y++) {
						out.add(new BlockPos(CelestialColosseum.CX + dx, y, CelestialColosseum.CZ + dz));
					}
				}
			}
		}
		return out;
	}

	private static void bar(ServerLevel level, boolean shut) {
		BlockState state = shut ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState();
		for (BlockPos pos : seal()) {
			level.setBlock(pos, state, 3);
		}
		BlockPos mid = seal().get(0);
		level.playSound(null, mid, shut ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.6F);
	}

	// --- Each tick ---

	void tick(MinecraftServer server, ServerLevel level) {
		pendingRestore.removeIf(uuid -> {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			return p == null || p.isAlive() && SparBot.matches().backups().restore(p);
		});
		switch (stage) {
			case WAITING -> waiting(server, level);
			case INTRO, FIGHT -> fighting(server, level);
			case OVER -> over(server, level);
		}
	}

	/** Anyone stepping into the arena (in survival, not in a match or drill) is the challenger. */
	private void waiting(MinecraftServer server, ServerLevel level) {
		if (!sealChecked) {
			// Bars left across the way in by a fight the server stopped in the middle of: open it again.
			sealChecked = true;
			if (seal().stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.IRON_BARS))) {
				bar(level, false);
			}
		}
		if (server.getTickCount() % 4 != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p instanceof BotPlayer || !p.isAlive() || !inArena(p.position(), -1.5)) {
				stepping.remove(p.getUUID());
				continue;
			}
			// Half a second inside first (not just passing through on a teleport).
			if (stepping.merge(p.getUUID(), 1, Integer::sum) < 3) {
				continue;
			}
			if (p.gameMode() != GameType.SURVIVAL && p.gameMode() != GameType.ADVENTURE) {
				if (server.getTickCount() % 100 == 0) {
					p.sendOverlayMessage(Component.literal("Vaelor only fights a challenger in survival.").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
				continue;
			}
			if (SparBot.matches().inMatch(p.getUUID()) || SparBot.drills().inDrill(p.getUUID())) {
				continue;
			}
			stepping.clear();
			start(server, level, p);
			return;
		}
	}

	/** The doors close: the challenger's kit, the war horn, the theme, and Vaelor's name. */
	private void start(MinecraftServer server, ServerLevel level, ServerPlayer p) {
		Kit kit = SparBot.kits().get(KIT).orElse(null);
		SkillProfile profile = SparBot.profiles().get(PROFILE).orElse(null);
		if (kit == null || profile == null) {
			SparBot.LOGGER.error("The Heartwell needs kit {} and profile {}", KIT, PROFILE);
			return;
		}
		if (SparBot.matches().backups().has(p.getUUID())) {
			p.sendSystemMessage(Component.literal("Get your inventory back from your last match first (rejoin).").withStyle(ChatFormatting.RED));
			return;
		}
		try {
			SparBot.matches().backups().save(p);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not keep {}'s inventory for the Heartwell", p.getPlainTextName(), e);
			return;
		}
		challenger = p.getUUID();
		challengerName = p.getPlainTextName();
		stage = Stage.INTRO;
		started = server.getTickCount();
		lines = 0;
		won = false;
		// Anyone else in the arena waits outside.
		Vec3 hall = world(new double[] {Heartwell.COS * (Heartwell.HALL + 3), Heartwell.FEET, Heartwell.SIN * (Heartwell.HALL + 3)});
		for (ServerPlayer other : level.players()) {
			if (other != p && !(other instanceof BotPlayer) && inArena(other.position(), 1)) {
				other.teleportTo(level, hall.x, hall.y, hall.z, Set.of(), other.getYRot(), 0, true);
				other.sendSystemMessage(Component.literal(challengerName + " has stepped into the Heartwell. One challenger at a time.")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		bar(level, true);
		Vec3 gate = world(Heartwell.gate());
		Vec3 throne = world(Heartwell.throne());
		p.teleportTo(level, gate.x, gate.y, gate.z, Set.of(), yawToward(gate, throne), 0, true);
		KitApplier.apply(p, kit);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(10);
		p.removeAllEffects();
		p.clearFire();
		playerTotems = totems(p);
		HollowCrown.title(p, Component.literal("THE HEARTWELL").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			Component.literal("Vaelor the Unbroken is waiting").withStyle(ChatFormatting.GRAY), 10, 50, 10);
		sound(level, p, SoundEvents.RAID_HORN, 4.0F, 1.0F);
		music(p);
		musicAt = server.getTickCount();
		ServerBossEvent b = new ServerBossEvent(UUID.randomUUID(), Component.literal("Vaelor the Unbroken").withStyle(ChatFormatting.LIGHT_PURPLE),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
		b.setProgress(0);
		b.addPlayer(p);
		bar = b;
		SparBot.LOGGER.info("{} stepped into the Heartwell", challengerName);
	}

	private static int totems(ServerPlayer p) {
		int n = 0;
		for (ItemStack s : p.getInventory()) {
			if (s.is(Items.TOTEM_OF_UNDYING)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static final String[] POPS = {"Ha! Again! Again!", "Good. GOOD. That one I felt.", "You fight like Aurel did. She was good too.",
		"Don't stop now. The star is listening."};
	private static final String[] TAUNTS = {"Up you get. We're not done.", "Is that your first? I've lost count of mine.",
		"Breathe. Then come at me again."};

	/** The intro, then the fight: Vaelor's arrival, the countdown, the bar, the theme, his lines, the outcome. */
	private void fighting(MinecraftServer server, ServerLevel level) {
		long t = server.getTickCount() - started;
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (p == null || p.level() != level) {
			// Gone (logged out): called off. Their inventory comes back when they rejoin.
			end(server, level, false, true);
			return;
		}
		if (server.getTickCount() - musicAt >= THEME_TICKS) {
			music(p);
			musicAt = server.getTickCount();
		}
		BotPlayer body = vaelor == null ? null : vaelor.body();
		if (stage == Stage.INTRO) {
			intro(server, level, p, t);
			return;
		}
		if (p.isDeadOrDying()) {
			end(server, level, false, false);
			return;
		}
		if (body == null || body.isDeadOrDying()) {
			end(server, level, true, false);
			return;
		}
		if (!inArena(p.position(), 2) || t > LIMIT) {
			p.sendSystemMessage(Component.literal(t > LIMIT ? "The fight has gone on too long: it is called off." : "You left the arena: the fight is off.")
				.withStyle(ChatFormatting.GRAY));
			end(server, level, false, false);
			return;
		}
		// The bar: Vaelor's health and the totems he has left.
		int left = totems(body);
		if (bar != null) {
			bar.setProgress((float) Math.max(0, Math.min(1, (left + body.getHealth() / body.getMaxHealth()) / (startTotems + 1))));
		}
		if (left < vaelorTotems) {
			sound(level, p, SoundEvents.RAID_HORN, 3.0F, 1.2F);
			say(p, POPS[Math.min(POPS.length - 1, startTotems - left - 1 + (lines++ > 3 ? 1 : 0))]);
		}
		vaelorTotems = left;
		int mine = totems(p);
		if (mine < playerTotems) {
			say(p, TAUNTS[Math.floorMod(mine, TAUNTS.length)]);
		}
		playerTotems = mine;
	}

	private void intro(MinecraftServer server, ServerLevel level, ServerPlayer p, long t) {
		if (t == 40) {
			spawn(server, level, p);
			say(p, "So. Another challenger.");
		} else if (t == 70) {
			say(p, "A hundred years I've held the heart of the star. Two hundred have come down to take it from me.");
		} else if (t == 100) {
			HollowCrown.title(p, Component.literal("VAELOR").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
				Component.literal("the Unbroken").withStyle(ChatFormatting.LIGHT_PURPLE), 5, 40, 10);
			sound(level, p, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
		} else if (t == 115) {
			say(p, "Show me what the arena taught you!");
		}
		if (bar != null && t >= 60) {
			bar.setProgress(Math.min(1F, (t - 60) / 60F));
		}
		if (t >= INTRO) {
			Bot v = vaelor;
			if (v == null || v.body() == null) {
				end(server, level, false, true);
				return;
			}
			HollowCrown.title(p, Component.literal("FIGHT!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty(), 2, 20, 8);
			sound(level, p, SoundEvents.RAID_HORN, 4.0F, 1.0F);
			v.resetMindForRound();
			v.setAssignedTarget(p.getUUID());
			v.setBrainPaused(false);
			stage = Stage.FIGHT;
		}
	}

	/** Vaelor rises in front of his throne, in the same kit, facing the challenger. */
	private void spawn(MinecraftServer server, ServerLevel level, ServerPlayer p) {
		Kit kit = SparBot.kits().get(KIT).orElseThrow();
		SkillProfile profile = SparBot.profiles().get(PROFILE).orElseThrow();
		SparBot.bots().get(NAME).ifPresent(old -> SparBot.bots().remove(old, "replaced by the Heartwell's Vaelor"));
		Vec3 at = world(Heartwell.throne());
		try {
			Bot v = SparBot.bots().spawn(server, NAME, level, at, yawToward(at, p.position()), profile, kit);
			v.setInMatch(true);
			v.setBrainPaused(true);
			vaelor = v;
			BotPlayer body = v.body();
			startTotems = body == null ? 0 : totems(body);
			vaelorTotems = startTotems;
		} catch (RuntimeException e) {
			SparBot.LOGGER.error("Vaelor could not rise in the Heartwell", e);
			p.sendSystemMessage(Component.literal("Vaelor could not rise: " + e.getMessage()).withStyle(ChatFormatting.RED));
			return;
		}
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 80, 0.6, 1.2, 0.6, 0.08);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 40, 1.0, 0.1, 1.0, 0.02);
		sound(level, p, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.8F);
		sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.5F);
	}

	/** The end of a fight: won, lost, or called off. */
	private void end(MinecraftServer server, ServerLevel level, boolean victory, boolean abandoned) {
		stage = Stage.OVER;
		overAt = server.getTickCount();
		won = victory;
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
		if (p != null) {
			stopMusic(p);
		}
		BotPlayer body = vaelor == null ? null : vaelor.body();
		if (victory && p != null) {
			Vec3 at = body == null ? p.position() : body.position();
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 150, 0.8, 1.5, 0.8, 0.4);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 100, 1.0, 2.0, 1.0, 0.1);
			sound(level, p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
			sound(level, p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3.0F, 1.0F);
			sound(level, p, SoundEvents.FIREWORK_ROCKET_TWINKLE, 3.0F, 1.0F);
			HollowCrown.title(p, Component.literal("VICTORY").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal("Vaelor the Unbroken is beaten").withStyle(ChatFormatting.YELLOW), 10, 80, 20);
			say(p, "...At last.");
		} else if (p != null && !abandoned) {
			say(p, "Not today. Train, and come back. I'll be here.");
			sound(level, p, SoundEvents.BELL_BLOCK, 2.0F, 0.7F);
		}
		if (vaelor != null && (!victory || body == null)) {
			SparBot.bots().remove(vaelor, "the fight in the Heartwell is over");
			vaelor = null;
		}
		SparBot.LOGGER.info("The fight in the Heartwell is over: {} {}", challengerName, victory ? "won" : abandoned ? "left" : "lost");
	}

	/** After the fight: Vaelor's last words and the rewards, or the way in opened again, and the floor swept. */
	private void over(MinecraftServer server, ServerLevel level) {
		long t = server.getTickCount() - overAt;
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (t == 20) {
			if (vaelor != null) {
				SparBot.bots().remove(vaelor, "beaten in the Heartwell");
				vaelor = null;
			}
			sweep(level);
		}
		if (won && p != null) {
			if (t == 50) {
				say(p, "A champion. I knew one would come.");
			} else if (t == 100) {
				say(p, "The heart of the star is yours to keep now. It will be quiet for you.");
			} else if (t == 150) {
				say(p, "Take my blade. Go up and sit in my seat, and tell them I'm resting.");
			}
		}
		if (t < (won ? 170 : 60)) {
			return;
		}
		if (p != null) {
			if (p.isAlive()) {
				SparBot.matches().backups().restore(p);
			} else {
				pendingRestore.add(p.getUUID());
			}
			if (won) {
				if (p.isAlive()) {
					give(p, HollowCrown.oathkeeper(p));
					give(p, HollowCrown.laurel(p));
				}
				crown.discover(p, level, HollowCrown.Find.CHAMPION);
				HollowCrown.crown(level, challengerName);
				Component news = Component.literal(challengerName + " has beaten Vaelor the Unbroken! A new Champion of the Crown.")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				for (ServerPlayer other : server.getPlayerList().getPlayers()) {
					if (!(other instanceof BotPlayer)) {
						other.sendSystemMessage(news);
					}
				}
			}
		}
		sweep(level);
		bar(level, false);
		stage = Stage.WAITING;
		challenger = null;
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) {
			p.drop(stack, false);
		}
	}

	/** Clears the kit's dropped items from the arena (the challenger's own things were kept safe). */
	private static void sweep(ServerLevel level) {
		AABB box = new AABB(CelestialColosseum.CX - Heartwell.SHELL, Heartwell.FEET - 4, CelestialColosseum.CZ - Heartwell.SHELL,
			CelestialColosseum.CX + Heartwell.SHELL + 1, Heartwell.FEET + 24, CelestialColosseum.CZ + Heartwell.SHELL + 1);
		for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof ItemEntity
			|| e instanceof net.minecraft.world.entity.projectile.Projectile)) {
			e.discard();
		}
	}

	/** Ends a fight at once (the server stopping, or a test): the bot removed, the way in opened. */
	public void stop(MinecraftServer server, ServerLevel level) {
		if (stage == Stage.INTRO || stage == Stage.FIGHT) {
			end(server, level, false, true);
		}
		if (stage == Stage.OVER) {
			overAt = server.getTickCount() - 1000;
			over(server, level);
		}
	}
}

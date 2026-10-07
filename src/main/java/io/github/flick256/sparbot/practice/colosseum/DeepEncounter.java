package io.github.flick256.sparbot.practice.colosseum;

import com.mojang.math.Transformation;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.content.SparBotContent;
import io.github.flick256.sparbot.content.VaelorBoss;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.mixin.TextDisplayAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * What happens in the Deep (see {@link TheDeep}): the Leap, the fight with Vaelor, and the long way back up for whoever
 * wins.
 * <ol>
 * <li><b>The Leap.</b> Someone in survival drops down the shaft from the Brink: their own things are kept safe, they get
 * the challenger's kit (full netherite, a shield, sword, axe and bow, pearls, golden apples, two totems), and they float
 * down eighty blocks into the arena while his theme starts. Vaelor rises from his throne when they land. One
 * challenger at a time: anyone else who drops in is sent back up. Nothing else starts a fight.</li>
 * <li><b>The fight</b> (see {@link VaelorBoss}). There is no way out of the arena but through him: whoever climbs out is
 * put back. Fall, and he sends you up to train (your things come back when you respawn) and sits down again.</li>
 * <li><b>The way back up.</b> When he falls the Triumph plays and the dead in the stands roar. The Gate of Triumph opens
 * across from his throne; the braziers light ahead of you down the Avenue and over the moat to the Hall of Triumph,
 * where your own things are given back and his are on their pedestals for you to take. The Gallery of Witness climbs
 * the cavern wall, the Stair of Stars goes in over the dome, the Laurel Door opens onto the Brink, and from the
 * platform the Heart of the Star breaks and lifts you up through the field into the colosseum, into fireworks and
 * the crowd. Everyone hears who won.</li>
 * </ol>
 * Rewards not taken from the pedestals (a champion who leaves, logs out or falls on the way) are kept for them and given
 * the next time they're about.
 */
public final class DeepEncounter {
	static final String KIT = "sparbot_vaelor";
	static final int THEME_TICKS = 96 * 20;
	/** Longest a fight may go on (fifteen minutes), and a victory walk (fifteen more). */
	private static final int FIGHT_LIMIT = 20 * 60 * 15;
	private static final int VICTORY_LIMIT = 20 * 60 * 15;
	static final String TAG = "sparbot_deep";

	enum Stage { IDLE, LEAP, FIGHT, VICTORY }

	private final HollowCrown crown;
	private Stage stage = Stage.IDLE;
	private long stageAt;
	private @Nullable UUID challenger;
	private String challengerName = "";
	private @Nullable VaelorBoss boss;
	private long musicAt;
	private long nobodyAt;
	private int outside;
	private boolean cleaned;
	private final Set<UUID> pendingRestore = new HashSet<>();
	private final Map<UUID, Integer> strays = new HashMap<>();
	// The way back up.
	private final List<Entity> shown = new ArrayList<>();
	private final Display.ItemDisplay[] pedestals = new Display.ItemDisplay[7];
	private boolean gateOpen;
	private boolean restored;
	private boolean laurelOpen;
	private boolean roaredGallery;
	private boolean roaredStair;
	private long ascendAt = -1;
	private boolean hatchOpen;
	private boolean crowned;

	DeepEncounter(HollowCrown crown) {
		this.crown = crown;
	}

	/** Whether a fight (or the walk after one) is going on. */
	public boolean running() {
		return stage != Stage.IDLE;
	}

	public boolean fighting() {
		return stage == Stage.LEAP || stage == Stage.FIGHT;
	}

	public boolean victorious() {
		return stage == Stage.VICTORY;
	}

	public @Nullable UUID challenger() {
		return challenger;
	}

	public @Nullable VaelorBoss boss() {
		return boss;
	}

	// --- Places ---

	static Vec3 world(double dx, double y, double dz) {
		return new Vec3(CelestialColosseum.CX + dx, y, CelestialColosseum.CZ + dz);
	}

	static BlockPos block(int dx, int y, int dz) {
		return new BlockPos(CelestialColosseum.CX + dx, y, CelestialColosseum.CZ + dz);
	}

	private static double dx(Entity e) {
		return e.getX() - CelestialColosseum.CX;
	}

	private static double dz(Entity e) {
		return e.getZ() - CelestialColosseum.CZ;
	}

	/** The middle of the arena, where the Leap lands. */
	static Vec3 arenaCentre() {
		return world(0.5, TheDeep.FEET, 0.5);
	}

	/** On the ledge, by the Hall of the Fallen's way in: where anyone not meant to be down there is sent. */
	static Vec3 brinkSpot() {
		double r = 21;
		return world(TheDeep.COS * r + 0.5, TheDeep.BRINK_FEET, TheDeep.SIN * r + 0.5);
	}

	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static boolean eligible(ServerPlayer p) {
		return !(p instanceof BotPlayer) && p.isAlive() && (p.gameMode() == GameType.SURVIVAL || p.gameMode() == GameType.ADVENTURE);
	}

	// --- Each tick ---

	void tick(MinecraftServer server, ServerLevel level) {
		if (!cleaned) {
			// Pedestal displays left from a walk the server stopped in the middle of.
			cleaned = true;
			AABB box = new AABB(CelestialColosseum.CX - TheDeep.REACH, CelestialColosseum.Y0, CelestialColosseum.CZ - TheDeep.REACH,
				CelestialColosseum.CX + TheDeep.REACH, CelestialColosseum.S + 40, CelestialColosseum.CZ + TheDeep.REACH);
			for (Entity e : level.getEntities((Entity) null, box, e -> e.entityTags().contains(TAG))) {
				e.discard();
			}
		}
		long now = server.getTickCount();
		pendingRestore.removeIf(uuid -> {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			return p == null || p.isAlive() && SparBot.matches().backups().restore(p);
		});
		if (now % 20 == 7) {
			payOwed(server, level);
		}
		keepBoss(server, level, now);
		switch (stage) {
			case IDLE -> idle(server, level, now);
			case LEAP -> leap(server, level, now);
			case FIGHT -> fight(server, level, now);
			case VICTORY -> victory(server, level, now);
		}
		if (now % 10 == 0) {
			watchDeep(server, level);
		}
	}

	/** Vaelor sits on his throne whenever anyone is near the Deep, and is gone when no one is. */
	private void keepBoss(MinecraftServer server, ServerLevel level, long now) {
		if (stage != Stage.IDLE) {
			// (During a fight the fight looks after him: whether he fell, or went.)
			return;
		}
		boolean someone = false;
		for (ServerPlayer p : level.players()) {
			if (!(p instanceof BotPlayer) && Math.hypot(dx(p), dz(p)) < TheDeep.REACH + 16 && p.getY() < CelestialColosseum.S - 2) {
				someone = true;
				break;
			}
		}
		if (someone) {
			nobodyAt = now;
		}
		if (boss != null && (boss.isRemoved() || boss.level() != level)) {
			boss = null;
		}
		if (boss == null && someone && now % 20 == 0) {
			VaelorBoss b = SparBotContent.VAELOR.create(level, EntitySpawnReason.EVENT);
			if (b != null) {
				double[] seat = TheDeep.throneSeat();
				Vec3 at = world(seat[0] + 0.5, seat[1], seat[2] + 0.5);
				float yaw = yawToward(at, arenaCentre());
				b.snapTo(at.x, at.y, at.z, yaw, 0);
				b.addTag(TAG);
				level.addFreshEntity(b);
				b.bind(at, yaw, arenaCentre(), TheDeep.ARENA - 1.5);
				boss = b;
			}
		}
		if (boss != null && stage == Stage.IDLE && now - nobodyAt > 600) {
			boss.discard();
			boss = null;
		}
	}

	/** Waiting: anyone dropping down the shaft is a challenger, or sent back up. */
	private void idle(MinecraftServer server, ServerLevel level, long now) {
		for (ServerPlayer p : level.players()) {
			if (p instanceof BotPlayer || !TheDeep.leaping(dx(p), p.getY(), dz(p))) {
				continue;
			}
			if (p.gameMode() == GameType.CREATIVE || p.gameMode() == GameType.SPECTATOR) {
				continue;
			}
			String why = SparBot.matches().inMatch(p.getUUID()) || SparBot.drills().inDrill(p.getUUID()) ? "Finish your match first."
				: SparBot.matches().backups().has(p.getUUID()) ? "Get your things back from your last match first (rejoin)."
				: boss == null || !boss.waiting() ? "Vaelor isn't on his throne yet. Try again in a moment." : null;
			if (why != null) {
				sendBack(level, p, why);
				continue;
			}
			startLeap(server, level, p, now);
			return;
		}
	}

	private void sendBack(ServerLevel level, ServerPlayer p, String why) {
		Vec3 at = brinkSpot();
		p.teleportTo(level, at.x, at.y, at.z, Set.of(), yawToward(at, world(0.5, at.y, 0.5)), 0, true);
		p.resetFallDistance();
		p.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// --- The Leap ---

	private void startLeap(MinecraftServer server, ServerLevel level, ServerPlayer p, long now) {
		Kit kit = SparBot.kits().get(KIT).orElse(null);
		if (kit == null) {
			SparBot.LOGGER.error("The Deep needs kit {}", KIT);
			sendBack(level, p, "The Deep isn't ready (its kit is missing).");
			return;
		}
		try {
			SparBot.matches().backups().save(p);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not keep {}'s things for the Deep", p.getPlainTextName(), e);
			sendBack(level, p, "Your things couldn't be kept safe, so you can't leap now.");
			return;
		}
		challenger = p.getUUID();
		challengerName = p.getPlainTextName();
		stage = Stage.LEAP;
		stageAt = now;
		outside = 0;
		KitApplier.apply(p, kit);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(10);
		p.removeAllEffects();
		p.clearFire();
		p.resetFallDistance();
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 30, 0, false, false, true));
		HollowCrown.title(p, Component.literal("THE LEAP").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
			Component.literal("Your own things are kept safe until you come back up").withStyle(ChatFormatting.GRAY), 10, 60, 15);
		music(p, SparBotContent.MUSIC_VAELOR);
		musicAt = now;
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 0.7F);
		SparBot.LOGGER.info("{} leapt into the Deep", challengerName);
	}

	private void leap(MinecraftServer server, ServerLevel level, long now) {
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (p == null || !p.isAlive()) {
			abandon(server, level, p);
			return;
		}
		long t = now - stageAt;
		if (t == 60) {
			HollowCrown.title(p, Component.literal("VAELOR").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
				Component.literal("the Unbroken, first champion of the Crown").withStyle(ChatFormatting.LIGHT_PURPLE), 10, 70, 20);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 3.0F, 0.8F);
		}
		boolean landed = p.onGround() && p.getY() < TheDeep.FEET + 3;
		if (landed && !TheDeep.inArena(dx(p), p.getY(), dz(p), -0.5) || t > 20 * 25) {
			// Off course (or stuck on the way): into the middle of the arena.
			Vec3 at = arenaCentre();
			p.teleportTo(level, at.x, at.y, at.z, Set.of(), p.getYRot(), 0, true);
			landed = true;
		}
		if (landed) {
			p.removeEffect(MobEffects.SLOW_FALLING);
			p.resetFallDistance();
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 3, 1, 0.1, 1, 0);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
			stage = Stage.FIGHT;
			stageAt = now;
			if (boss != null) {
				boss.challenge(p);
			}
		}
	}

	// --- The fight ---

	private void fight(MinecraftServer server, ServerLevel level, long now) {
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (boss != null && boss.defeated()) {
			victoryStart(server, level, p, now);
			return;
		}
		if (p == null || now - stageAt > FIGHT_LIMIT) {
			abandon(server, level, p);
			return;
		}
		if (!p.isAlive()) {
			defeat(server, level, p);
			return;
		}
		if (boss == null || boss.isRemoved()) {
			// Gone without falling (killed by a command): no winner.
			abandon(server, level, p);
			return;
		}
		if (now - musicAt >= THEME_TICKS) {
			music(p, SparBotContent.MUSIC_VAELOR);
			musicAt = now;
		}
		// No way out but through him.
		if (now % 10 == 0) {
			boolean in = TheDeep.inArena(dx(p), p.getY(), dz(p), 1.0);
			outside = in ? 0 : outside + 10;
			if (outside > 160) {
				outside = 0;
				Vec3 c = arenaCentre();
				double ang = Math.atan2(dz(p), dx(p));
				Vec3 at = world(0.5 + Math.cos(ang) * (TheDeep.ARENA - 4), TheDeep.FEET, 0.5 + Math.sin(ang) * (TheDeep.ARENA - 4));
				p.teleportTo(level, at.x, at.y, at.z, Set.of(), yawToward(at, c), 0, true);
				p.resetFallDistance();
				p.sendSystemMessage(Component.literal("There is no way out but through him.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
			}
		}
	}

	private void defeat(MinecraftServer server, ServerLevel level, ServerPlayer p) {
		stopMusic(p);
		p.sendSystemMessage(Component.literal("Vaelor: ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
			.append(Component.literal("Go up. Train. Come back down. I'll be here.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
		pendingRestore.add(p.getUUID());
		SparBot.LOGGER.info("{} fell to Vaelor", challengerName);
		reset(level);
	}

	private void abandon(MinecraftServer server, ServerLevel level, @Nullable ServerPlayer p) {
		if (p != null) {
			stopMusic(p);
			p.removeEffect(MobEffects.SLOW_FALLING);
			if (p.isAlive()) {
				SparBot.matches().backups().restore(p);
				if (TheDeep.inDeep(dx(p), p.getY(), dz(p))) {
					Vec3 at = brinkSpot();
					p.teleportTo(level, at.x, at.y, at.z, Set.of(), p.getYRot(), 0, true);
				}
			} else {
				pendingRestore.add(p.getUUID());
			}
		}
		SparBot.LOGGER.info("The fight in the Deep was called off ({})", challengerName);
		reset(level);
	}

	/** Back to waiting: Vaelor to his throne, the floor swept. */
	private void reset(ServerLevel level) {
		stage = Stage.IDLE;
		challenger = null;
		if (boss != null && !boss.isRemoved()) {
			boss.standDown();
		}
		sweep(level);
	}

	/** Clears the kit's dropped things and loose arrows from the arena. */
	private static void sweep(ServerLevel level) {
		double r = TheDeep.FACADE;
		AABB box = new AABB(CelestialColosseum.CX - r, TheDeep.GROUND - 6, CelestialColosseum.CZ - r, CelestialColosseum.CX + r + 1, TheDeep.FEET + 30,
			CelestialColosseum.CZ + r + 1);
		for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof Projectile)) {
			e.discard();
		}
	}

	/** Anyone in the Deep who has no business there (a stray from a relog, say) is sent back to the Brink. */
	private void watchDeep(MinecraftServer server, ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!eligible(p) || !TheDeep.inDeep(dx(p), p.getY(), dz(p)) || p.getUUID().equals(challenger) || TheDeep.leaping(dx(p), p.getY(), dz(p))) {
				strays.remove(p.getUUID());
				continue;
			}
			if (strays.merge(p.getUUID(), 10, Integer::sum) >= 60) {
				strays.remove(p.getUUID());
				sendBack(level, p, stage == Stage.IDLE ? "The Deep lets no one linger. Leap from the Well to face him."
					: "Someone else is facing Vaelor. One challenger at a time.");
			}
		}
	}

	// --- Victory ---

	private void victoryStart(MinecraftServer server, ServerLevel level, @Nullable ServerPlayer p, long now) {
		stage = Stage.VICTORY;
		stageAt = now;
		gateOpen = false;
		restored = false;
		laurelOpen = false;
		roaredGallery = false;
		roaredStair = false;
		ascendAt = -1;
		hatchOpen = false;
		crowned = false;
		Vec3 fell = boss != null ? boss.position() : arenaCentre();
		boss = null;
		if (p == null) {
			return;
		}
		stopMusic(p);
		music(p, SparBotContent.MUSIC_TRIUMPH);
		crowd(p, 1.0F);
		HollowCrown.title(p, Component.literal("VICTORY").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			Component.literal("Vaelor, the Unbroken, has fallen").withStyle(ChatFormatting.YELLOW), 10, 90, 20);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		// What he leaves: his blade, planted where he knelt; his things on their pedestals in the Hall of Triumph.
		HollowCrown.owe(p.getUUID(), SparBotContent.REWARDS.stream().map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()).toList());
		Display.ItemDisplay blade = itemDisplay(level, new ItemStack(SparBotContent.OATHKEEPER), fell.add(0, 1.4, 0), 2.6F, true);
		shown.add(blade);
		for (int i = 0; i < 7; i++) {
			double[] top = TheDeep.pedestalTop(i);
			Vec3 at = world(top[0], top[1], top[2]);
			pedestals[i] = itemDisplay(level, new ItemStack(SparBotContent.REWARDS.get(i)), at, 1.0F, false);
			shown.add(pedestals[i]);
			shown.add(label(level, at.add(0, 0.85, 0), Component.translatable(SparBotContent.REWARDS.get(i).getDescriptionId()).withStyle(ChatFormatting.LIGHT_PURPLE)));
		}
		crown.discover(p, level, HollowCrown.Find.CHAMPION);
		HollowCrown.crown(level, challengerName);
		SparBot.LOGGER.info("{} beat Vaelor", challengerName);
	}

	private void victory(MinecraftServer server, ServerLevel level, long now) {
		long t = now - stageAt;
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (p == null || !p.isAlive() || t > VICTORY_LIMIT) {
			endVictory(server, level, p, false);
			return;
		}
		spin(now);
		if (t == 60) {
			say(p, "The Gate of Triumph is open. Walk out the way he never could.");
		}
		if (t >= 50 && !gateOpen) {
			gateOpen = true;
			List<BlockPos> gate = gateCells();
			for (BlockPos pos : gate) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					6, 0.3, 0.3, 0.3, 0.05);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
			if (!gate.isEmpty()) {
				BlockPos mid = gate.get(gate.size() / 2);
				level.playSound(null, mid, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 3.0F, 0.5F);
				level.playSound(null, mid, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3.0F, 0.5F);
			}
		}
		if (now % 10 == 0) {
			lightBraziers(level, p);
		}
		double dx = dx(p);
		double dz = dz(p);
		double a = dx * TheDeep.COS + dz * TheDeep.SIN;
		double lat = -dx * TheDeep.SIN + dz * TheDeep.COS;
		boolean inHall = a > TheDeep.HALL_FROM && a < TheDeep.HALL_APSE + 10 && Math.abs(lat) < TheDeep.HALL_HALF + 1 && p.getY() < TheDeep.FEET + 8;
		if (inHall && !restored) {
			restored = true;
			SparBot.matches().backups().restore(p);
			p.sendSystemMessage(Component.literal("Your own things are given back. His are on the pedestals: take them.")
				.withStyle(ChatFormatting.GOLD));
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.2F);
		}
		if (restored) {
			claim(level, p);
		}
		double ang = Math.atan2(dz, dx);
		double turn = TheDeep.diff(ang, TheDeep.C);
		double d = Math.hypot(dx, dz);
		if (!roaredGallery && turn > TheDeep.TURN * 0.5 && d > 80) {
			roaredGallery = true;
			crowd(p, 0.6F);
			say(p, "Listen: up there, they've heard.");
		}
		double a2 = dx * TheDeep.COS2 + dz * TheDeep.SIN2;
		double l2 = -dx * TheDeep.SIN2 + dz * TheDeep.COS2;
		if (!roaredStair && a2 < 50 && a2 > 30 && Math.abs(l2) < 3 && p.getY() > 20) {
			roaredStair = true;
			crowd(p, 0.8F);
		}
		if (!laurelOpen && a2 < TheDeep.LEDGE + 8 && a2 > TheDeep.LEDGE && Math.abs(l2) < 3 && p.getY() > TheDeep.BRINK - 2) {
			laurelOpen = true;
			for (BlockPos pos : laurelCells()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
			BlockPos at = block((int) Math.round(TheDeep.COS2 * (TheDeep.LEDGE + 1)), TheDeep.BRINK_FEET, (int) Math.round(TheDeep.SIN2 * (TheDeep.LEDGE + 1)));
			level.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.7F);
			level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.2F);
			say(p, "The Laurel Door opens for you. Go to the Well.");
		}
		if (ascendAt < 0 && d < TheDeep.PLATFORM - 0.6 && Math.abs(p.getY() - TheDeep.BRINK_FEET) < 1.5) {
			ascend(level, p, now);
		}
		if (ascendAt >= 0) {
			ascending(server, level, p, now);
		}
	}

	/** Takes a reward off its pedestal when the champion comes up to it. */
	private void claim(ServerLevel level, ServerPlayer p) {
		for (int i = 0; i < 7; i++) {
			Display.ItemDisplay d = pedestals[i];
			if (d == null || d.isRemoved() || d.distanceToSqr(p.getX(), d.getY(), p.getZ()) > 1.9 * 1.9) {
				continue;
			}
			Item item = SparBotContent.REWARDS.get(i);
			if (HollowCrown.unowe(p.getUUID(), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString())) {
				give(p, reward(p, item));
				p.sendSystemMessage(Component.literal("You take ").withStyle(ChatFormatting.GOLD).append(Component.translatable(item.getDescriptionId())
					.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)).append(Component.literal(".").withStyle(ChatFormatting.GOLD)));
			}
			level.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY(), d.getZ(), 30, 0.3, 0.3, 0.3, 0.08);
			level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.0F);
			d.discard();
			pedestals[i] = null;
			for (Entity e : level.getEntities((Entity) null, d.getBoundingBox().inflate(0.5, 1.5, 0.5), e -> e instanceof Display.TextDisplay
				&& e.entityTags().contains(TAG))) {
				e.discard();
			}
			boolean all = true;
			for (Display.ItemDisplay left : pedestals) {
				all &= left == null;
			}
			if (all) {
				say(p, "All of it is yours. Now go up: the Gallery of Witness, through the door to your right.");
			}
		}
	}

	/** A reward as given: his item, with whose it is now. */
	static ItemStack reward(ServerPlayer p, Item item) {
		ItemStack stack = new ItemStack(item);
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> lines = new ArrayList<>(lore.lines());
		lines.add(Component.literal("Won from Vaelor by " + p.getPlainTextName()).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) {
			p.drop(stack, false);
		}
	}

	/** Rewards a champion didn't take from the pedestals, given when they're about (not down in the Deep). */
	private void payOwed(MinecraftServer server, ServerLevel level) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p instanceof BotPlayer || !p.isAlive() || p.getUUID().equals(challenger) && stage == Stage.VICTORY
				|| SparBot.matches().backups().has(p.getUUID())) {
				continue;
			}
			List<String> owed = HollowCrown.owed(p.getUUID());
			if (owed.isEmpty()) {
				continue;
			}
			for (String id : owed) {
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).ifPresent(item -> {
					if (HollowCrown.unowe(p.getUUID(), id)) {
						give(p, reward(p, item));
					}
				});
			}
			p.sendSystemMessage(Component.literal("Vaelor's things, which you left on their pedestals, are yours: they're in your inventory.")
				.withStyle(ChatFormatting.GOLD));
		}
	}

	/** The champion steps onto the platform: the Heart breaks, the hatch in the field opens, and up they go. */
	private void ascend(ServerLevel level, ServerPlayer p, long now) {
		ascendAt = now;
		say(p, "The Heart is quiet now. Rise, champion.");
		for (int dx = -TheDeep.HEART_R; dx <= TheDeep.HEART_R; dx++) {
			for (int dz = -TheDeep.HEART_R; dz <= TheDeep.HEART_R; dz++) {
				for (int y = TheDeep.HEART_Y - TheDeep.HEART_R - 1; y <= Heartwell.dome(0); y++) {
					if (TheDeep.inHeart(dx, y, dz)) {
						BlockPos pos = block(dx, y, dz);
						if (!level.getBlockState(pos).isAir()) {
							level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), pos.getX() + 0.5, pos.getY() + 0.5,
								pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.1);
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						}
					}
				}
			}
		}
		Vec3 heart = world(0.5, TheDeep.HEART_Y, 0.5);
		level.sendParticles(ParticleTypes.END_ROD, heart.x, heart.y, heart.z, 200, 1.5, 1.5, 1.5, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
		level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 3.0F, 0.6F);
		level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 3.0F, 0.5F);
		level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3.0F, 0.8F);
		hatch(level, true);
		Vec3 at = world(0.5, TheDeep.BRINK_FEET + 0.2, 0.5);
		p.teleportTo(level, at.x, at.y, at.z, Set.of(), p.getYRot(), -60, true);
		p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 12, 3, false, false, false));
		crowd(p, 0.7F);
	}

	private void ascending(MinecraftServer server, ServerLevel level, ServerPlayer p, long now) {
		long t = now - ascendAt;
		if (t % 2 == 0 && !crowned) {
			Vec3 c = world(0.5, p.getY() - 1, 0.5);
			level.sendParticles(ParticleTypes.END_ROD, true, false, c.x, c.y, c.z, 6, 0.5, 2.0, 0.5, 0.02);
		}
		if (!crowned && p.getY() > CelestialColosseum.S + 2.5) {
			crowned = true;
			p.removeEffect(MobEffects.LEVITATION);
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 6, 0, false, false, false));
			HollowCrown.title(p, Component.literal("CHAMPION OF THE CROWN").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(challengerName + ", who beat Vaelor the Unbroken").withStyle(ChatFormatting.YELLOW), 10, 100, 30);
			crowd(p, 1.0F);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
			Component news = Component.literal("⚔ " + challengerName + " has beaten Vaelor, the Unbroken! A new Champion of the Crown.")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (!(other instanceof BotPlayer)) {
					other.sendSystemMessage(news);
					if (other != p) {
						other.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F);
					}
				}
			}
		}
		if (crowned && hatchOpen && p.getY() > CelestialColosseum.S + 3.5) {
			hatch(level, false);
		}
		if (crowned && t % 8 == 0 && t < 20 * 14) {
			firework(level, p);
		}
		if (crowned && t > 20 * 15) {
			endVictory(server, level, p, true);
		}
		if (!crowned && t > 20 * 15) {
			// Something held them back: lift them out.
			Vec3 up = world(0.5, CelestialColosseum.S + 3, 0.5);
			p.teleportTo(level, up.x, up.y, up.z, Set.of(), p.getYRot(), 0, true);
		}
	}

	private void endVictory(MinecraftServer server, ServerLevel level, @Nullable ServerPlayer p, boolean done) {
		if (p != null) {
			stopMusic(p);
			if (!restored && p.isAlive()) {
				SparBot.matches().backups().restore(p);
			} else if (!restored) {
				pendingRestore.add(p.getUUID());
			}
			p.removeEffect(MobEffects.LEVITATION);
		}
		if (hatchOpen) {
			hatch(level, false);
		}
		for (Entity e : shown) {
			e.discard();
		}
		shown.clear();
		java.util.Arrays.fill(pedestals, null);
		stage = Stage.IDLE;
		challenger = null;
		sweep(level);
	}

	/** Opens (sets air) or closes (puts back as the blueprint has it) the field over the shaft. */
	private void hatch(ServerLevel level, boolean open) {
		hatchOpen = open;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState[] col = CelestialColosseum.column(dx, dz);
				for (int y = TheDeep.HATCH; y <= CelestialColosseum.S + 1; y++) {
					BlockPos pos = block(dx, y, dz);
					BlockState want = open ? Blocks.AIR.defaultBlockState() : col == null ? Blocks.AIR.defaultBlockState() : CelestialColosseum.at(col, y);
					if (!level.getBlockState(pos).equals(want)) {
						if (open) {
							level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), pos.getX() + 0.5, pos.getY() + 0.5,
								pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
						}
						level.setBlock(pos, want, 3);
					}
				}
			}
		}
		Vec3 top = world(0.5, CelestialColosseum.S + 1, 0.5);
		level.playSound(null, top.x, top.y, top.z, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.6F);
	}

	/** Lights the braziers within reach of the champion, one after another as they go. */
	private static void lightBraziers(ServerLevel level, ServerPlayer p) {
		BlockPos at = p.blockPosition();
		int lit = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-9, -4, -9), at.offset(9, 6, 9))) {
			BlockState s = level.getBlockState(pos);
			if (s.getBlock() instanceof CampfireBlock && !s.getValue(CampfireBlock.LIT)) {
				level.setBlock(pos, s.setValue(CampfireBlock.LIT, true), 3);
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.8F + lit * 0.1F);
				level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.03);
				if (++lit >= 2) {
					return;
				}
			}
		}
	}

	private static List<BlockPos> gateCells() {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -40; dx <= 40; dx++) {
			for (int dz = -40; dz <= 40; dz++) {
				if (TheDeep.gate(dx, dz)) {
					for (int y = TheDeep.FEET; y <= TheDeep.FEET + 5; y++) {
						out.add(block(dx, y, dz));
					}
				}
			}
		}
		return out;
	}

	private static List<BlockPos> laurelCells() {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -30; dx <= 30; dx++) {
			for (int dz = -30; dz <= 30; dz++) {
				if (TheDeep.laurelDoor(dx, dz)) {
					for (int y = TheDeep.BRINK_FEET; y <= TheDeep.BRINK_FEET + 3; y++) {
						out.add(block(dx, y, dz));
					}
				}
			}
		}
		return out;
	}

	// --- Shows ---

	private static Display.ItemDisplay itemDisplay(ServerLevel level, ItemStack stack, Vec3 at, float scale, boolean planted) {
		Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
		d.setPos(at.x, at.y, at.z);
		d.setItemStack(stack);
		d.setItemTransform(planted ? ItemDisplayContext.FIXED : ItemDisplayContext.GROUND);
		Quaternionf rot = planted ? new Quaternionf().rotateZ((float) Math.toRadians(-135)) : new Quaternionf();
		d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
		d.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
		d.setGlowColorOverride(0xC77DFF);
		d.setGlowingTag(true);
		d.setViewRange(2.0F);
		d.addTag(TAG);
		level.addFreshEntity(d);
		return d;
	}

	private static Display.TextDisplay label(ServerLevel level, Vec3 at, Component text) {
		Display.TextDisplay label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		label.setPos(at.x, at.y, at.z);
		((TextDisplayAccessor) label).sparbot$setText(text);
		((TextDisplayAccessor) label).sparbot$setBackgroundColor(0x60000000);
		label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6F, 0.6F, 0.6F), new Quaternionf()));
		label.setViewRange(1.0F);
		label.addTag(TAG);
		level.addFreshEntity(label);
		return label;
	}

	/** Turns the rewards on their pedestals, smoothly (each display interpolates a quarter turn a second). */
	private void spin(long now) {
		if (now % 20 != 0) {
			return;
		}
		float angle = (float) Math.toRadians((now / 20 % 4) * 90 + 90);
		for (Display.ItemDisplay d : pedestals) {
			if (d != null && !d.isRemoved()) {
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration(20);
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle), new Vector3f(1, 1, 1), new Quaternionf()));
			}
		}
	}

	private static void firework(ServerLevel level, ServerPlayer p) {
		double ang = level.getRandom().nextDouble() * Math.PI * 2;
		double r = 14 + level.getRandom().nextDouble() * 26;
		Vec3 at = world(0.5 + Math.cos(ang) * r, CelestialColosseum.F + 1, 0.5 + Math.sin(ang) * r);
		int[] colours = {0xC77DFF, 0xFFC24A, 0xFFFFFF, 0x9BE7FF, 0xFF6B6B};
		int c1 = colours[level.getRandom().nextInt(colours.length)];
		int c2 = colours[level.getRandom().nextInt(colours.length)];
		FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[level.getRandom().nextInt(FireworkExplosion.Shape.values().length)];
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + level.getRandom().nextInt(2), List.of(new FireworkExplosion(shape, IntList.of(c1, c2),
			IntList.of(0xFFFFFF), true, level.getRandom().nextBoolean()))));
		level.addFreshEntity(new FireworkRocketEntity(level, at.x, at.y, at.z, rocket));
	}

	// --- Sound and words ---

	private static void music(ServerPlayer p, Holder<SoundEvent> track) {
		p.connection.send(new ClientboundSoundPacket(track, SoundSource.RECORDS, p.getX(), p.getY(), p.getZ(), 1.0F, 1.0F, p.getRandom().nextLong()));
	}

	private static void stopMusic(ServerPlayer p) {
		p.connection.send(new ClientboundStopSoundPacket(SparBotContent.MUSIC_VAELOR.value().location(), SoundSource.RECORDS));
		p.connection.send(new ClientboundStopSoundPacket(SparBotContent.MUSIC_TRIUMPH.value().location(), SoundSource.RECORDS));
	}

	/** The crowd: the dead in the stands below, and the living in the colosseum above. */
	private static void crowd(ServerPlayer p, float volume) {
		p.connection.send(new ClientboundSoundPacket(SparBotContent.CROWD, SoundSource.AMBIENT, p.getX(), p.getY(), p.getZ(), volume, 1.0F,
			p.getRandom().nextLong()));
	}

	private static void say(ServerPlayer p, String line) {
		p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	/** Ends whatever is going on at once (the server stopping, or a test). */
	public void stop(MinecraftServer server, ServerLevel level) {
		ServerPlayer p = challenger == null ? null : server.getPlayerList().getPlayer(challenger);
		if (stage == Stage.VICTORY) {
			endVictory(server, level, p, false);
		} else if (stage != Stage.IDLE) {
			abandon(server, level, p);
		}
		if (boss != null) {
			boss.discard();
			boss = null;
		}
	}
}

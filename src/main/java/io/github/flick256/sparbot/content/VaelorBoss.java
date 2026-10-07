package io.github.flick256.sparbot.content;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vaelor, the Unbroken: the first champion of the Celestial Colosseum, bound under it with the star in his chest. Not a
 * bot: a boss, four blocks tall in star-iron plate with a greatsword, who fights in three phases. Everything he does is
 * shown before it lands (he winds up, the ground is marked), so a fight with him is about reading and moving:
 * <ol>
 * <li><b>The Oath</b> (full health to two thirds): Cleave (a wide sweep in front of him; a shield takes it), Overhead (a
 * blow along a marked line that knocks a shield aside), Lunge (he crouches, then charges where you are) and Guard
 * (his blade up: hits from the front barely land and he answers with a riposte, but get round him and he staggers).</li>
 * <li><b>Starfall</b> (two thirds to one third): he calls stars down on marked circles round you, and leaps to where you
 * stand; the landing sends a shockwave out along the ground, which you jump over.</li>
 * <li><b>The Unbroken</b> (the last third): faster, his blows chain, and the Star Lance: a beam he aims slowly, then fires
 * and drags after you. Get behind something.</li>
 * </ol>
 * He can't be hurt between phases, by anything but players, by falling, fire or lava, or by potions. When he falls he
 * kneels, the star goes out of him, and he is gone; {@link #defeated()} then says so and who did it.
 *
 * <p>His blows are his own damage types, which don't scale with difficulty (the same fight on Easy and Hard). The
 * numbers are set for the challenger's kit (full netherite with Protection IV): a Cleave takes about two hearts, an
 * Overhead three, a star two and a half.
 */
public class VaelorBoss extends Monster {
	public static final String NAME = "Vaelor, the Unbroken";
	public static final float MAX_HEALTH = 640;

	public enum Action {
		SEATED, RISE, IDLE, CLEAVE, OVERHEAD, LUNGE, GUARD, RIPOSTE, STAGGER, STARFALL, LEAP, LANCE, ROAR, KNEEL;
		static final Action[] ALL = values();
	}

	enum Mode { WAITING, FIGHT, DYING, DONE }

	// Raw damage of each blow, before armour.
	static final float CLEAVE_DMG = 26;
	static final float RIPOSTE_DMG = 22;
	static final float OVERHEAD_DMG = 34;
	static final float LUNGE_DMG = 28;
	static final float STAR_DMG = 30;
	static final float SLAM_DMG = 36;
	static final float WAVE_DMG = 18;
	static final float LANCE_DMG = 16;
	/** How long the windups are, in phases one, two and three. */
	public static final int[] CLEAVE_WIND = {14, 12, 9};
	public static final int[] OVERHEAD_WIND = {20, 16, 13};
	public static final int[] LUNGE_WIND = {16, 13, 11};
	static final int STARFALL_CAST = 24;
	static final int LEAP_CROUCH = 10;
	static final int LANCE_CHARGE = 34;
	static final int LANCE_FIRE = 36;
	static final int ROAR_TICKS = 70;
	static final int DEATH_TICKS = 120;
	public static final float DEATH_TICKS_CLIENT = DEATH_TICKS;
	static final double WAVE_MAX = 20;

	/** Action ordinal in the low five bits, and a count above that so the same action twice in a row still shows. */
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(VaelorBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(VaelorBoss.class, EntityDataSerializers.INT);

	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(NAME).withStyle(ChatFormatting.LIGHT_PURPLE,
		ChatFormatting.BOLD), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
	private Mode mode = Mode.WAITING;
	private int actionTick;
	private int serial;
	private @Nullable UUID targetId;
	private Vec3 centre = Vec3.ZERO;
	private double radius = 34;
	private Vec3 throne = Vec3.ZERO;
	private float throneYaw;
	private boolean bound;
	private int rest;
	private int starfallReady;
	private int leapReady;
	private int lanceReady;
	private int pendingPhase = 1;
	private final List<Strike> strikes = new ArrayList<>();
	private @Nullable Wave wave;
	private boolean wavesExplained;
	private Vec3 dash = Vec3.ZERO;
	private Vec3 leapFrom = Vec3.ZERO;
	private Vec3 leapTo = Vec3.ZERO;
	private float aimYaw;
	private float aimPitch;
	private final Set<UUID> struck = new HashSet<>();
	private final ArrayDeque<Integer> recentHits = new ArrayDeque<>();
	private boolean guardHit;
	private boolean heavy;
	private Action last = Action.IDLE;
	private int repeats;
	private int combo;
	private @Nullable UUID killer;
	private boolean defeated;
	private int clientActionStart;

	private record Strike(Vec3 at, int fallsAt) {
	}

	private static final class Wave {
		final Vec3 at;
		double r;
		final Set<UUID> hit = new HashSet<>();

		Wave(Vec3 at, double r) {
			this.at = at;
			this.r = r;
		}
	}

	public VaelorBoss(EntityType<? extends VaelorBoss> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setPersistenceRequired();
		this.setCustomName(Component.literal(NAME));
		this.bar.setDarkenScreen(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ARMOR_TOUGHNESS, 6.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 80.0)
			.add(Attributes.STEP_HEIGHT, 1.5)
			.add(Attributes.SAFE_FALL_DISTANCE, 256.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, Action.SEATED.ordinal());
		builder.define(DATA_PHASE, 1);
	}

	// --- What the client sees ---

	public Action action() {
		int a = entityData.get(DATA_ACTION) & 31;
		return a < Action.ALL.length ? Action.ALL[a] : Action.IDLE;
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** Ticks since the current action started (on the client, counted from when it heard). */
	public float actionTime(float partialTicks) {
		return level().isClientSide() ? tickCount - clientActionStart + partialTicks : actionTick;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ACTION.equals(accessor)) {
			clientActionStart = tickCount;
		}
	}

	// --- What the encounter asks of him ---

	/**
	 * Where he lives: his throne (where he waits, facing {@code yaw}) and the arena he fights in. Sends him to the
	 * throne, healed, if he isn't fighting.
	 */
	public void bind(Vec3 throne, float yaw, Vec3 centre, double radius) {
		this.throne = throne;
		this.throneYaw = yaw;
		this.centre = centre;
		this.radius = radius;
		this.bound = true;
		if (mode == Mode.WAITING) {
			standDown();
		}
	}

	/** Rises from his throne to fight {@code challenger}. */
	public void challenge(ServerPlayer challenger) {
		targetId = challenger.getUUID();
		mode = Mode.FIGHT;
		killer = null;
		defeated = false;
		setHealth(getMaxHealth());
		setPhase(1);
		pendingPhase = 1;
		combo = 0;
		starfallReady = tickCount + 120;
		leapReady = tickCount + 160;
		lanceReady = tickCount;
		recentHits.clear();
		strikes.clear();
		wave = null;
		wavesExplained = false;
		act(Action.RISE);
		if (level() instanceof ServerLevel level) {
			sound(level, SoundEvents.WARDEN_EMERGE, 2.0F, 0.7F);
			say(level, "Another one comes down the stair.");
		}
	}

	/** Back to his throne, healed, waiting. */
	public void standDown() {
		mode = Mode.WAITING;
		targetId = null;
		strikes.clear();
		wave = null;
		setHealth(getMaxHealth());
		setPhase(1);
		pendingPhase = 1;
		bar.removeAllPlayers();
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		if (bound) {
			teleportTo(throne.x, throne.y, throne.z);
			face(throneYaw, 360);
		}
		act(Action.SEATED);
	}

	public boolean fighting() {
		return mode == Mode.FIGHT || mode == Mode.DYING;
	}

	public boolean waiting() {
		return mode == Mode.WAITING;
	}

	/** Whether a challenger brought him down (he's gone from the world once the kneeling is over). */
	public boolean defeated() {
		return defeated;
	}

	public @Nullable UUID killer() {
		return killer;
	}

	public @Nullable UUID challenger() {
		return targetId;
	}

	// --- The fight ---

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		actionTick++;
		if (tickCount % 10 == 0) {
			syncBar(level);
		}
		switch (mode) {
			case WAITING -> waitOnThrone(level);
			case FIGHT -> fight(level);
			case DYING -> dying(level);
			case DONE -> {
			}
		}
		tickStrikes(level);
		tickWave(level);
		bar.setProgress(mode == Mode.FIGHT ? Mth.clamp(getHealth() / getMaxHealth(), 0, 1) : 0);
	}

	private void waitOnThrone(ServerLevel level) {
		getNavigation().stop();
		if (bound && position().distanceToSqr(throne) > 0.25) {
			teleportTo(throne.x, throne.y, throne.z);
		}
		setDeltaMovement(0, getDeltaMovement().y, 0);
		Player near = level.getNearestPlayer(this, 24);
		if (near != null && !near.isSpectator()) {
			getLookControl().setLookAt(near, 10, 10);
		}
		yBodyRot = throneYaw;
		setYRot(throneYaw);
		if (action() != Action.SEATED) {
			act(Action.SEATED);
		}
	}

	private @Nullable ServerPlayer target(ServerLevel level) {
		if (targetId == null) {
			return null;
		}
		Player p = level.getPlayerByUUID(targetId);
		return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator() ? sp : null;
	}

	private void fight(ServerLevel level) {
		ServerPlayer t = target(level);
		leash();
		if (t == null) {
			// The challenger is down or gone: the encounter decides what happens next.
			getNavigation().stop();
			if (action() != Action.IDLE && action() != Action.RISE) {
				act(Action.IDLE);
			}
			return;
		}
		Action a = action();
		if (pendingPhase > phase() && a != Action.LEAP && a != Action.ROAR && a != Action.RISE) {
			roarStart(level);
			return;
		}
		switch (a) {
			case RISE -> {
				face(t, 6);
				if (actionTick == 1) {
					say(level, "Two hundred came before you. Let us see what you are.");
				}
				if (actionTick >= 36) {
					idle(10);
				}
			}
			case IDLE -> idleStep(level, t);
			case CLEAVE, RIPOSTE -> cleave(level, t, a);
			case OVERHEAD -> overhead(level, t);
			case LUNGE -> lunge(level, t);
			case GUARD -> guard(level, t);
			case STAGGER -> {
				getNavigation().stop();
				if (actionTick >= 28) {
					idle(4);
				}
			}
			case STARFALL -> starfall(level, t);
			case LEAP -> leap(level, t);
			case LANCE -> lance(level, t);
			case ROAR -> roar(level);
			default -> idle(10);
		}
	}

	private int p(int[] byPhase) {
		return byPhase[Mth.clamp(phase() - 1, 0, 2)];
	}

	private boolean enraged() {
		return phase() >= 3 && getHealth() < getMaxHealth() * 0.15F;
	}

	private void idle(int restTicks) {
		act(Action.IDLE);
		rest = enraged() ? restTicks * 3 / 5 : restTicks;
	}

	private void idleStep(ServerLevel level, ServerPlayer t) {
		double d = flatDistance(t.position());
		face(t, 20);
		if (combo > 0 && d <= 5.5) {
			combo--;
			start(level, t, Action.CLEAVE);
			return;
		}
		combo = 0;
		if (--rest > 0) {
			approach(t, d);
			return;
		}
		start(level, t, choose(d, t));
	}

	private void approach(ServerPlayer t, double d) {
		if (d > 3.2) {
			double speed = switch (phase()) {
				case 1 -> 1.0;
				case 2 -> 1.12;
				default -> 1.3;
			};
			getNavigation().moveTo(t, enraged() ? speed * 1.1 : speed);
		} else {
			getNavigation().stop();
		}
	}

	private Action choose(double d, ServerPlayer t) {
		List<Action> pool = new ArrayList<>();
		boolean p2 = phase() >= 2;
		boolean p3 = phase() >= 3;
		boolean high = t.getY() > centre.y + 3.5 || flatDistance(t.position(), centre) > radius + 1;
		if (high && leapReady <= tickCount + 80) {
			// Up on the wall or out of reach: he comes to you.
			return Action.LEAP;
		}
		if (d <= 5.0) {
			add(pool, Action.CLEAVE, 4);
			add(pool, Action.OVERHEAD, 3);
			if (!p3 && pressed()) {
				add(pool, Action.GUARD, 4);
			}
		} else if (d <= 7.0) {
			add(pool, Action.OVERHEAD, 3);
			add(pool, Action.LUNGE, 2);
		} else {
			add(pool, Action.LUNGE, 3);
		}
		if (p2 && tickCount >= starfallReady) {
			add(pool, Action.STARFALL, d > 7 ? 4 : 2);
		}
		if (p2 && tickCount >= leapReady && d > 6) {
			add(pool, Action.LEAP, 3);
		}
		if (p3 && tickCount >= lanceReady && d > 6) {
			add(pool, Action.LANCE, 4);
		}
		if (repeats >= 2 && pool.size() > 1) {
			pool.removeIf(x -> x == last);
		}
		if (pool.isEmpty()) {
			return Action.LUNGE;
		}
		return pool.get(random.nextInt(pool.size()));
	}

	private static void add(List<Action> pool, Action a, int weight) {
		for (int i = 0; i < weight; i++) {
			pool.add(a);
		}
	}

	/** Whether the challenger has landed three blows in the last three seconds. */
	private boolean pressed() {
		while (!recentHits.isEmpty() && recentHits.peekFirst() < tickCount - 60) {
			recentHits.pollFirst();
		}
		return recentHits.size() >= 3;
	}

	private void start(ServerLevel level, ServerPlayer t, Action a) {
		repeats = a == last ? repeats + 1 : 0;
		last = a;
		getNavigation().stop();
		switch (a) {
			case STARFALL -> starfallReady = tickCount + (phase() >= 3 ? 200 : 260);
			case LEAP -> leapReady = tickCount + 170;
			case LANCE -> lanceReady = tickCount + 280;
			default -> {
			}
		}
		act(a);
	}

	private void cleave(ServerLevel level, ServerPlayer t, Action a) {
		int wind = a == Action.RIPOSTE ? 5 : combo > 0 || last == Action.CLEAVE && repeats > 0 ? p(CLEAVE_WIND) - 3 : p(CLEAVE_WIND);
		getNavigation().stop();
		if (actionTick == 1) {
			sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.6F, 0.6F);
		}
		if (actionTick < wind) {
			face(t, a == Action.RIPOSTE ? 40 : 9);
			Vec3 side = forward().yRot((float) Math.toRadians(-70)).scale(2.0);
			particles(level, ParticleTypes.CRIT, getX() + side.x, getY() + 2.6, getZ() + side.z, 2, 0.3, 0.3, 0.3, 0.05);
			return;
		}
		if (actionTick == wind) {
			sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
			Vec3 f = forward();
			for (int i = -3; i <= 3; i++) {
				Vec3 v = f.yRot((float) Math.toRadians(i * 22)).scale(3.4);
				particles(level, ParticleTypes.SWEEP_ATTACK, getX() + v.x, getY() + 1.4, getZ() + v.z, 1, 0, 0, 0, 0);
			}
			sweep(level, 5.4, 75, a == Action.RIPOSTE ? RIPOSTE_DMG : CLEAVE_DMG);
			if (phase() >= 3 && a == Action.CLEAVE && combo == 0 && random.nextFloat() < 0.45F) {
				combo = 1;
			}
		}
		if (actionTick >= wind + 12) {
			idle(combo > 0 ? 1 : p(new int[] {18, 13, 9}));
		}
	}

	private void overhead(ServerLevel level, ServerPlayer t) {
		int wind = p(OVERHEAD_WIND);
		getNavigation().stop();
		if (actionTick == 1) {
			sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 2.0F, 0.5F);
			sound(level, SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.5F);
		}
		if (actionTick < wind) {
			if (actionTick < wind * 2 / 3) {
				face(t, 7);
			}
			if (actionTick % 3 == 0) {
				line(level, forward(), 7.5, new DustParticleOptions(0xFF3B2F, 1.4F));
			}
			return;
		}
		if (actionTick == wind) {
			heavy = true;
			sound(level, SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
			sound(level, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
			Vec3 f = forward();
			line(level, f, 7.5, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()));
			line(level, f, 7.5, ParticleTypes.CRIT);
			Vec3 end = position().add(f.scale(7.5));
			particles(level, ParticleTypes.EXPLOSION, end.x, end.y + 0.3, end.z, 1, 0, 0, 0, 0);
			for (LivingEntity e : victims(level, 9)) {
				Vec3 to = e.position().subtract(position());
				double along = to.x * f.x + to.z * f.z;
				double across = Math.abs(-to.x * f.z + to.z * f.x);
				if (along > 0.3 && along < 7.8 && across < 1.4 + e.getBbWidth() / 2 && Math.abs(to.y) < 3) {
					hit(level, e, SparBotContent.BLADE, OVERHEAD_DMG, f.scale(0.6).add(0, 0.55, 0));
				}
			}
			heavy = false;
		}
		if (actionTick >= wind + 16) {
			idle(p(new int[] {16, 12, 8}));
		}
	}

	private void lunge(ServerLevel level, ServerPlayer t) {
		int wind = p(LUNGE_WIND);
		if (actionTick < wind) {
			getNavigation().stop();
			face(t, 12);
			if (actionTick == 1) {
				sound(level, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.6F, 0.7F);
			}
			particles(level, new DustParticleOptions(0xFF3B2F, 1.2F), getX(), getY() + 0.2, getZ(), 4, 0.6, 0.1, 0.6, 0);
			return;
		}
		if (actionTick == wind) {
			Vec3 aim = t.position().add(t.getDeltaMovement().multiply(4, 0, 4)).subtract(position());
			aim = new Vec3(aim.x, 0, aim.z);
			double len = Math.min(aim.length() + 3, 22);
			dash = aim.lengthSqr() < 1.0E-4 ? forward() : aim.normalize();
			dash = dash.scale(len / 10.0);
			face((float) Math.toDegrees(Math.atan2(-dash.x, dash.z)), 360);
			sound(level, SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.6F);
		}
		if (actionTick >= wind && actionTick < wind + 10) {
			heavy = true;
			Vec3 next = position().add(dash);
			if (flatDistance(next, centre) > radius - 2.0) {
				setDeltaMovement(0, getDeltaMovement().y, 0);
				actionTick = wind + 10;
			} else {
				setDeltaMovement(dash.x, Math.min(getDeltaMovement().y, 0), dash.z);
				particles(level, ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
				for (LivingEntity e : victims(level, 3)) {
					if (!struck.contains(e.getUUID()) && e.getBoundingBox().intersects(getBoundingBox().inflate(0.7, 0, 0.7))) {
						struck.add(e.getUUID());
						hit(level, e, SparBotContent.BLADE, LUNGE_DMG, dash.normalize().scale(1.4).add(0, 0.45, 0));
					}
				}
			}
			heavy = false;
			return;
		}
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (actionTick >= wind + 26) {
			idle(p(new int[] {14, 10, 6}));
		}
	}

	private void guard(ServerLevel level, ServerPlayer t) {
		getNavigation().stop();
		face(t, 14);
		if (actionTick == 1) {
			sound(level, SoundEvents.SHIELD_BLOCK.value(), 1.5F, 0.6F);
		}
		if (guardHit) {
			guardHit = false;
			act(Action.RIPOSTE);
			return;
		}
		if (actionTick % 4 == 0) {
			Vec3 f = forward().scale(1.2);
			particles(level, ParticleTypes.ENCHANTED_HIT, getX() + f.x, getY() + 2.4, getZ() + f.z, 3, 0.5, 0.8, 0.5, 0.02);
		}
		if (actionTick >= 44) {
			idle(6);
		}
	}

	private void starfall(ServerLevel level, ServerPlayer t) {
		getNavigation().stop();
		face(t, 10);
		int cast = phase() >= 3 ? STARFALL_CAST - 6 : STARFALL_CAST;
		if (actionTick == 1) {
			sound(level, SoundEvents.BEACON_ACTIVATE, 2.5F, 0.6F);
			sound(level, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 0.5F);
		}
		if (actionTick < cast) {
			double a = actionTick * 0.6;
			for (int k = 0; k < 3; k++) {
				double r = 1.4 - actionTick * 0.03;
				double ang = a + k * Math.PI * 2 / 3;
				particles(level, ParticleTypes.END_ROD, getX() + Math.cos(ang) * r, getY() + 1 + actionTick * 0.2, getZ() + Math.sin(ang) * r, 1, 0, 0, 0,
					0);
			}
			return;
		}
		if (actionTick == cast) {
			int n = (phase() >= 3 ? 10 : 7) + (enraged() ? 3 : 0);
			List<Vec3> marks = new ArrayList<>();
			marks.add(ground(t.position()));
			for (int tries = 0; marks.size() < n && tries < 200; tries++) {
				double ang = random.nextDouble() * Math.PI * 2;
				double r = 2 + random.nextDouble() * 8;
				Vec3 m = ground(t.position().add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
				if (flatDistance(m, centre) > radius - 1.5) {
					continue;
				}
				boolean apart = true;
				for (Vec3 o : marks) {
					apart &= flatDistance(m, o) >= 2.6;
				}
				if (apart) {
					marks.add(m);
				}
			}
			for (int i = 0; i < marks.size(); i++) {
				strikes.add(new Strike(marks.get(i), tickCount + 32 + i * 3));
			}
			sound(level, SoundEvents.BELL_RESONATE, 2.5F, 1.4F);
			say(level, phase() >= 3 ? "Fall, stars. All of you." : "Look up, challenger.");
		}
		if (actionTick >= cast + 10) {
			idle(p(new int[] {20, 20, 12}));
		}
	}

	private void tickStrikes(ServerLevel level) {
		if (strikes.isEmpty()) {
			return;
		}
		DustParticleOptions gold = new DustParticleOptions(0xFFC24A, 1.5F);
		for (int i = strikes.size() - 1; i >= 0; i--) {
			Strike s = strikes.get(i);
			int left = s.fallsAt - tickCount;
			if (left > 0) {
				if (tickCount % 3 == 0) {
					ring(level, s.at, 2.3, 18, gold);
				}
				if (left <= 12) {
					double y = s.at.y + left * 1.4;
					particles(level, ParticleTypes.END_ROD, s.at.x, y, s.at.z, 3, 0.15, 0.4, 0.15, 0.0);
					particles(level, ParticleTypes.FLAME, s.at.x, y, s.at.z, 2, 0.2, 0.2, 0.2, 0.01);
				}
				continue;
			}
			strikes.remove(i);
			particles(level, ParticleTypes.EXPLOSION, s.at.x, s.at.y + 0.5, s.at.z, 2, 0.6, 0.3, 0.6, 0);
			particles(level, ParticleTypes.FLAME, s.at.x, s.at.y + 0.3, s.at.z, 30, 1.0, 0.3, 1.0, 0.08);
			particles(level, ParticleTypes.END_ROD, s.at.x, s.at.y + 0.5, s.at.z, 16, 0.6, 0.6, 0.6, 0.2);
			level.playSound(null, s.at.x, s.at.y, s.at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 2.5F, 0.7F);
			level.playSound(null, s.at.x, s.at.y, s.at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.at, s.at).inflate(3, 3, 3), this::victim)) {
				if (flatDistance(e.position(), s.at) <= 2.4 + e.getBbWidth() / 2 && Math.abs(e.getY() - s.at.y) < 2.5) {
					Vec3 out = e.position().subtract(s.at);
					out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(out.x, 0, out.z).normalize().scale(0.5);
					hit(level, e, SparBotContent.STAR, STAR_DMG, out.add(0, 0.5, 0));
				}
			}
		}
	}

	private void leap(ServerLevel level, ServerPlayer t) {
		int flight = phase() >= 3 ? 20 : 24;
		getNavigation().stop();
		if (actionTick < LEAP_CROUCH) {
			face(t, 20);
			particles(level, ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 3, 0.7, 0.05, 0.7, 0.02);
			return;
		}
		if (actionTick == LEAP_CROUCH) {
			leapFrom = position();
			Vec3 to = t.position();
			if (flatDistance(to, centre) > radius - 2) {
				Vec3 in = new Vec3(to.x - centre.x, 0, to.z - centre.z).normalize().scale(radius - 2);
				to = centre.add(in);
			}
			leapTo = ground(new Vec3(to.x, Math.max(to.y, centre.y), to.z));
			face((float) Math.toDegrees(Math.atan2(-(leapTo.x - getX()), leapTo.z - getZ())), 360);
			sound(level, SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.5F);
			sound(level, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.6F);
		}
		int ft = actionTick - LEAP_CROUCH;
		if (ft <= flight) {
			double s = ft / (double) flight;
			double height = 10;
			Vec3 want = leapFrom.lerp(leapTo, s).add(0, 4 * height * s * (1 - s), 0);
			setDeltaMovement(want.subtract(position()));
			if (ft % 2 == 0) {
				ring(level, leapTo, 3.4, 22, new DustParticleOptions(0xFF3B2F, 1.6F));
				particles(level, ParticleTypes.FLAME, leapTo.x, leapTo.y + 0.1, leapTo.z, 3, 0.8, 0.05, 0.8, 0.0);
			}
			if (ft == flight) {
				setDeltaMovement(Vec3.ZERO);
				teleportTo(leapTo.x, leapTo.y, leapTo.z);
				sound(level, SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.6F);
				sound(level, SoundEvents.ANVIL_LAND, 2.5F, 0.4F);
				particles(level, ParticleTypes.EXPLOSION_EMITTER, leapTo.x, leapTo.y + 0.5, leapTo.z, 1, 0, 0, 0, 0);
				for (LivingEntity e : victims(level, 5)) {
					if (flatDistance(e.position(), leapTo) <= 3.4 + e.getBbWidth() / 2 && Math.abs(e.getY() - leapTo.y) < 3) {
						Vec3 out = new Vec3(e.getX() - leapTo.x, 0, e.getZ() - leapTo.z);
						out = out.lengthSqr() < 1.0E-4 ? forward() : out.normalize();
						hit(level, e, SparBotContent.STAR, SLAM_DMG, out.scale(1.2).add(0, 0.6, 0));
					}
				}
				wave = new Wave(leapTo, 3.4);
				if (!wavesExplained) {
					wavesExplained = true;
					say(level, "The ground itself answers me. Jump, if you can.");
				}
			}
			return;
		}
		if (actionTick >= LEAP_CROUCH + flight + 20) {
			idle(p(new int[] {16, 16, 10}));
		}
	}

	private void tickWave(ServerLevel level) {
		Wave w = wave;
		if (w == null) {
			return;
		}
		w.r += 0.9;
		if (w.r > WAVE_MAX || w.r > radius + flatDistance(w.at, centre) + 2) {
			wave = null;
			return;
		}
		int n = (int) (w.r * 5);
		ring(level, w.at.add(0, 0.15, 0), w.r, n, new DustParticleOptions(0xFFB347, 1.8F));
		if ((int) (w.r * 10) % 3 == 0) {
			ring(level, w.at.add(0, 0.2, 0), w.r, n / 3, ParticleTypes.CLOUD);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(w.at, w.at).inflate(w.r + 2, 3, w.r + 2), this::victim)) {
			double d = flatDistance(e.position(), w.at);
			if (Math.abs(d - w.r) <= 0.9 && e.onGround() && Math.abs(e.getY() - w.at.y) < 1.2 && w.hit.add(e.getUUID())) {
				Vec3 out = new Vec3(e.getX() - w.at.x, 0, e.getZ() - w.at.z).normalize();
				hit(level, e, SparBotContent.STAR, WAVE_DMG, out.scale(0.6).add(0, 0.5, 0));
			}
		}
	}

	private void lance(ServerLevel level, ServerPlayer t) {
		getNavigation().stop();
		Vec3 eye = new Vec3(getX(), getY() + 2.5, getZ());
		if (actionTick == 1) {
			aimYaw = getYRot();
			aimPitch = 0;
			sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.8F);
			sound(level, SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
			say(level, "Find something to hide behind.");
		}
		Vec3 aimAt = t.position().add(0, 1.0, 0);
		float wantYaw = (float) Math.toDegrees(Math.atan2(-(aimAt.x - eye.x), aimAt.z - eye.z));
		double flat = Math.sqrt((aimAt.x - eye.x) * (aimAt.x - eye.x) + (aimAt.z - eye.z) * (aimAt.z - eye.z));
		float wantPitch = (float) Math.toDegrees(Math.atan2(eye.y - aimAt.y, flat));
		boolean firing = actionTick >= LANCE_CHARGE;
		float turn = firing ? (enraged() ? 1.5F : 1.1F) : 3.5F;
		aimYaw = Mth.approachDegrees(aimYaw, wantYaw, turn);
		aimPitch = Mth.approachDegrees(aimPitch, Mth.clamp(wantPitch, -30, 40), 2.0F);
		face(aimYaw, 360);
		Vec3 dir = Vec3.directionFromRotation(aimPitch, aimYaw);
		Vec3 far = eye.add(dir.scale(44));
		HitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		double len = end.distanceTo(eye);
		if (!firing) {
			if (actionTick % 3 == 0) {
				for (double s = 2; s < len; s += 2.0) {
					Vec3 p = eye.add(dir.scale(s));
					particles(level, new DustParticleOptions(0xFF5A4A, 0.9F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
			particles(level, ParticleTypes.END_ROD, eye.x + dir.x * 2, eye.y, eye.z + dir.z * 2, 3, 0.3, 0.3, 0.3, 0.05);
			return;
		}
		if (actionTick == LANCE_CHARGE) {
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.9F);
		}
		if (actionTick % 8 == 0) {
			sound(level, SoundEvents.BEACON_AMBIENT, 3.0F, 1.8F);
		}
		for (double s = 1.5; s < len; s += 0.7) {
			Vec3 p = eye.add(dir.scale(s));
			particles(level, ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
		}
		if (actionTick % 4 == 0) {
			particles(level, ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		}
		for (LivingEntity e : victims(level, 46)) {
			Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
			double along = c.subtract(eye).dot(dir);
			if (along < 0 || along > len) {
				continue;
			}
			double off = c.subtract(eye.add(dir.scale(along))).length();
			if (off <= 0.9 + e.getBbWidth() / 2) {
				hit(level, e, SparBotContent.STAR, LANCE_DMG, dir.scale(0.3));
			}
		}
		if (actionTick >= LANCE_CHARGE + LANCE_FIRE) {
			idle(14);
		}
	}

	private void roarStart(ServerLevel level) {
		strikes.clear();
		getNavigation().stop();
		act(Action.ROAR);
	}

	private void roar(ServerLevel level) {
		getNavigation().stop();
		setDeltaMovement(0, getDeltaMovement().y, 0);
		int next = pendingPhase;
		if (actionTick == 1) {
			sound(level, SoundEvents.WARDEN_ROAR, 3.0F, 0.8F);
			sound(level, SoundEvents.RAID_HORN.value(), 3.0F, 0.9F);
			for (LivingEntity e : victims(level, 9)) {
				Vec3 out = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
				out = out.lengthSqr() < 1.0E-4 ? forward() : out.normalize();
				e.push(out.x * 1.6, 0.5, out.z * 1.6);
				e.hurtMarked = true;
			}
			ring(level, position().add(0, 0.2, 0), 3, 30, next >= 3 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME);
			say(level, next >= 3 ? "I have not knelt in a thousand years. I will not kneel to you!" : "Enough. The stars still answer me.");
		}
		if (actionTick % 4 == 0) {
			ring(level, position().add(0, 0.2, 0), 2 + actionTick * 0.12, 24, next >= 3 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME);
			particles(level, ParticleTypes.END_ROD, getX(), getY() + 3.2, getZ(), 6, 0.8, 0.8, 0.8, 0.1);
		}
		if (actionTick == ROAR_TICKS / 2) {
			setPhase(next);
			sound(level, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.5F);
		}
		if (actionTick >= ROAR_TICKS) {
			idle(8);
		}
	}

	private void setPhase(int phase) {
		entityData.set(DATA_PHASE, phase);
		bar.setColor(switch (phase) {
			case 1 -> BossEvent.BossBarColor.PURPLE;
			case 2 -> BossEvent.BossBarColor.YELLOW;
			default -> BossEvent.BossBarColor.RED;
		});
	}

	private void dying(ServerLevel level) {
		getNavigation().stop();
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (actionTick == 1) {
			sound(level, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
			say(level, "...So. The oath is kept.");
		}
		if (actionTick == 50) {
			say(level, "Take it. Take all of it. Wear it better than I did.");
			sound(level, SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
		}
		if (actionTick % 2 == 0) {
			particles(level, ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 4 + actionTick / 10, 0.7, 1.2, 0.7, 0.04);
			particles(level, ParticleTypes.SOUL, getX(), getY() + 2.2, getZ(), 2, 0.5, 0.6, 0.5, 0.02);
		}
		if (actionTick % 20 == 10) {
			particles(level, ParticleTypes.EXPLOSION, getX() + random.nextGaussian() * 0.8, getY() + 1 + random.nextDouble() * 2,
				getZ() + random.nextGaussian() * 0.8, 1, 0, 0, 0, 0);
			sound(level, SoundEvents.AMETHYST_BLOCK_BREAK, 2.5F, 0.5F + actionTick / 200.0F);
		}
		if (actionTick >= DEATH_TICKS) {
			particles(level, ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
			particles(level, ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 160, 0.6, 1.5, 0.6, 0.4);
			particles(level, ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 2, getZ(), 120, 0.8, 1.5, 0.8, 0.6);
			sound(level, SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.7F);
			sound(level, SoundEvents.TOTEM_USE, 3.0F, 0.7F);
			defeated = true;
			mode = Mode.DONE;
			bar.removeAllPlayers();
			remove(RemovalReason.KILLED);
		}
	}

	// --- Helpers ---

	private void act(Action a) {
		actionTick = 0;
		struck.clear();
		heavy = false;
		serial = (serial + 1) & 0xFFFF;
		entityData.set(DATA_ACTION, a.ordinal() | serial << 5);
	}

	private Vec3 forward() {
		return Vec3.directionFromRotation(0, getYRot());
	}

	private void face(ServerPlayer t, float maxTurn) {
		face((float) Math.toDegrees(Math.atan2(-(t.getX() - getX()), t.getZ() - getZ())), maxTurn);
	}

	private void face(float yaw, float maxTurn) {
		float y = Mth.approachDegrees(getYRot(), yaw, maxTurn);
		setYRot(y);
		yBodyRot = y;
		setYHeadRot(y);
	}

	private double flatDistance(Vec3 to) {
		return flatDistance(position(), to);
	}

	private static double flatDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The point on the arena floor under {@code at}. */
	private Vec3 ground(Vec3 at) {
		return new Vec3(at.x, bound ? centre.y : at.y, at.z);
	}

	private void leash() {
		if (!bound) {
			return;
		}
		if (flatDistance(position(), centre) > radius + 3 || getY() < centre.y - 4) {
			teleportTo(centre.x, centre.y, centre.z);
			setDeltaMovement(Vec3.ZERO);
		}
	}

	private boolean victim(LivingEntity e) {
		return e != this && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative());
	}

	private List<LivingEntity> victims(ServerLevel level, double range) {
		return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, 4, range), this::victim);
	}

	/** A sweep in front: everything within {@code reach} and {@code halfAngle} degrees of where he faces. */
	private void sweep(ServerLevel level, double reach, double halfAngle, float damage) {
		Vec3 f = forward();
		for (LivingEntity e : victims(level, reach + 1)) {
			Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
			double d = to.length();
			if (d > reach + e.getBbWidth() / 2 || Math.abs(e.getY() - getY()) > 3.5) {
				continue;
			}
			if (d > 0.8 && to.normalize().dot(f) < Math.cos(Math.toRadians(halfAngle))) {
				continue;
			}
			Vec3 out = d < 1.0E-3 ? f : to.normalize();
			hit(level, e, SparBotContent.BLADE, damage, out.scale(0.9).add(0, 0.35, 0));
		}
	}

	private void hit(ServerLevel level, LivingEntity e, ResourceKey<DamageType> type, float damage, Vec3 push) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), this, this);
		boolean blocking = e.isBlocking();
		if (e.hurtServer(level, source, damage)) {
			e.push(push.x, push.y, push.z);
			e.hurtMarked = true;
		} else if (blocking) {
			e.push(push.x * 0.5, 0.1, push.z * 0.5);
			e.hurtMarked = true;
		}
	}

	/** His heavy blows knock a shield aside for five seconds, like an axe. */
	@Override
	public float getSecondsToDisableBlocking() {
		return heavy ? 5.0F : 0.0F;
	}

	private void line(ServerLevel level, Vec3 dir, double length, ParticleOptions particle) {
		for (double s = 1; s <= length; s += 0.5) {
			particles(level, particle, getX() + dir.x * s, getY() + 0.15, getZ() + dir.z * s, 1, 0.1, 0.02, 0.1, 0);
		}
	}

	private void ring(ServerLevel level, Vec3 at, double r, int points, ParticleOptions particle) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			particles(level, particle, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	private static void particles(ServerLevel level, ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz,
		double speed) {
		level.sendParticles(p, true, false, x, y, z, count, dx, dy, dz, speed);
	}

	private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, getX(), getY() + 2, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	/** Says a line to everyone who can see his bar. */
	private void say(ServerLevel level, String line) {
		Component c = Component.literal("Vaelor: ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
			.append(Component.literal(line).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withBold(false).withItalic(true)));
		for (ServerPlayer p : bar.getPlayers()) {
			p.sendSystemMessage(c);
		}
		if (bar.getPlayers().isEmpty() && target(level) instanceof ServerPlayer t) {
			t.sendSystemMessage(c);
		}
	}

	/** His bar shows for everyone down in the deep with him while he fights. */
	private void syncBar(ServerLevel level) {
		if (mode != Mode.FIGHT && mode != Mode.DYING) {
			bar.removeAllPlayers();
			return;
		}
		for (ServerPlayer p : level.players()) {
			boolean near = flatDistance(p.position(), bound ? centre : position()) < 110 && Math.abs(p.getY() - (bound ? centre.y : getY())) < 90;
			if (near) {
				bar.addPlayer(p);
			} else {
				bar.removePlayer(p);
			}
		}
	}

	// --- What can and can't hurt him ---

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (mode != Mode.FIGHT) {
			return true;
		}
		Action a = action();
		if (a == Action.RISE || a == Action.ROAR) {
			return true;
		}
		return !(source.getEntity() instanceof Player) || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isInvulnerableTo(level, source)) {
			return false;
		}
		Action a = action();
		Vec3 from = source.getSourcePosition();
		if (a == Action.GUARD && from != null) {
			Vec3 to = new Vec3(from.x - getX(), 0, from.z - getZ());
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(forward()) > Math.cos(Math.toRadians(80))) {
				damage *= 0.2F;
				guardHit = true;
				sound(level, SoundEvents.SHIELD_BLOCK.value(), 2.0F, 0.5F);
			} else {
				damage *= 1.25F;
				act(Action.STAGGER);
				sound(level, SoundEvents.ANVIL_BREAK, 1.5F, 0.7F);
				say(level, "Behind me? Clever.");
			}
		} else if (a == Action.STAGGER) {
			damage *= 1.25F;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && mode == Mode.FIGHT) {
			recentHits.addLast(tickCount);
			if (source.getEntity() instanceof ServerPlayer p) {
				killer = p.getUUID();
			}
			float frac = getHealth() / getMaxHealth();
			if (pendingPhase < 2 && frac <= 2 / 3.0F) {
				pendingPhase = 2;
			}
			if (pendingPhase < 3 && frac <= 1 / 3.0F) {
				pendingPhase = 3;
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (mode == Mode.FIGHT && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// He kneels instead: see dying().
			setHealth(1.0F);
			mode = Mode.DYING;
			strikes.clear();
			wave = null;
			if (source.getEntity() instanceof ServerPlayer p) {
				killer = p.getUUID();
			}
			act(Action.KNEEL);
			return;
		}
		if (mode == Mode.DYING) {
			setHealth(1.0F);
			return;
		}
		mode = Mode.DONE;
		bar.removeAllPlayers();
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		this.noActionTime = 0;
	}

	/** Never written to the world: the encounter puts him back on his throne when the deep is loaded. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	public float getVoicePitch() {
		return 0.55F;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
	}
}

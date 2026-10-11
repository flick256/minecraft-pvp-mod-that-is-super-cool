package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotManager;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.drill.Drill;
import io.github.flick256.sparbot.core.drill.DrillTally;
import io.github.flick256.sparbot.core.drill.Feedback;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.style.Playstyle;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.practice.PracticeWorld;
import io.github.flick256.sparbot.practice.TrainingHall;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs the skill drills in the training hall, and watches fights against bots for the live coach and the
 * fight report.
 *
 * <p>A drill takes a free bay, keeps your inventory safe (as a match does), and runs its three stages (learn
 * with cues, practise, under pressure; see {@link Drill}) against a bot of its own. Every attempt is judged
 * the moment it happens ({@link Feedback}) and told to you on the action bar with a sound; a stage ends the
 * moment enough good reps are in, and one that runs out of time starts again with a tip. You can't die in a
 * drill: what would kill you heals you instead.
 */
public final class DrillManager {
	private static final int COUNTDOWN = 60;
	private static final int BREAK = 50;
	/** A hit within this long of disabling the bot's shield follows the stun up. */
	private static final int STUN_FOLLOW_UP = 20;
	/** A jump this soon after (or just before) a hit lands on you is a jump reset attempt. */
	private static final int JUMP_RESET_AFTER = 5;
	private static final int JUMP_RESET_BEFORE = 6;
	/** A combo is over once this long passes without another hit. */
	private static final int COMBO_GAP = 30;
	/** How long after its missed swing the bot can still be punished at all (the rep's own mark is tighter). */
	private static final int PUNISH_LIMIT = 20;
	/** Wandering this far from the bay ends the drill. */
	private static final double LEASH = 40;
	/** The attacker pattern swings this often (ticks). */
	private static final int ATTACK_RHYTHM = 40;
	/** Health below this in a drill is topped back up. */
	private static final float TOP_UP_BELOW = 8.0F;

	private enum Phase {
		COUNTDOWN,
		RUN,
		BREAK
	}

	private static final class Session {
		final UUID player;
		/** The drill, or null when this only watches a fight against a bot (for the coach and the report). */
		final @Nullable Drill drill;
		final int bay;
		final String botName;
		int stage;
		int attempt;
		Phase phase = Phase.COUNTDOWN;
		int phaseTicks;
		DrillTally tally = new DrillTally();
		int passed;
		/** The stage's scripted bot moves, or null for the brain. */
		@Nullable Object pattern;
		// The melee click being resolved this tick.
		long attackTick = -1;
		double charge;
		boolean crit;
		boolean sprint;
		boolean onGround;
		boolean falling;
		boolean mace;
		double reach;
		double fall;
		// Shields.
		long stunCheckAt = -1;
		long stunAt = -1;
		boolean shieldCooling;
		// Jump resets: a hit taken on the ground waits a few ticks for the jump.
		long hitTakenAt = -1;
		int lastJumps;
		long lastJumpAt = Long.MIN_VALUE / 2;
		// Combos.
		int comboHits;
		long lastOwnHitAt = Long.MIN_VALUE / 2;
		// The bot's swings, and a missed one to punish.
		boolean botWasSwinging;
		int botLastSwingTime;
		long botSwingAt = -1;
		long whiffAt = -1;
		long lastHurtByBotAt = Long.MIN_VALUE / 2;
		// First hits.
		boolean exchangeOpen;
		long resetAt = -1;
		// Webs, fire and totems.
		boolean inWeb;
		long webSince;
		boolean botBurning;
		boolean hadTotem;
		long poppedAt = -1;
		// Blasts are counted once a tick.
		long lastBlastAt = -1;
		final Set<FishingHook> hooks = new HashSet<>();
		final Set<FishingHook> hooked = new HashSet<>();
		final Set<AbstractArrow> arrows = new HashSet<>();
		int botDownTicks;
		/** The action bar shows the last verdict until then, then the cue and progress again. */
		long verdictUntil = -1;
		/** The last urgent cue shown in the middle of the screen, and when. */
		String lastUrgent = "";
		long urgentAt = Long.MIN_VALUE / 2;
		/** The live coach's last tip (fights only), and when. */
		long lastTipAt = Long.MIN_VALUE / 2;

		Session(UUID player, Drill drill, int bay) {
			this.player = player;
			this.drill = drill;
			this.bay = bay;
			this.botName = "Drill" + (bay + 1);
		}

		/** Watching a fight against the named bot. */
		Session(UUID player, String botName) {
			this.player = player;
			this.drill = null;
			this.bay = -1;
			this.botName = botName;
			this.phase = Phase.RUN;
		}

		Drill.Stage current() {
			return drill.stages().get(stage);
		}

		boolean is(Drill.Rep rep) {
			return drill != null && phase == Phase.RUN && drill.rep() == rep;
		}
	}

	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<LivingEntity, Float> healthBefore = new IdentityHashMap<>();
	private DrillMedals medals;
	private final LiveCoach coach = new LiveCoach();

	public void load(Path configDir) {
		medals = new DrillMedals(configDir.resolve("drill-medals.json"));
		reports = new FightReports(configDir.resolve("fight-reports.json"));
	}

	public void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && !(player instanceof BotPlayer)) {
				onAttack(sp, entity);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (involved(entity)) {
				healthBefore.put(entity, entity.getHealth() + entity.getAbsorptionAmount());
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Float before = healthBefore.remove(entity);
			if (before != null) {
				onDamage(entity, source, Math.max(0, before - Math.max(0, entity.getHealth()) - entity.getAbsorptionAmount()));
			}
		});
		// Nobody dies in a drill: what would have killed you heals you instead.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && !(p instanceof BotPlayer) && inDrill(p.getUUID())) {
				Float before = healthBefore.remove(entity);
				if (before != null) {
					onDamage(entity, source, before);
				}
				revive(p, true);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Float before = healthBefore.remove(entity);
			if (before != null) {
				onDamage(entity, source, before);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> onSpawn(entity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (!(handler.player instanceof BotPlayer) && inDrill(handler.player.getUUID())) {
				stop(handler.player, "you left the game");
			}
		});
	}

	public DrillMedals medals() {
		return medals;
	}

	public boolean inDrill(UUID player) {
		Session s = sessions.get(player);
		return s != null && s.drill != null;
	}

	/** Full health, no fire, nothing still falling: a drill can't kill you. */
	private static void revive(ServerPlayer p, boolean wouldHaveDied) {
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		p.resetFallDistance();
		p.getFoodData().setFoodLevel(20);
		if (wouldHaveDied) {
			p.sendSystemMessage(Component.literal("That would have killed you: healed (you can't die in a drill).").withStyle(ChatFormatting.YELLOW));
		}
	}

	// --- Watching fights ---

	/** Starts watching a player's fight against a bot, for the live coach and the report at the end. */
	public void watchMatch(ServerPlayer player, Bot bot) {
		if (!inDrill(player.getUUID())) {
			Session s = new Session(player.getUUID(), bot.name());
			s.lastJumps = jumps(player);
			s.hadTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
			s.shieldCooling = shieldCooling(player);
			sessions.put(player.getUUID(), s);
		}
	}

	/** The match is over: the player gets their report and coach lines, kept for the You page. */
	public void endMatch(MinecraftServer server, UUID player, String mode, String opponent, String result) {
		Session s = sessions.get(player);
		if (s == null || s.drill != null) {
			return;
		}
		sessions.remove(player);
		if (s.inWeb) {
			s.tally.webEscapes++;
			s.tally.webTicks += (int) (server.overworld().getGameTime() - s.webSince);
		}
		io.github.flick256.sparbot.core.drill.FightReport report = io.github.flick256.sparbot.core.drill.FightReport.of(mode, opponent, result,
			System.currentTimeMillis(), s.tally);
		reports.add(player, report);
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p == null) {
			return;
		}
		StringBuilder numbers = new StringBuilder();
		report.numbers().forEach((k, v) -> numbers.append(numbers.length() == 0 ? "" : ", ").append(k).append(" ").append(v));
		p.sendSystemMessage(Component.literal("Your fight (" + mode + " against " + opponent + ", " + result + "): ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(numbers.toString()).withStyle(ChatFormatting.WHITE)));
		for (String line : report.coach()) {
			p.sendSystemMessage(Component.literal("Coach: " + line).withStyle(ChatFormatting.AQUA));
		}
	}

	private FightReports reports;

	public FightReports reports() {
		return reports;
	}

	// --- Starting and stopping ---

	/** Starts a drill for the player in a free bay of the training hall. */
	public String start(ServerPlayer player, Drill drill) throws IOException {
		MinecraftServer server = player.level().getServer();
		if (inDrill(player.getUUID())) {
			throw new IllegalArgumentException("You are already in a drill: /sparbot drill stop first");
		}
		if (SparBot.matches().inMatch(player.getUUID())) {
			throw new IllegalArgumentException("You are in a match: stop it first");
		}
		Kit botKit = kit(drill.botKit());
		kit(drill.kit());
		ServerLevel level = SparBot.practice().ensure(server);
		int bay = -1;
		for (int i = 0; i < PracticeLayout.BAYS && bay < 0; i++) {
			int each = i;
			if (sessions.values().stream().noneMatch(s -> s.drill != null && s.bay == each)) {
				bay = i;
			}
		}
		if (bay < 0) {
			throw new IllegalArgumentException("All " + PracticeLayout.BAYS + " bays are in use; try again in a minute");
		}
		if (SparBot.matches().backups().has(player.getUUID())) {
			throw new IllegalArgumentException("You still have an unrestored match backup; rejoin first");
		}
		SparBot.matches().backups().save(player);
		Session s = new Session(player.getUUID(), drill, bay);
		sessions.put(player.getUUID(), s);
		SparBot.bots().get(s.botName).ifPresent(old -> SparBot.bots().remove(old, "replaced by a new drill"));
		Vec3 botAt = botSpot(s, s.current());
		Bot bot = SparBot.bots().spawn(server, s.botName, level, botAt, 0, profile(s.current().profile()), botKit);
		bot.setAssignedTarget(player.getUUID());
		player.sendSystemMessage(Component.literal(drill.name() + ": " + drill.skill() + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("\n" + drill.how()).withStyle(ChatFormatting.GRAY)));
		for (String tip : drill.tips()) {
			player.sendSystemMessage(Component.literal(" - " + tip).withStyle(ChatFormatting.WHITE));
		}
		setUp(server, level, s, player);
		return drill.name() + " in bay " + (bay + 1);
	}

	/** Ends the player's drill early (the medal stays at the stages already passed). */
	public boolean stop(ServerPlayer player, String why) {
		Session s = sessions.get(player.getUUID());
		if (s == null || s.drill == null) {
			return false;
		}
		player.sendSystemMessage(Component.literal(s.drill.name() + " stopped: " + why + ".").withStyle(ChatFormatting.YELLOW));
		finish(player.level().getServer(), s, s.passed > 0);
		return true;
	}

	/** Every drill ends (server stopping): inventories back, bots gone. */
	public void stopAll(MinecraftServer server) {
		for (Session s : new ArrayList<>(sessions.values())) {
			if (s.drill != null) {
				finish(server, s, false);
			}
		}
	}

	private static Kit kit(String id) {
		return SparBot.kits().get(id).orElseThrow(() -> new IllegalStateException("The drill's kit " + id + " isn't loaded"));
	}

	private static SkillProfile profile(String id) {
		return SparBot.profiles().get(id).orElseThrow(() -> new IllegalStateException("No profile " + id));
	}

	private static Vec3 playerSpot(Session s, Drill.Stage stage) {
		int[] b = PracticeLayout.bay(s.bay);
		return new Vec3(b[0] + 0.5, PracticeLayout.FLOOR, b[1] + 0.5 + Math.round(stage.distance() / 2));
	}

	private static Vec3 botSpot(Session s, Drill.Stage stage) {
		int[] b = PracticeLayout.bay(s.bay);
		return new Vec3(b[0] + 0.5, PracticeLayout.FLOOR, b[1] + 0.5 - (stage.distance() - Math.round(stage.distance() / 2)));
	}

	/** Puts the bay back, both of you on your spots with fresh kits, and the bot set up for the stage. */
	private void setUp(MinecraftServer server, ServerLevel level, Session s, ServerPlayer player) {
		Drill.Stage stage = s.current();
		TrainingHall.resetBay(level, s.bay);
		for (Entity e : level.getEntities((Entity) null, TrainingHall.bayBox(s.bay), DrillManager::loose)) {
			e.discard();
		}
		Vec3 me = playerSpot(s, stage);
		player.teleportTo(level, me.x, me.y, me.z, Set.of(), 180, 0, true);
		player.setDeltaMovement(Vec3.ZERO);
		KitApplier.applyForPlayer(player, kit(s.drill.kit()));
		revive(player, false);
		Bot bot = SparBot.bots().get(s.botName).orElse(null);
		if (bot != null) {
			bot.setHome(level.dimension(), botSpot(s, stage), 0);
			bot.setProfile(profile(stage.profile()));
			bot.setPlaystyle(new Playstyle("drill", "Drill", "A drill's bot: only the tactics its stage trains.", stage.tactics(), Map.of(), 0));
			EnumSet<Technique> off = EnumSet.noneOf(Technique.class);
			for (String id : stage.techniquesOff()) {
				Technique.byId(id).ifPresent(off::add);
			}
			bot.setDisabledTechniques(off);
			UUID target = player.getUUID();
			java.util.function.Supplier<@Nullable ServerPlayer> who = () -> server.getPlayerList().getPlayer(target);
			long seed = level.getGameTime() ^ target.getLeastSignificantBits();
			Object pattern = switch (stage.pattern()) {
				case STILL -> DrillPatterns.still(who, botSpot(s, stage));
				case PACE -> DrillPatterns.pace(who, stage.distance(), false);
				case SHIELD -> DrillPatterns.pace(who, stage.distance(), true);
				case ATTACKER -> new DrillPatterns.Attacker(who, ATTACK_RHYTHM, !stage.cues(), seed);
				case WHIFFER -> new DrillPatterns.Whiffer(who);
				case STRAFER -> DrillPatterns.strafer(who, 2.8, seed);
				case RUSHER -> new DrillPatterns.Rusher(who);
				case FIGHT -> null;
			};
			s.pattern = pattern;
			@SuppressWarnings("unchecked")
			java.util.function.Function<BotPlayer, io.github.flick256.sparbot.core.act.Inputs> moves =
				(java.util.function.Function<BotPlayer, io.github.flick256.sparbot.core.act.Inputs>) pattern;
			bot.setPattern(moves);
			bot.setAssignedTarget(target);
			BotPlayer body = bot.body();
			if (body != null && body.isAlive()) {
				BotManager.prepareForRound(bot, body);
			}
			bot.setBrainPaused(true);
		}
		s.phase = Phase.COUNTDOWN;
		s.phaseTicks = 0;
		player.sendSystemMessage(Component.literal("Stage " + (s.stage + 1) + " of " + s.drill.stages().size() + ": " + stage.label()
			+ (s.attempt > 0 ? " (try " + (s.attempt + 1) + ")" : "") + ". " + stage.reps() + " good " + s.drill.rep().label() + " to pass"
			+ (stage.cues() ? ": follow the cues on screen." : ".")).withStyle(ChatFormatting.AQUA));
	}

	private static boolean loose(Entity e) {
		return e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile
			|| e instanceof net.minecraft.world.entity.ExperienceOrb || e instanceof net.minecraft.world.entity.item.PrimedTnt || e instanceof EndCrystal
			|| e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart || e instanceof net.minecraft.world.entity.item.FallingBlockEntity;
	}

	/** The drill is over: the medal (if a stage was passed), the bot gone, your inventory back, and you by the walkway. */
	private void finish(MinecraftServer server, Session s, boolean award) {
		sessions.remove(s.player);
		SparBot.bots().get(s.botName).ifPresent(b -> SparBot.bots().remove(b, "drill over"));
		ServerPlayer player = server.getPlayerList().getPlayer(s.player);
		ServerLevel level = PracticeWorld.level(server);
		if (level != null) {
			TrainingHall.resetBay(level, s.bay);
			for (Entity e : level.getEntities((Entity) null, TrainingHall.bayBox(s.bay), DrillManager::loose)) {
				e.discard();
			}
		}
		if (player == null) {
			return; // their inventory comes back when they next join
		}
		if (award) {
			String medal = Drill.medal(s.passed);
			boolean best = medals.record(s.player, s.drill.id(), s.passed);
			int color = s.passed == 3 ? 0xFFD700 : s.passed == 2 ? 0xC0C0C0 : s.passed == 1 ? 0xCD7F32 : 0xAAAAAA;
			player.sendSystemMessage(Component.literal(s.drill.name() + ": " + (s.passed == 0 ? "no medal yet" : medal + " medal") + " ("
				+ s.passed + " of " + s.drill.stages().size() + " stages)" + (best ? ", your best!" : "."))
				.withStyle(st -> st.withColor(color).withBold(true)));
		}
		if (player.isAlive()) {
			SparBot.matches().backups().restore(player);
			if (level != null) {
				BlockPos at = TrainingHall.arrival();
				player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 180, 0, true);
			}
		} else {
			pendingRestore.add(s.player);
		}
	}

	private final Set<UUID> pendingRestore = new HashSet<>();

	// --- Ticking ---

	public void tick(MinecraftServer server) {
		healthBefore.clear();
		pendingRestore.removeIf(uuid -> {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			return p == null || p.isAlive() && SparBot.matches().backups().restore(p);
		});
		watchFights(server);
		for (Session s : new ArrayList<>(sessions.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(s.player);
			if (s.drill == null) {
				// A fight being watched: what the game can't report by event, and the live coach.
				BotPlayer body = SparBot.bots().get(s.botName).map(Bot::body).orElse(null);
				if (player == null || body == null) {
					continue;
				}
				if (body.isAlive() && player.isAlive() && player.level() == body.level()) {
					measure((ServerLevel) player.level(), s, player, body);
					coach.tick(s.tally, player, body, (ServerLevel) player.level(), this::tipAllowed, s);
				}
				continue;
			}
			if (player == null) {
				finish(server, s, false);
				continue;
			}
			ServerLevel level = PracticeWorld.level(server);
			if (level == null || player.level() != level) {
				stop(player, "you left the practice world");
				continue;
			}
			int[] b = PracticeLayout.bay(s.bay);
			if (Math.abs(player.getX() - b[0]) > LEASH || Math.abs(player.getZ() - b[1]) > LEASH) {
				stop(player, "you left the bay");
				continue;
			}
			if (player.getHealth() < TOP_UP_BELOW && player.isAlive()) {
				revive(player, false);
				player.sendOverlayMessage(Component.literal("Healed up: you can't die in a drill").withStyle(ChatFormatting.YELLOW));
			}
			step(server, level, s, player);
		}
	}

	/**
	 * Fights against bots that aren't matches (the menu's Fight, /sparbot fight): watched like a match, so
	 * the live coach speaks there too.
	 */
	private void watchFights(MinecraftServer server) {
		if (!SparBot.config().liveCoach) {
			return;
		}
		for (Bot bot : SparBot.bots().all()) {
			UUID target = bot.assignedTarget();
			if (target == null || sessions.containsKey(target) || bot.name().startsWith("Drill")) {
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(target);
			if (p != null && !(p instanceof BotPlayer)) {
				Session s = new Session(target, bot.name());
				s.lastJumps = jumps(p);
				s.hadTotem = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
				s.shieldCooling = shieldCooling(p);
				sessions.put(target, s);
			}
		}
		// A fight that isn't a match ends when the bot stops targeting them.
		sessions.values().removeIf(s -> s.drill == null && !SparBot.matches().inMatch(s.player) && SparBot.bots().get(s.botName)
			.map(b -> !s.player.equals(b.assignedTarget())).orElse(true));
	}

	private boolean tipAllowed(Object session, long now) {
		Session s = (Session) session;
		if (!SparBot.config().liveCoach || now - s.lastTipAt < LiveCoach.TIP_GAP) {
			return false;
		}
		s.lastTipAt = now;
		return true;
	}

	private void step(MinecraftServer server, ServerLevel level, Session s, ServerPlayer player) {
		s.phaseTicks++;
		Bot bot = SparBot.bots().get(s.botName).orElse(null);
		BotPlayer body = bot == null ? null : bot.body();
		if (bot == null) {
			stop(player, "its bot is gone");
			return;
		}
		// A bot that died gets up again on its spot (the drill goes on).
		if (body == null || body.isDeadOrDying()) {
			if (++s.botDownTicks >= 20) {
				s.botDownTicks = 0;
				SparBot.bots().respawn(server, bot);
			}
			return;
		}
		switch (s.phase) {
			case COUNTDOWN -> {
				int left = (COUNTDOWN - s.phaseTicks + 19) / 20;
				if (s.phaseTicks % 20 == 1) {
					player.sendOverlayMessage(Component.literal(left > 0 ? Integer.toString(left) : "Go!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
				if (s.phaseTicks >= COUNTDOWN) {
					s.phase = Phase.RUN;
					s.phaseTicks = 0;
					s.tally = new DrillTally();
					s.lastJumps = jumps(player);
					s.hadTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
					s.shieldCooling = shieldCooling(player);
					s.inWeb = false;
					s.poppedAt = -1;
					s.hitTakenAt = -1;
					s.stunAt = -1;
					s.comboHits = 0;
					s.whiffAt = -1;
					s.botSwingAt = -1;
					s.exchangeOpen = false;
					s.resetAt = -1;
					s.botBurning = body.isOnFire();
					s.verdictUntil = -1;
					s.hooks.clear();
					s.hooked.clear();
					s.arrows.clear();
					bot.resetMindForRound();
					bot.setBrainPaused(false);
					if (s.pattern instanceof DrillPatterns.Rusher r) {
						r.restart(level.getGameTime(), 30);
					}
					player.sendOverlayMessage(Component.literal("Go!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
			}
			case RUN -> {
				measure(level, s, player, body);
				if (s.phase != Phase.RUN) {
					return; // the stage was passed this tick
				}
				long now = level.getGameTime();
				if (now >= s.verdictUntil && s.phaseTicks % 4 == 0) {
					hud(s, player, body, now);
				}
				if (s.phaseTicks >= s.current().seconds() * 20) {
					endStage(server, level, s, player, bot, false);
				}
			}
			case BREAK -> {
				if (s.phaseTicks >= BREAK) {
					setUp(server, level, s, player);
				}
			}
		}
	}

	/** The action bar between verdicts: the cue (in a stage with cues) and how the stage is going. */
	private void hud(Session s, ServerPlayer player, BotPlayer body, long now) {
		Drill.Stage stage = s.current();
		int left = Math.max(0, (stage.seconds() * 20 - s.phaseTicks) / 20);
		Component progress = Component.literal(s.tally.goodReps + "/" + stage.reps() + " " + s.drill.rep().label() + "  " + left + " s")
			.withStyle(ChatFormatting.WHITE);
		String cue = stage.cues() ? Cues.cue(s.drill.rep(), player, body, s.pattern, now, s.whiffAt, s.stunAt) : null;
		player.sendOverlayMessage(cue == null || cue.isEmpty() ? progress
			: Component.literal(cue + "   ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD).append(progress));
		// The moment to act goes up in the middle of the screen too, big, where the eyes are.
		if (cue != null && Cues.urgent(cue) && (!cue.equals(s.lastUrgent) || now - s.urgentAt > 10)) {
			s.lastUrgent = cue;
			s.urgentAt = now;
			title(player, Component.literal(cue).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), Component.empty(), 8);
		} else if (cue == null || !Cues.urgent(cue)) {
			s.lastUrgent = "";
		}
	}

	/** One judged attempt: counted, told on the action bar with a sound, and the stage passed when enough are in. */
	private void rep(Session s, ServerPlayer player, Feedback.Verdict verdict) {
		if (s.drill == null) {
			return;
		}
		s.tally.rep(verdict, s.drill.rep().strict());
		long now = player.level().getGameTime();
		s.verdictUntil = now + 30;
		Drill.Stage stage = s.current();
		player.sendOverlayMessage(Component.literal((verdict.good() ? "✔ " : "✘ ") + verdict.text()).withStyle(verdict.good() ? ChatFormatting.GREEN
			: ChatFormatting.RED).append(Component.literal("   " + s.tally.goodReps + "/" + stage.reps()).withStyle(ChatFormatting.WHITE)));
		sound(player, verdict.good() ? SoundEvents.NOTE_BLOCK_PLING : SoundEvents.NOTE_BLOCK_BASS, verdict.good() ? 1.6F : 0.6F);
		title(player, Component.empty(), Component.literal((verdict.good() ? "\u2714 " : "\u2718 ") + verdict.text())
			.withStyle(verdict.good() ? ChatFormatting.GREEN : ChatFormatting.RED), 30);
		if (s.tally.goodReps >= stage.reps()) {
			Bot bot = SparBot.bots().get(s.botName).orElse(null);
			if (bot != null) {
				endStage(player.level().getServer(), (ServerLevel) player.level(), s, player, bot, true);
			}
		}
	}

	/** A title (and subtitle) in the middle of the screen, up at once, for {@code stay} ticks. */
	private static void title(ServerPlayer player, Component title, Component subtitle, int stay) {
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, stay, 6));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
	}

	private static void sound(ServerPlayer player, Holder<SoundEvent> sound, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(), 0.7F, pitch,
			player.getRandom().nextLong()));
	}

	private void endStage(MinecraftServer server, ServerLevel level, Session s, ServerPlayer player, Bot bot, boolean passed) {
		Drill.Stage stage = s.current();
		bot.setBrainPaused(true);
		String mistake = s.tally.commonestMistake();
		if (passed) {
			s.passed++;
			s.attempt = 0;
			sound(player, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), 1.0F);
			player.sendSystemMessage(Component.literal("Stage " + (s.stage + 1) + " (" + stage.label() + ") passed: " + s.tally.goodReps + " good of "
				+ s.tally.reps + " tries.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			if (mistake != null) {
				player.sendSystemMessage(Component.literal("Still to work on: " + mistake).withStyle(ChatFormatting.GRAY));
			}
			if (s.stage + 1 >= s.drill.stages().size()) {
				finish(server, s, true);
				return;
			}
			s.stage++;
		} else {
			s.attempt++;
			player.sendSystemMessage(Component.literal("Time: " + s.tally.goodReps + " of " + stage.reps() + " good " + s.drill.rep().label()
				+ ". Again!").withStyle(ChatFormatting.GOLD));
			if (mistake != null) {
				player.sendSystemMessage(Component.literal("What went wrong most: " + mistake).withStyle(ChatFormatting.RED));
			}
			List<String> tips = s.drill.tips();
			player.sendSystemMessage(Component.literal("Tip: " + tips.get(s.attempt % tips.size())).withStyle(ChatFormatting.AQUA));
		}
		s.phase = Phase.BREAK;
		s.phaseTicks = 0;
	}

	/** One tick of what the game can see without an event: jumps, combos, the bot's swings, webs, fire, totems, shields, arrows, hooks. */
	private void measure(ServerLevel level, Session s, ServerPlayer player, BotPlayer body) {
		DrillTally t = s.tally;
		long now = level.getGameTime();
		t.ticks++;
		// Jumps, and the jump reset a hit taken on the ground was waiting for.
		int jumps = jumps(player);
		if (jumps > s.lastJumps) {
			s.lastJumpAt = now;
			if (s.hitTakenAt >= 0 && now - s.hitTakenAt <= JUMP_RESET_AFTER) {
				int offset = (int) (now - s.hitTakenAt);
				if (offset <= 2) {
					t.jumpResets++;
				}
				if (s.is(Drill.Rep.JUMP_RESET)) {
					rep(s, player, Feedback.jumpReset(offset));
				} else if (s.drill == null) {
					coach.jumpResetJudged(offset, player, s, this::tipAllowed);
				}
				s.hitTakenAt = -1;
			}
		}
		s.lastJumps = jumps;
		if (s.hitTakenAt >= 0 && now - s.hitTakenAt > JUMP_RESET_AFTER) {
			if (s.is(Drill.Rep.JUMP_RESET)) {
				rep(s, player, Feedback.jumpReset(null));
			} else if (s.drill == null) {
				coach.jumpResetJudged(null, player, s, this::tipAllowed);
			}
			s.hitTakenAt = -1;
		}
		// A combo that petered out.
		if (s.comboHits > 0 && now - s.lastOwnHitAt > COMBO_GAP) {
			endCombo(s, player);
		}
		// The bot's swings: one that found nobody is a miss to punish.
		boolean swinging = body.swinging;
		if (swinging && (!s.botWasSwinging || body.swingTime < s.botLastSwingTime)) {
			s.botSwingAt = now;
		}
		s.botWasSwinging = swinging;
		s.botLastSwingTime = body.swingTime;
		if (s.botSwingAt >= 0 && now - s.botSwingAt == 3) {
			boolean found = s.lastHurtByBotAt >= s.botSwingAt;
			if (!found && body.distanceTo(player) < 5.5 && (s.drill == null || s.is(Drill.Rep.PUNISH))) {
				s.whiffAt = s.botSwingAt;
				if (s.drill == null) {
					coach.whiffed(player, s, this::tipAllowed);
				}
			} else if (found && s.is(Drill.Rep.PUNISH) && s.pattern instanceof DrillPatterns.Whiffer) {
				rep(s, player, new Feedback.Verdict(false, "You walked into its swing: wait outside its reach until it has swung", "early"));
			}
		}
		if (s.whiffAt >= 0 && now - s.whiffAt > PUNISH_LIMIT) {
			if (s.is(Drill.Rep.PUNISH)) {
				rep(s, player, Feedback.punish(null, (int) s.current().target()));
			}
			s.whiffAt = -1;
		}
		// First hits: an exchange opens with the two of you apart (or the rusher coming), and resets after.
		if (s.is(Drill.Rep.FIRST_HIT)) {
			if (s.pattern instanceof DrillPatterns.Rusher r) {
				if (s.resetAt >= 0 && now >= s.resetAt) {
					s.resetAt = -1;
					Drill.Stage stage = s.current();
					Vec3 me = playerSpot(s, stage);
					Vec3 it = botSpot(s, stage);
					player.teleportTo(level, me.x, me.y, me.z, Set.of(), 180, 0, true);
					body.teleportTo(level, it.x, it.y, it.z, Set.of(), 0, 0, true);
					r.restart(now, 25);
				} else if (s.resetAt < 0 && r.rushing(now)) {
					s.exchangeOpen = true;
				}
			} else if (!s.exchangeOpen && body.distanceTo(player) > 5.0) {
				s.exchangeOpen = true;
			}
		}
		// Webs.
		BlockPos feet = player.blockPosition();
		boolean web = level.getBlockState(feet).is(Blocks.COBWEB) || level.getBlockState(feet.above()).is(Blocks.COBWEB);
		if (web && !s.inWeb) {
			s.webSince = now;
		} else if (!web && s.inWeb) {
			t.webEscapes++;
			t.webTicks += (int) (now - s.webSince);
			if (s.is(Drill.Rep.WEB)) {
				rep(s, player, Feedback.web((now - s.webSince) / 20.0, s.current().target()));
			}
		}
		s.inWeb = web;
		// The bot set alight.
		boolean burning = body.isOnFire();
		if (burning) {
			t.botBurningTicks++;
			if (!s.botBurning && s.is(Drill.Rep.LAVA)) {
				rep(s, player, Feedback.landed("Set alight!"));
			}
		}
		s.botBurning = burning;
		// Totems.
		boolean totem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		if (s.hadTotem && !totem) {
			s.poppedAt = now;
		} else if (!s.hadTotem && totem && s.poppedAt >= 0) {
			t.retotems++;
			t.retotemTicks += (int) (now - s.poppedAt);
			if (s.is(Drill.Rep.RETOTEM)) {
				rep(s, player, Feedback.retotem((now - s.poppedAt) / 20.0, s.current().target()));
			}
			s.poppedAt = -1;
		}
		s.hadTotem = totem;
		// Your shield, disabled.
		boolean cooling = shieldCooling(player);
		if (cooling && !s.shieldCooling) {
			t.shieldsLost++;
			if (s.is(Drill.Rep.SHIELD_DEFENCE)) {
				rep(s, player, Feedback.shieldDefence(true));
				t.goodReps = Math.max(0, t.goodReps - 2); // a lost shield costs three blocks
			} else if (s.drill == null) {
				coach.say(player, s, this::tipAllowed, "Your shield was disabled: drop it when they swap to an axe");
			}
		}
		s.shieldCooling = cooling;
		// Their shield, disabled by your axe: the follow-up is due within a second.
		if (s.stunCheckAt >= 0 && now > s.stunCheckAt) {
			if (shieldCooling(body)) {
				t.stuns++;
				s.stunAt = now;
			}
			s.stunCheckAt = -1;
		}
		if (s.stunAt >= 0 && now - s.stunAt > STUN_FOLLOW_UP) {
			if (s.is(Drill.Rep.STUN)) {
				rep(s, player, Feedback.stun(null));
			}
			s.stunAt = -1;
		}
		// Hooks and arrows that missed.
		for (FishingHook hook : s.hooks) {
			if (!hook.isRemoved() && hook.getHookedIn() == body && s.hooked.add(hook)) {
				t.rodHooks++;
				if (s.is(Drill.Rep.ROD)) {
					rep(s, player, Feedback.rod(true));
				}
			}
		}
		for (FishingHook hook : s.hooks) {
			if (hook.isRemoved() && !s.hooked.contains(hook) && s.is(Drill.Rep.ROD)) {
				rep(s, player, Feedback.rod(false));
			}
		}
		s.hooks.removeIf(Entity::isRemoved);
		s.hooked.removeIf(Entity::isRemoved);
		for (AbstractArrow arrow : new ArrayList<>(s.arrows)) {
			boolean stuck = arrow.tickCount > 3 && arrow.getDeltaMovement().lengthSqr() < 1.0E-4;
			if (arrow.isRemoved() || stuck || arrow.tickCount > 200) {
				s.arrows.remove(arrow);
				if (s.is(Drill.Rep.ARROW)) {
					rep(s, player, Feedback.arrow(false));
				}
			}
		}
	}

	private void endCombo(Session s, ServerPlayer player) {
		if (s.is(Drill.Rep.COMBO)) {
			rep(s, player, Feedback.combo(s.comboHits, (int) s.current().target()));
		}
		s.comboHits = 0;
	}

	private static int jumps(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
	}

	private static boolean shieldCooling(net.minecraft.world.entity.player.Player p) {
		for (ItemStack stack : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
			if (stack.is(Items.SHIELD) && p.getCooldowns().isOnCooldown(stack)) {
				return true;
			}
		}
		return false;
	}

	// --- Events ---

	private @Nullable Session running(@Nullable Entity entity) {
		if (entity == null) {
			return null;
		}
		Session s = sessions.get(entity.getUUID());
		return s != null && s.phase == Phase.RUN ? s : null;
	}

	/** The session whose bot this is (running), or null. */
	private @Nullable Session ofBot(@Nullable Entity entity) {
		if (!(entity instanceof BotPlayer bot)) {
			return null;
		}
		for (Session s : sessions.values()) {
			if (s.phase == Phase.RUN && s.botName.equalsIgnoreCase(bot.bot().name())) {
				return s;
			}
		}
		return null;
	}

	private boolean involved(Entity entity) {
		return !sessions.isEmpty() && (sessions.containsKey(entity.getUUID()) || ofBot(entity) != null);
	}

	/** A player's click on an entity, before it lands: how it was made. */
	private void onAttack(ServerPlayer player, Entity target) {
		Session s = running(player);
		if (s == null || ofBot(target) != s) {
			return;
		}
		long now = player.level().getGameTime();
		s.attackTick = now;
		s.charge = player.getAttackStrengthScale(0.5F);
		s.fall = player.fallDistance;
		s.onGround = player.onGround();
		s.falling = player.fallDistance > 0;
		s.crit = s.charge > 0.9 && player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
			&& !player.isPassenger() && !player.isSprinting() && target instanceof LivingEntity;
		s.sprint = player.isSprinting();
		ItemStack hand = player.getMainHandItem();
		s.mace = hand.is(Items.MACE);
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
		s.reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (target instanceof LivingEntity living && living.isBlocking() && hand.is(ItemTags.AXES)) {
			s.stunCheckAt = now;
		}
	}

	/** A swing of the player's arm (a click): on the bot this tick, or a miss. */
	public void onSwing(ServerPlayer player) {
		Session s = running(player);
		if (s != null) {
			s.tally.swings++;
			if (s.is(Drill.Rep.AIM) && player.getMainHandItem().is(ItemTags.SWORDS)) {
				rep(s, player, Feedback.aim(s.attackTick == player.level().getGameTime()));
			}
		}
	}

	/** Damage a drill's (or fight's) player or bot took ({@code lost}: the health it really lost). */
	private void onDamage(LivingEntity victim, DamageSource source, float lost) {
		long now = victim.level().getGameTime();
		Session s = ofBot(victim);
		if (s != null) {
			ServerPlayer player = victim.level().getServer().getPlayerList().getPlayer(s.player);
			if (player == null) {
				return;
			}
			DrillTally t = s.tally;
			Entity by = source.getEntity();
			Entity direct = source.getDirectEntity();
			boolean mine = by != null && by.getUUID().equals(s.player);
			if (mine && direct == by && s.attackTick == now) {
				boolean landed = lost > 0;
				if (landed) {
					t.hit(s.charge, s.crit, s.sprint, s.reach);
					s.comboHits++;
					s.lastOwnHitAt = now;
				}
				if (s.drill == null) {
					if (landed) {
						coach.hitLanded(s.charge, s.reach, player, s, this::tipAllowed);
					}
				} else if (landed) {
					ownHit(s, player, now);
				}
				if (landed && s.mace && s.fall >= 1.5) {
					t.smashes++;
					if (s.is(Drill.Rep.SMASH)) {
						rep(s, player, Feedback.landed(String.format(Locale.ROOT, "Smash from %.1f blocks!", s.fall)));
					}
				}
				if (landed && s.stunAt >= 0 && now - s.stunAt <= STUN_FOLLOW_UP && now > s.stunAt) {
					t.stunFollowUps++;
					if (s.is(Drill.Rep.STUN)) {
						rep(s, player, Feedback.stun((int) (now - s.stunAt)));
					}
					s.stunAt = -1;
				}
			} else if (mine && direct instanceof AbstractArrow arrow && lost > 0) {
				t.arrowHits++;
				if (s.arrows.remove(arrow) && s.is(Drill.Rep.ARROW)) {
					rep(s, player, Feedback.arrow(true));
				}
			} else if (lost >= 1 && now != s.lastBlastAt) {
				if (mine && direct instanceof EndCrystal) {
					t.crystalBlasts++;
					s.lastBlastAt = now;
					if (s.is(Drill.Rep.CRYSTAL)) {
						rep(s, player, Feedback.landed(String.format(Locale.ROOT, "Crystal: %.1f hearts", lost / 2)));
					}
				} else if (direct instanceof MinecartTNT) {
					t.cartBlasts++;
					s.lastBlastAt = now;
					if (s.is(Drill.Rep.CART)) {
						rep(s, player, Feedback.landed(String.format(Locale.ROOT, "Cart: %.1f hearts", lost / 2)));
					}
				} else if (source.is(DamageTypes.BAD_RESPAWN_POINT)) {
					t.anchorBlasts++;
					s.lastBlastAt = now;
					if (s.is(Drill.Rep.ANCHOR)) {
						rep(s, player, Feedback.landed(String.format(Locale.ROOT, "Anchor: %.1f hearts", lost / 2)));
					}
				}
			}
			return;
		}
		s = running(victim);
		if (s != null && victim instanceof ServerPlayer player && lost > 0 && ofBot(source.getEntity()) == s
			&& source.getDirectEntity() == source.getEntity()) {
			s.lastHurtByBotAt = now;
			boolean jumpedJustBefore = now - s.lastJumpAt <= JUMP_RESET_BEFORE;
			s.tally.hitTaken(player.onGround() || jumpedJustBefore);
			if (jumpedJustBefore && now - s.lastJumpAt <= 2) {
				s.tally.jumpResets++;
			}
			if (jumpedJustBefore) {
				int offset = (int) (s.lastJumpAt - now);
				if (s.is(Drill.Rep.JUMP_RESET)) {
					rep(s, player, Feedback.jumpReset(offset));
				} else if (s.drill == null) {
					coach.jumpResetJudged(offset, player, s, this::tipAllowed);
				}
			} else if (player.onGround()) {
				s.hitTakenAt = now;
			}
			if (s.comboHits > 0) {
				endCombo(s, player);
			}
			if (s.is(Drill.Rep.FIRST_HIT) && s.exchangeOpen) {
				s.exchangeOpen = false;
				rep(s, player, Feedback.firstHit(false, 0));
				resetExchange(s, now);
			}
			if (s.is(Drill.Rep.TRADE)) {
				rep(s, player, Feedback.trade(false));
			}
			if (s.is(Drill.Rep.BLOCK)) {
				rep(s, player, Feedback.block(false));
			}
		}
	}

	/** Your melee hit landed on the drill's bot: whatever the drill judges hits by. */
	private void ownHit(Session s, ServerPlayer player, long now) {
		Drill.Stage stage = s.current();
		switch (s.drill.rep()) {
			case CHARGE -> rep(s, player, Feedback.charge(s.charge));
			case REACH -> rep(s, player, Feedback.reach(s.reach, stage.target()));
			case CRIT -> rep(s, player, Feedback.crit(s.crit, s.charge, s.onGround, s.falling, s.sprint));
			case SPRINT_HIT -> rep(s, player, Feedback.sprintHit(s.sprint, s.charge));
			case TRADE -> rep(s, player, Feedback.trade(true));
			case PUNISH -> {
				if (s.whiffAt >= 0) {
					rep(s, player, Feedback.punish((int) (now - s.whiffAt), (int) stage.target()));
					s.whiffAt = -1;
				}
			}
			case FIRST_HIT -> {
				if (s.exchangeOpen) {
					s.exchangeOpen = false;
					rep(s, player, Feedback.firstHit(true, s.reach));
					resetExchange(s, now);
				}
			}
			default -> {
			}
		}
	}

	/** After a decided exchange against the rusher, both of you go back to your spots. */
	private static void resetExchange(Session s, long now) {
		if (s.pattern instanceof DrillPatterns.Rusher) {
			s.resetAt = now + 25;
		}
	}

	/** A hit stopped by a raised shield. */
	public void onBlocked(LivingEntity defender, DamageSource source) {
		Session s = running(defender);
		if (s != null && ofBot(source.getEntity()) == s && defender instanceof ServerPlayer player) {
			s.tally.blocked++;
			s.tally.combo = 0;
			if (s.is(Drill.Rep.BLOCK)) {
				rep(s, player, Feedback.block(true));
			} else if (s.is(Drill.Rep.SHIELD_DEFENCE)) {
				rep(s, player, Feedback.shieldDefence(false));
			}
		}
	}

	/** A splash of instant health or harm reaching someone. */
	public void onInstantEffect(MobEffect effect, @Nullable Entity thrower, LivingEntity target, double intensity) {
		Session s = running(target);
		if (s != null && thrower == target && effect == MobEffects.INSTANT_HEALTH.value() && target instanceof ServerPlayer player) {
			s.tally.potIntensity += Math.min(1, intensity);
			if (s.is(Drill.Rep.POT)) {
				rep(s, player, Feedback.pot(Math.min(1, intensity)));
			}
		}
	}

	/** Arrows, pots and hooks a drill's player sends out. */
	private void onSpawn(Entity entity) {
		if (sessions.isEmpty()) {
			return;
		}
		if (entity instanceof AbstractArrow arrow) {
			Session s = running(arrow.getOwner());
			if (s != null) {
				s.tally.arrowsShot++;
				s.arrows.add(arrow);
			}
		} else if (entity instanceof ThrownSplashPotion pot) {
			Session s = running(pot.getOwner());
			if (s != null) {
				s.tally.pots++;
			}
		} else if (entity instanceof FishingHook hook) {
			Session s = running(hook.getPlayerOwner());
			if (s != null) {
				s.tally.rodCasts++;
				s.hooks.add(hook);
			}
		}
	}

	/** Each player's medals, for the menu. */
	public Map<String, Integer> medalsOf(UUID player) {
		return medals == null ? Map.of() : medals.of(player);
	}

	/** Whether the player's drill is in a scored round right now (for tests). */
	public boolean stageRunning(UUID player) {
		Session s = sessions.get(player);
		return s != null && s.drill != null && s.phase == Phase.RUN;
	}

	/** Stages the player has passed in the drill they are in, or -1 (for tests). */
	public int stagesPassed(UUID player) {
		Session s = sessions.get(player);
		return s == null ? -1 : s.passed;
	}

	/** Good reps so far in the current stage, or -1 (for tests). */
	public int goodReps(UUID player) {
		Session s = sessions.get(player);
		return s == null || s.drill == null ? -1 : s.tally.goodReps;
	}

	/** Whether the drill's bot just swung and missed and the punish is still open (for tests). */
	public boolean punishWindowOpen(UUID player) {
		Session s = sessions.get(player);
		return s != null && s.drill != null && s.phase == Phase.RUN && s.whiffAt >= 0;
	}

	/** The bay of the player's drill, or -1 (for tests). */
	public int bayOf(UUID player) {
		Session s = sessions.get(player);
		return s == null ? -1 : s.bay;
	}

	/** The drill a player is in, or null. */
	public @Nullable String drillOf(UUID player) {
		Session s = sessions.get(player);
		return s == null || s.drill == null ? null : s.drill.id();
	}

	static List<String> ids() {
		return io.github.flick256.sparbot.core.drill.Drills.ALL.stream().map(Drill::id).toList();
	}
}

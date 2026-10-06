package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotManager;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.drill.Drill;
import io.github.flick256.sparbot.core.drill.DrillTally;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Runs the skill drills in the training hall. A drill takes a free bay, keeps your inventory safe (as a
 * match does), and runs its three stages against a bot of its own: a countdown, a timed round in which the
 * one skill is measured from what the game sees you do (your hits and how they were made, the bot's hits on
 * you, blocks, pots, webs, blasts, totems), then the score against the stage's pass mark. A failed stage
 * ends the drill; the medal is the number of stages passed, and your best is kept.
 */
public final class DrillManager {
	private static final int COUNTDOWN = 60;
	private static final int BREAK = 60;
	/** A hit within this long of disabling the bot's shield follows the stun up. */
	private static final int STUN_FOLLOW_UP = 20;
	/** A jump this soon after (or just before) a hit lands on you resets it. */
	private static final int JUMP_RESET_AFTER = 4;
	private static final int JUMP_RESET_BEFORE = 2;
	/** Wandering this far from the bay ends the drill. */
	private static final double LEASH = 40;

	private enum Phase {
		COUNTDOWN,
		RUN,
		BREAK
	}

	private static final class Session {
		final UUID player;
		/** The drill, or null when this only watches a match against a bot (for the fight report). */
		final @Nullable Drill drill;
		final int bay;
		final String botName;
		int stage;
		Phase phase = Phase.COUNTDOWN;
		int phaseTicks;
		DrillTally tally = new DrillTally();
		int passed;
		// The melee click being resolved this tick.
		long attackTick = -1;
		double charge;
		boolean crit;
		boolean sprint;
		boolean mace;
		double reach;
		double fall;
		// Shields.
		long stunCheckAt = -1;
		long stunAt = -1;
		boolean shieldCooling;
		// Jump resets.
		long hitTakenAt = -1;
		int jumpsAtHit;
		int lastJumps;
		long lastJumpAt = Long.MIN_VALUE / 2;
		// Webs and totems.
		boolean inWeb;
		long webSince;
		boolean hadTotem;
		long poppedAt = -1;
		// Blasts are counted once a tick.
		long lastBlastAt = -1;
		final Set<FishingHook> hooks = new HashSet<>();
		final Set<FishingHook> hooked = new HashSet<>();
		int botDownTicks;

		Session(UUID player, Drill drill, int bay) {
			this.player = player;
			this.drill = drill;
			this.bay = bay;
			this.botName = "Drill" + (bay + 1);
		}

		/** Watching a match against the named bot. */
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
	}

	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<LivingEntity, Float> healthBefore = new IdentityHashMap<>();
	private DrillMedals medals;

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

	// --- Watching matches ---

	/** Starts measuring a player's match against a bot, for the report and coach lines at the end. */
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
		Kit kit = kit(drill.kit());
		Kit botKit = kit(drill.botKit());
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
		player.sendSystemMessage(Component.literal(drill.name() + ": " + drill.skill() + ".").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("\n" + drill.how()).withStyle(ChatFormatting.GRAY)));
		setUp(server, level, s, player);
		return drill.name() + " in bay " + (bay + 1);
	}

	/** Ends the player's drill early (no medal). */
	public boolean stop(ServerPlayer player, String why) {
		Session s = sessions.get(player.getUUID());
		if (s == null || s.drill == null) {
			return false;
		}
		player.sendSystemMessage(Component.literal(s.drill.name() + " stopped: " + why + ".").withStyle(ChatFormatting.YELLOW));
		finish(player.level().getServer(), s, false);
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
			bot.setPattern(switch (stage.pattern()) {
				case STILL -> DrillPatterns.still(() -> server.getPlayerList().getPlayer(target), botSpot(s, stage));
				case PACE -> DrillPatterns.pace(() -> server.getPlayerList().getPlayer(target), stage.distance(), false);
				case SHIELD -> DrillPatterns.pace(() -> server.getPlayerList().getPlayer(target), stage.distance(), true);
				case FIGHT -> null;
			});
			bot.setAssignedTarget(target);
			BotPlayer body = bot.body();
			if (body != null && body.isAlive()) {
				BotManager.prepareForRound(bot, body);
			}
			bot.setBrainPaused(true);
		}
		s.phase = Phase.COUNTDOWN;
		s.phaseTicks = 0;
		player.sendSystemMessage(Component.literal("Stage " + (s.stage + 1) + " of " + s.drill.stages().size() + ": " + stage.label() + ". "
			+ (s.drill.metric().lowerIsBetter() ? "Stay under " : "Reach ") + s.drill.metric().format(stage.pass()) + " " + s.drill.metric().label()
			+ " in " + s.drill.seconds() + " seconds.").withStyle(ChatFormatting.AQUA));
	}

	private static boolean loose(Entity e) {
		return e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.projectile.Projectile
			|| e instanceof net.minecraft.world.entity.ExperienceOrb || e instanceof net.minecraft.world.entity.item.PrimedTnt || e instanceof EndCrystal
			|| e instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart || e instanceof net.minecraft.world.entity.item.FallingBlockEntity;
	}

	/** The drill is over: the medal (if it ran to the end), the bot gone, your inventory back, and you by the walkway. */
	private void finish(MinecraftServer server, Session s, boolean completed) {
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
		if (completed) {
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
		for (Session s : new ArrayList<>(sessions.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(s.player);
			if (s.drill == null) {
				// A match being watched: only what the game can't report by event.
				BotPlayer body = SparBot.bots().get(s.botName).map(Bot::body).orElse(null);
				if (player != null && body != null && body.isAlive() && player.isAlive() && player.level() == body.level()) {
					measure((ServerLevel) player.level(), s, player, body);
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
			if (player.isDeadOrDying()) {
				player.sendSystemMessage(Component.literal("You died: that stage is failed.").withStyle(ChatFormatting.RED));
				finish(server, s, true);
				continue;
			}
			step(server, level, s, player);
		}
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
					s.hooks.clear();
					s.hooked.clear();
					bot.resetMindForRound();
					bot.setBrainPaused(false);
					player.sendOverlayMessage(Component.literal("Go!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
			}
			case RUN -> {
				measure(level, s, player, body);
				if (s.phaseTicks % 5 == 0) {
					Drill.Metric m = s.drill.metric();
					int left = Math.max(0, (s.drill.seconds() * 20 - s.phaseTicks) / 20);
					boolean passing = m.passes(s.tally, s.current().pass());
					player.sendOverlayMessage(Component.literal(m.label() + ": " + m.format(m.value(s.tally)) + "   pass " + m.format(s.current().pass())
						+ "   " + left + " s").withStyle(passing ? ChatFormatting.GREEN : ChatFormatting.WHITE));
				}
				if (s.phaseTicks >= s.drill.seconds() * 20) {
					endStage(server, level, s, player, bot);
				}
			}
			case BREAK -> {
				if (s.phaseTicks >= BREAK) {
					s.stage++;
					setUp(server, level, s, player);
				}
			}
		}
	}

	private void endStage(MinecraftServer server, ServerLevel level, Session s, ServerPlayer player, Bot bot) {
		if (s.inWeb) {
			s.tally.webEscapes++;
			s.tally.webTicks += (int) (level.getGameTime() - s.webSince);
		}
		Drill.Metric m = s.drill.metric();
		Drill.Stage stage = s.current();
		boolean passed = m.passes(s.tally, stage.pass());
		String few = m.samples(s.tally) < m.minSamples() ? " (too few tries to count: " + m.samples(s.tally) + " of " + m.minSamples() + ")" : "";
		player.sendSystemMessage(Component.literal("Stage " + (s.stage + 1) + " (" + stage.label() + "): " + m.format(m.value(s.tally)) + " " + m.label()
			+ few + ", " + (passed ? "passed" : "not passed") + " (" + (m.lowerIsBetter() ? "under " : "needed ") + m.format(stage.pass()) + ")")
			.withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
		bot.setBrainPaused(true);
		if (passed) {
			s.passed++;
		}
		if (!passed || s.stage + 1 >= s.drill.stages().size()) {
			finish(server, s, true);
			return;
		}
		s.phase = Phase.BREAK;
		s.phaseTicks = 0;
	}

	/** One tick of what the game can see without an event: jumps, webs, the bot burning, totems, your shield, hooks. */
	private void measure(ServerLevel level, Session s, ServerPlayer player, BotPlayer body) {
		DrillTally t = s.tally;
		long now = level.getGameTime();
		t.ticks++;
		int jumps = jumps(player);
		if (jumps > s.lastJumps) {
			s.lastJumpAt = now;
			if (s.hitTakenAt >= 0 && now - s.hitTakenAt <= JUMP_RESET_AFTER) {
				t.jumpResets++;
				s.hitTakenAt = -1;
			}
		}
		s.lastJumps = jumps;
		if (s.hitTakenAt >= 0 && now - s.hitTakenAt > JUMP_RESET_AFTER) {
			s.hitTakenAt = -1;
		}
		BlockPos feet = player.blockPosition();
		boolean web = level.getBlockState(feet).is(Blocks.COBWEB) || level.getBlockState(feet.above()).is(Blocks.COBWEB);
		if (web && !s.inWeb) {
			s.webSince = now;
		} else if (!web && s.inWeb) {
			t.webEscapes++;
			t.webTicks += (int) (now - s.webSince);
		}
		s.inWeb = web;
		if (body.isOnFire()) {
			t.botBurningTicks++;
		}
		boolean totem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		if (s.hadTotem && !totem) {
			s.poppedAt = now;
		} else if (!s.hadTotem && totem && s.poppedAt >= 0) {
			t.retotems++;
			t.retotemTicks += (int) (now - s.poppedAt);
			s.poppedAt = -1;
		}
		s.hadTotem = totem;
		boolean cooling = shieldCooling(player);
		if (cooling && !s.shieldCooling) {
			t.shieldsLost++;
		}
		s.shieldCooling = cooling;
		if (s.stunCheckAt >= 0 && now > s.stunCheckAt) {
			if (shieldCooling(body)) {
				t.stuns++;
				s.stunAt = now;
			}
			s.stunCheckAt = -1;
		}
		for (FishingHook hook : s.hooks) {
			if (!hook.isRemoved() && hook.getHookedIn() == body && s.hooked.add(hook)) {
				t.rodHooks++;
			}
		}
		s.hooks.removeIf(Entity::isRemoved);
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

	public void onSwing(ServerPlayer player) {
		Session s = running(player);
		if (s != null) {
			s.tally.swings++;
		}
	}

	/** Damage a drill's player or bot took ({@code lost}: the health it really lost). */
	private void onDamage(LivingEntity victim, DamageSource source, float lost) {
		long now = victim.level().getGameTime();
		Session s = ofBot(victim);
		if (s != null) {
			DrillTally t = s.tally;
			Entity by = source.getEntity();
			Entity direct = source.getDirectEntity();
			boolean mine = by != null && by.getUUID().equals(s.player);
			if (mine && direct == by && lost > 0 && s.attackTick == now) {
				t.hit(s.charge, s.crit, s.sprint, s.reach);
				if (s.mace && s.fall >= 1.5) {
					t.smashes++;
				}
				if (s.stunAt >= 0 && now - s.stunAt <= STUN_FOLLOW_UP && now > s.stunAt) {
					t.stunFollowUps++;
					s.stunAt = -1;
				}
			} else if (mine && direct instanceof AbstractArrow && lost > 0) {
				t.arrowHits++;
			} else if (lost >= 1 && now != s.lastBlastAt) {
				if (mine && direct instanceof EndCrystal) {
					t.crystalBlasts++;
					s.lastBlastAt = now;
				} else if (direct instanceof MinecartTNT) {
					t.cartBlasts++;
					s.lastBlastAt = now;
				} else if (source.is(DamageTypes.BAD_RESPAWN_POINT)) {
					t.anchorBlasts++;
					s.lastBlastAt = now;
				}
			}
			return;
		}
		s = running(victim);
		if (s != null && victim instanceof ServerPlayer player && lost > 0 && ofBot(source.getEntity()) == s
			&& source.getDirectEntity() == source.getEntity()) {
			boolean jumpedJustBefore = now - s.lastJumpAt <= JUMP_RESET_BEFORE;
			s.tally.hitTaken(player.onGround() || jumpedJustBefore);
			if (jumpedJustBefore) {
				s.tally.jumpResets++;
			} else if (player.onGround()) {
				s.hitTakenAt = now;
			}
		}
	}

	/** A hit stopped by a raised shield. */
	public void onBlocked(LivingEntity defender, DamageSource source) {
		Session s = running(defender);
		if (s != null && ofBot(source.getEntity()) == s) {
			s.tally.blocked++;
			s.tally.combo = 0;
		}
	}

	/** A splash of instant health or harm reaching someone. */
	public void onInstantEffect(MobEffect effect, @Nullable Entity thrower, LivingEntity target, double intensity) {
		Session s = running(target);
		if (s != null && thrower == target && effect == MobEffects.INSTANT_HEALTH.value()) {
			s.tally.potIntensity += Math.min(1, intensity);
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

	/** The drill a player is in, or null. */
	public @Nullable String drillOf(UUID player) {
		Session s = sessions.get(player);
		return s == null || s.drill == null ? null : s.drill.id();
	}

	static List<String> ids() {
		return io.github.flick256.sparbot.core.drill.Drills.ALL.stream().map(Drill::id).toList();
	}
}

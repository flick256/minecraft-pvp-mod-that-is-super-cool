package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.config.SparBotConfig;
import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.brain.DecisionTrace;
import io.github.flick256.sparbot.core.brain.DuelBrain;
import io.github.flick256.sparbot.core.brain.Policy;
import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Layout;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.stats.FightStats;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One sparring bot. Unlike its {@link BotPlayer} body (vanilla replaces the player object on every
 * respawn), this lives for as long as the bot exists and holds its brain, profile, kit and stats.
 */
public final class Bot {
	private final String name;
	private final UUID uuid;
	private final BotConnection connection;
	private final ClientEmulator client = new ClientEmulator();
	private final Perception perception = new Perception();
	private final FightStats stats = new FightStats();
	private final long seed;
	private SkillProfile profile;
	private Playstyle playstyle = Playstyle.BALANCED;
	/** Learned melee model in use, or null for the scripted melee. */
	private @Nullable String modelId;
	private io.github.flick256.sparbot.core.ml.@Nullable Mlp model;
	private Set<Technique> disabledTechniques =
		EnumSet.noneOf(Technique.class);
	private Kit kit;
	private @Nullable Layout layout;
	private Policy policy;
	private InputShaper shaper;
	private @Nullable BotPlayer body;
	private @Nullable UUID assignedTarget;
	private ResourceKey<Level> homeDimension;
	private Vec3 home;
	private float homeYaw;
	private int deadTicks;
	private @Nullable String lastTactic;
	private int attackTargetId = -1;
	private boolean attackWouldCrit;
	private boolean stabbing;
	private Inputs lastInputs = Inputs.IDLE;
	private @Nullable List<String> violations;
	private boolean brainPaused;
	private boolean inMatch;
	private final ConsumptionTracker consumption = new ConsumptionTracker();

	Bot(String name, UUID uuid, BotConnection connection, SkillProfile profile, Kit kit, ResourceKey<Level> dimension, Vec3 home, float homeYaw, long seed) {
		this.name = name;
		this.uuid = uuid;
		this.connection = connection;
		this.kit = kit;
		this.homeDimension = dimension;
		this.home = home;
		this.homeYaw = homeYaw;
		this.seed = seed;
		setProfile(profile);
	}

	/** Called by every new body, including the one vanilla creates on respawn. */
	void attachBody(BotPlayer player) {
		this.body = player;
	}

	/** Runs before the body's vanilla tick: perceive, decide, then press keys like a client would. */
	void beforeTick(BotPlayer player) {
		connection.drain();
		if (player.isDeadOrDying() || player.isRemoved()) {
			return;
		}
		// ClientPacketListener#handleMovePlayer: a client confirms each teleport (see BotConnection).
		int teleport = connection.takePendingTeleport();
		if (teleport >= 0 && player.connection != null) {
			player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleport));
		}
		// Knockback the "client" received since last tick (see BotConnection).
		net.minecraft.world.phys.Vec3 motion = connection.takePendingMotion();
		if (motion != null) {
			player.setDeltaMovement(motion);
		}

		consumption.update(player, stats);

		List<String> problems = MortalityGuard.violations(player);
		if (!problems.isEmpty()) {
			violations = problems;
			return;
		}

		if (brainPaused) {
			lastInputs = Inputs.IDLE;
			return;
		}
		SparBotConfig config = SparBot.config();
		Observation observation = perception.observe(this, player, player.level().getGameTime(), config.awarenessRadius,
			config.autoTarget, config.autoTargetBots);
		Inputs desired = policy.act(observation);
		Inputs actual = shaper.shape(desired);
		client.apply(player, this, actual);
		lastInputs = actual;

		String tactic = policy.lastTrace().tactic();
		if (config.logDecisions && !tactic.equals(lastTactic)) {
			SparBot.LOGGER.info("[{}] {} -> {} {}", name, lastTactic, tactic, policy.lastTrace().scores());
		}
		lastTactic = tactic;
	}

	/** The inputs the bot's brain pressed this tick, after the human limits (what a recording stores). */
	public Inputs lastInputs() {
		return lastInputs;
	}

	/** A paused bot keeps its body (physics, damage, guard) but its brain presses nothing. */
	public void setBrainPaused(boolean paused) {
		this.brainPaused = paused;
		if (paused && body != null) {
			client.apply(body, this, io.github.flick256.sparbot.core.act.Inputs.IDLE);
		}
	}

	/** While in a match, the match (not the respawn config) decides when the bot respawns. */
	public void setInMatch(boolean inMatch) {
		this.inMatch = inMatch;
	}

	public boolean inMatch() {
		return inMatch;
	}

	/** Start of a new round: forget the last round's fight (stats are kept). */
	public void resetMindForRound() {
		resetMind();
	}

	public boolean brainPaused() {
		return brainPaused;
	}

	public void setProfile(SkillProfile profile) {
		this.profile = profile;
		rebuildBrain();
	}

	/** Changes how the bot prefers to fight; its skill level stays the same. */
	public void setPlaystyle(Playstyle playstyle) {
		this.playstyle = playstyle;
		rebuildBrain();
	}

	public Playstyle playstyle() {
		return playstyle;
	}

	/** Switches techniques off for this bot (the rest on). Takes effect at once. */
	public void setDisabledTechniques(Set<Technique> techniques) {
		this.disabledTechniques = techniques.isEmpty() ? EnumSet.noneOf(Technique.class)
			: EnumSet.copyOf(techniques);
		if (policy instanceof DuelBrain brain) {
			brain.setDisabledTechniques(disabledTechniques);
		}
	}

	public Set<Technique> disabledTechniques() {
		return Collections.unmodifiableSet(disabledTechniques);
	}

	/** Fights in melee with a learned model ({@code null}: the scripted melee). */
	public void setModel(@Nullable String id, io.github.flick256.sparbot.core.ml.@Nullable Mlp net) {
		this.modelId = net == null ? null : id;
		this.model = net;
		rebuildBrain();
	}

	public @Nullable String modelId() {
		return modelId;
	}

	private void rebuildBrain() {
		DuelBrain brain = model == null ? new DuelBrain(profile, playstyle, seed, new io.github.flick256.sparbot.core.brain.EngageTactic())
			: DuelBrain.learned(profile, playstyle, seed, model);
		if (disabledTechniques != null) {
			brain.setDisabledTechniques(disabledTechniques);
		}
		this.policy = brain;
		// The shaper enforces limits from the effective (playstyle-biased) profile.
		this.shaper = new InputShaper(brain.profile(), new Rng(seed ^ 0x5EED), SparBot.config().maxCps);
	}

	/** Forget everything about the last fight (respawn, new round). Stats are kept. */
	void resetMind() {
		consumption.reset();
		policy.reset();
		shaper.reset();
		client.reset();
		perception.reset();
		lastTactic = null;
	}

	void beginAttack(int targetId, boolean wouldCrit) {
		attackTargetId = targetId;
		attackWouldCrit = wouldCrit;
	}

	void endAttack() {
		attackTargetId = -1;
		attackWouldCrit = false;
	}

	/** A spear jab hits everything along the look ray, so every entity it damages counts as a hit. */
	void beginStab() {
		stabbing = true;
	}

	void endStab() {
		stabbing = false;
	}

	/** While a spear charge is held, its hits land during the body's own tick. */
	void beginKineticTick(BotPlayer body) {
		stabbing = body.isUsingItem() && body.getUseItem().has(DataComponents.KINETIC_WEAPON);
	}

	void endKineticTick() {
		stabbing = false;
	}

	/** True while this bot's own attack (a click, a spear jab or a spear charge) is being resolved against {@code entityId}. */
	public boolean isAttacking(int entityId) {
		return stabbing || attackTargetId == entityId;
	}

	public boolean attackWouldCrit() {
		return attackWouldCrit;
	}

	public @Nullable List<String> takeViolations() {
		List<String> v = violations;
		violations = null;
		return v;
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	public @Nullable BotPlayer body() {
		return body;
	}

	BotConnection connection() {
		return connection;
	}

	ClientEmulator client() {
		return client;
	}

	public FightStats stats() {
		return stats;
	}

	public SkillProfile profile() {
		return profile;
	}

	public Kit kit() {
		return kit;
	}

	/** Call after an operator re-kits the bot so consumption stats do not count the swap as usage. */
	public void onKitReset() {
		consumption.reset();
	}

	public void setKit(Kit kit) {
		this.kit = kit;
		if (layout != null && !layout.kit().equals(kit.id())) {
			layout = null;
		}
	}

	public @Nullable Layout layout() {
		return layout;
	}

	public void setLayout(@Nullable Layout layout) {
		this.layout = layout;
	}

	/** The kit as this bot carries it: rearranged by its personal layout, if it has one. */
	public Kit effectiveKit() {
		return layout != null ? layout.arrange(kit) : kit;
	}

	public DecisionTrace trace() {
		return policy.lastTrace();
	}

	public @Nullable UUID assignedTarget() {
		return assignedTarget;
	}

	public void setAssignedTarget(@Nullable UUID target) {
		this.assignedTarget = target;
		perception.reset();
	}

	public ResourceKey<Level> homeDimension() {
		return homeDimension;
	}

	public Vec3 home() {
		return home;
	}

	public float homeYaw() {
		return homeYaw;
	}

	int incrementDeadTicks() {
		return ++deadTicks;
	}

	void clearDeadTicks() {
		deadTicks = 0;
	}
}

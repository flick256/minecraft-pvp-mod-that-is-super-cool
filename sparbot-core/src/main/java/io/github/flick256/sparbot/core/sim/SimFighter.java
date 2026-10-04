package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.EffectInfo;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One player in the simulator: vanilla 26.2 movement, melee and items, re-done without Minecraft so
 * thousands of fights a second can run. The numbers and the order of operations follow the decompiled
 * game code they are named after:
 * <ul>
 *   <li>keys to movement: LocalPlayer#modifyInput (x 0.98, x 0.2 while using an item, then stretched to
 *       the unit square); sprinting as ClientEmulator#updateSprint (not in shallow water)</li>
 *   <li>LivingEntity#travelInAir: accelerate by the movement speed (0.1, x 1.3 sprinting) on the ground
 *       or the flying speed (0.02, 0.026 sprinting) in the air, move, then gravity 0.08 and drag (0.98
 *       vertical; 0.6 x 0.91 on the ground, 0.91 in the air)</li>
 *   <li>#travelInWater (0.02 a tick, drag 0.8, 0.9 sprinting, sinking at gravity / 16), #travelInLava
 *       (drag 0.5, gravity / 4), #jumpInLiquid (+0.04), #jumpOutOfFluid (0.3 up at an edge), the current
 *       of flowing water (EntityFluidInteraction, 0.014)</li>
 *   <li>Entity#move and #collide: blocks stop a 0.6 x 1.8 box axis by axis (Y first), step-up of 0.6,
 *       velocity into a block is zeroed (#restituteMovementAfterCollisions); a cobweb multiplies the next
 *       move by (0.25, 0.05, 0.25) and stops all velocity (WebBlock, Entity#makeStuckInBlock)</li>
 *   <li>LivingEntity#jumpFromGround: 0.42 up, plus 0.2 along the facing when sprinting</li>
 *   <li>Player#attack: charge (20 / attack speed ticks), damage x (0.2 + 0.8 charge^2) plus Sharpness x
 *       charge, crits x 1.5 (charge above 0.9, falling, not on the ground, in water or sprinting), sprint
 *       knockback (+0.5 along the attacker's facing, then the attacker slows to 0.6 and stops sprinting);
 *       only an attack resets the charge (Player#onAttack); a click at nothing locks clicks for 10 ticks
 *       (Minecraft#startAttack's missTime)</li>
 *   <li>LivingEntity#hurtServer: 20 ticks of invulnerability, of which the last 10 let a bigger hit
 *       through for the difference (without knockback), default knockback 0.4 away from the attacker;
 *       armor (CombatRules) except for fire and falls, then Protection (4% a level), then absorption</li>
 *   <li>Lava: 4 damage (Entity#lavaHurt) and 15 s of fire; burning: 1 damage every 20 ticks; water puts
 *       fire out; falls: 1 damage a block beyond 3; shields don't stop any of these</li>
 *   <li>Knockback reaches a player one tick later (the server sends it, the client applies it); a hit
 *       blocked by a shield pushes nobody (it isn't marked hurt, so nothing is sent)</li>
 *   <li>Items: switching the held item resets the charge; a shield blocks hits from within 90 degrees of
 *       its holder's facing once it has been up 5 ticks (BlocksAttacks), and an axe hit on it disables it
 *       for 5 s; a golden apple takes 32 ticks and gives Absorption I (4) and Regeneration II (1 health
 *       every 25 ticks for 5 s); buckets (BucketItem#use: a filled one empties next to the block the
 *       crosshair is on, an empty one takes a source); blocks and cobwebs go next to the block the
 *       crosshair is on (BlockItem: not into a body, except a cobweb); a bow (BowItem, arrows as
 *       AbstractArrow); right click held repeats every 4 ticks (Minecraft#startUseItem)</li>
 * </ul>
 */
public final class SimFighter {
	static final double EYE_HEIGHT = 1.62;
	static final double HALF_WIDTH = 0.3;
	static final double HEIGHT = 1.8;
	static final double REACH = 3.0;
	static final double BLOCK_REACH = 4.5;
	private static final double WALK_SPEED = 0.1;
	private static final double SPRINT_FACTOR = 1.3;
	private static final double AIR_SPEED = 0.02;
	private static final double AIR_SPEED_SPRINTING = 0.026;
	private static final double GRAVITY = 0.08;
	private static final double GROUND_FRICTION = 0.6 * 0.91;
	private static final double AIR_FRICTION = 0.91;
	private static final double VERTICAL_DRAG = 0.98;
	private static final double JUMP_POWER = 0.42;
	private static final double SPRINT_JUMP_BOOST = 0.2;
	private static final double STEP_HEIGHT = 0.6;
	private static final double FLUID_JUMP_THRESHOLD = 0.4;
	private static final double DEFAULT_KNOCKBACK = 0.4;
	private static final double SPRINT_KNOCKBACK = 0.5;
	private static final int JUMP_DELAY = 10;
	static final float MAX_HEALTH = 20.0F;
	static final int SWORD_SLOT = 0;
	static final int AXE_SLOT = 1;
	static final int GAPPLE_SLOT = 2;
	private static final int EAT_TICKS = 32;
	private static final int SHIELD_DELAY_TICKS = 5;
	private static final int SHIELD_DISABLE_TICKS = 100;
	private static final double USE_SLOWDOWN = 0.2;
	private static final int REGEN_TICKS = 100;
	private static final int REGEN_INTERVAL = 25;
	private static final float GAPPLE_ABSORPTION = 4.0F;
	/** Minecraft#startUseItem: holding right click retries every 4 ticks. */
	private static final int RIGHT_CLICK_DELAY = 4;
	private static final int MISS_TIME = 10;
	private static final int LAVA_FIRE_TICKS = 300;
	private static final float LAVA_DAMAGE = 4.0F;
	private static final double WATER_CURRENT = 0.014;
	private static final double LAVA_CURRENT = 0.0023333333333333335;
	private static final double EPS = 1.0E-7;

	/** How damage was dealt: what armor, shields and knockback do with it. */
	enum Damage {
		MELEE(true, true, true),
		ARROW(true, true, true),
		LAVA(true, false, false),
		FIRE(false, false, false),
		FALL(false, false, false);

		final boolean armor;
		final boolean shieldable;
		final boolean knockback;

		Damage(boolean armor, boolean shieldable, boolean knockback) {
			this.armor = armor;
			this.shieldable = shieldable;
			this.knockback = knockback;
		}
	}

	private enum Use {
		NONE,
		EAT,
		SHIELD,
		BOW
	}

	final Loadout loadout;
	SimWorld world;
	/** Arrows shot this tick, collected by the simulation. */
	final List<SimArrow> shot = new ArrayList<>();
	double x;
	double y;
	double z;
	double vx;
	double vy;
	double vz;
	float yaw;
	float pitch;
	boolean onGround = true;
	boolean sprinting;
	boolean horizontalCollision;
	double fallDistance;
	int attackTicker = 100;
	float health = MAX_HEALTH;
	int invulnerableTime;
	int hurtTime;
	float lastHurt;
	int noJumpDelay;
	int missTime;
	/** Knockback the "client" receives at the start of its next tick. */
	Vec3 pendingMotion;
	/** Where it was at the end of the last tick (what an observer derives velocity from). */
	double lastX;
	double lastY;
	double lastZ;
	/** Ticks since this fighter last swung (an opponent can see the swing animation). */
	int ticksSinceSwing = 100;
	Inputs keys = Inputs.IDLE;

	// The world around it.
	boolean inWater;
	boolean inLava;
	boolean eyesInWater;
	double waterHeight;
	double lavaHeight;
	boolean inWeb;
	/** Entity#stuckSpeedMultiplier for the next move, or null. */
	private double[] stuck;
	int fireTicks;

	// Items.
	final SimStack[] slots = new SimStack[InventoryState.SIZE];
	int selected;
	float absorption;
	int regenTicks;
	private Use using = Use.NONE;
	int useTicks;
	int shieldCooldown;
	private boolean useHeld;
	private int rightClickDelay;
	boolean inventoryOpen;
	// Breaking a cobweb (MultiPlayerGameMode#continueDestroyBlock).
	private boolean destroying;
	private int destroyX;
	private int destroyY;
	private int destroyZ;
	private float destroyProgress;
	private int destroyDelay;

	// Statistics.
	int swings;
	int hits;
	int crits;
	int sprintHits;
	float damageDealt;
	int blockedHits;
	int strafeTicks;
	int ticks;
	int gapplesEaten;
	int lavaPours;
	int waterPours;
	int blocksPlaced;
	int websPlaced;
	int scoops;
	int websBroken;
	int arrowsShot;
	int arrowHits;
	float lavaDamage;
	float fireDamage;
	float fallDamage;

	SimFighter(Loadout loadout, double x, double z, float yaw) {
		this(loadout, null, x, 0, z, yaw);
	}

	SimFighter(Loadout loadout, SimWorld world, double x, double y, double z, float yaw) {
		this.loadout = loadout;
		this.world = world != null ? world : SimWorld.arena(64, 0, new Rng(0));
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.lastX = x;
		this.lastY = y;
		this.lastZ = z;
		fill();
	}

	private void fill() {
		Kit kit = loadout.kit();
		if (kit == null) {
			slots[SWORD_SLOT] = new SimStack(ItemKind.SWORD, "minecraft:diamond_sword", 1, loadout.attackDamage(), loadout.attackSpeed(), loadout.enchantBonus(), 0);
			if (loadout.hasAxe()) {
				slots[AXE_SLOT] = new SimStack(ItemKind.AXE, "minecraft:diamond_axe", 1, loadout.axeDamage(), Loadout.AXE_SPEED, 0, 0);
			}
			if (loadout.gapples() > 0) {
				slots[GAPPLE_SLOT] = new SimStack(ItemKind.GOLDEN_APPLE, "minecraft:golden_apple", loadout.gapples(), 1, 4, 0, 0);
			}
			return;
		}
		for (Map.Entry<String, Kit.KitItem> e : kit.slots().entrySet()) {
			int slot = Integer.parseInt(e.getKey());
			if (slot >= 0 && slot < InventoryState.SIZE) {
				slots[slot] = SimStack.fromKit(e.getValue());
			}
		}
	}

	public double x() {
		return x;
	}

	public double z() {
		return z;
	}

	public float health() {
		return health;
	}

	public boolean sprinting() {
		return sprinting;
	}

	SimStack held() {
		return slots[selected];
	}

	private ItemKind heldKind() {
		SimStack s = slots[selected];
		return s == null ? ItemKind.EMPTY : s.kind();
	}

	/** Player#getAttackStrengthScale(0.5), for the held item. */
	float attackStrength() {
		return (float) Math.max(0, Math.min(1, (attackTicker + 0.5) / chargeTicks()));
	}

	private double chargeTicks() {
		SimStack s = held();
		return 20.0 / (s == null ? SimStack.HAND_SPEED : s.attackSpeed());
	}

	/** A shield up long enough to block. */
	boolean blocking() {
		return using == Use.SHIELD && useTicks >= SHIELD_DELAY_TICKS;
	}

	boolean usingItem() {
		return using != Use.NONE;
	}

	private ItemKind usingKind() {
		return switch (using) {
			case NONE -> ItemKind.EMPTY;
			case SHIELD -> ItemKind.SHIELD;
			case BOW -> ItemKind.BOW;
			case EAT -> heldKind();
		};
	}

	Vec3 position() {
		return new Vec3(x, y, z);
	}

	Vec3 eye() {
		return new Vec3(x, y + EYE_HEIGHT, z);
	}

	Vec3 look() {
		return Angles.lookVector(yaw, pitch);
	}

	boolean onFire() {
		return fireTicks > 0;
	}

	int count(ItemKind kind) {
		int n = 0;
		for (SimStack s : slots) {
			if (s != null && s.kind() == kind) {
				n += s.count();
			}
		}
		return n;
	}

	SelfState selfState() {
		List<EffectInfo> effects = new ArrayList<>(2);
		if (absorption > 0) {
			effects.add(new EffectInfo("minecraft:absorption", 0, 2400));
		}
		if (regenTicks > 0) {
			effects.add(new EffectInfo("minecraft:regeneration", 1, regenTicks));
		}
		ItemKind held = heldKind();
		SimWorld.Hit block = world.clip(eye(), look(), 6.0, SimWorld.Clip.OUTLINE);
		return new SelfState(position(), eye(), new Vec3(vx, vy, vz), yaw, pitch, health, MAX_HEALTH, absorption, 20, onGround, sprinting, inWater,
			horizontalCollision, fallDistance, attackStrength(), REACH, hurtTime, held != ItemKind.SPEAR, dropDepths(), inventory(), effects,
			onFire(), inLava, inWeb, block == null ? Double.POSITIVE_INFINITY : block.distance());
	}

	/** This fighter as its opponent sees it (Perception#targetState, while visible). */
	TargetState asTarget(int id) {
		Vec3 velocity = new Vec3(x - lastX, y - lastY, z - lastZ);
		return new TargetState(id, "Sim" + id, position(), velocity, yaw, health, MAX_HEALTH, onGround, hurtTime, blocking(), true, 0, HALF_WIDTH,
			HEIGHT, heldKind(), loadout.shield() ? ItemKind.SHIELD : ItemKind.EMPTY, usingKind(), loadout.armor(), onFire(), inWeb, inWater,
			Math.min(TargetState.NO_SWING, ticksSinceSwing));
	}

	private InventoryState inventory() {
		ItemInfo[] items = new ItemInfo[InventoryState.SIZE];
		for (int i = 0; i < items.length; i++) {
			items[i] = slots[i] == null ? ItemInfo.EMPTY : slots[i].info();
		}
		ItemInfo offhand = loadout.shield() ? new ItemInfo(ItemKind.SHIELD, "minecraft:shield", 1, 1.0, 1.0, shieldCooldown > 0, false, null) : ItemInfo.EMPTY;
		ItemInfo diamond = new ItemInfo(ItemKind.ARMOR, "minecraft:diamond_chestplate", 1, 1.0, 1.0, false, false, null);
		ItemInfo[] armor = loadout.armor() > 0 ? new ItemInfo[] {diamond, diamond, diamond, diamond}
			: new ItemInfo[] {ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY};
		boolean use = using != Use.NONE;
		return new InventoryState(items, offhand, armor, selected, use, using == Use.SHIELD, use ? useTicks : 0, usingKind(), blocking());
	}

	/**
	 * Perception#dropDepths: for each of 8 compass directions one block away, how far the ground drops;
	 * lava (there, or 1.6 blocks ahead at the feet' level) counts as a deadly drop, water as ground.
	 */
	private int[] dropDepths() {
		int[] depths = new int[SelfState.DIRECTIONS];
		int feetY = (int) Math.floor(y + EPS);
		for (int i = 0; i < SelfState.DIRECTIONS; i++) {
			double rad = Math.toRadians(i * 45.0);
			double sx = x - Math.sin(rad);
			double sz = z + Math.cos(rad);
			depths[i] = SelfState.VOID_DROP;
			if (world.type((int) Math.floor(sx), feetY, (int) Math.floor(sz)) == SimWorld.LAVA
				|| world.type((int) Math.floor(x - Math.sin(rad) * 1.6), feetY, (int) Math.floor(z + Math.cos(rad) * 1.6)) == SimWorld.LAVA) {
				continue;
			}
			for (int drop = 0; drop <= 24; drop++) {
				int by = feetY - 1 - drop;
				if (by < world.minY - 1) {
					break;
				}
				byte t = world.type((int) Math.floor(sx), by, (int) Math.floor(sz));
				if (t == SimWorld.LAVA) {
					break;
				}
				if (SimWorld.solid(t) || t == SimWorld.WATER) {
					depths[i] = drop;
					break;
				}
			}
		}
		return depths;
	}

	/** Start of tick: knockback sent last tick arrives. */
	void receiveKnockback() {
		if (pendingMotion != null) {
			vx = pendingMotion.x();
			vy = pendingMotion.y();
			vz = pendingMotion.z();
			pendingMotion = null;
		}
	}

	/** Mouse movement, hotbar and the inventory screen, as the client applies them before anything else. */
	void look(Inputs in) {
		keys = in;
		if (in.inventoryOpen()) {
			inventoryOpen = true;
			if (in.inventoryClick() != null) {
				swap(in.inventoryClick());
			}
			return;
		}
		inventoryOpen = false;
		yaw = Angles.wrapDegrees(yaw + in.yawDelta());
		pitch = Math.max(-90.0F, Math.min(90.0F, pitch + in.pitchDelta()));
		if (in.hotbarSlot() >= 0 && in.hotbarSlot() < InventoryState.HOTBAR_SIZE && in.hotbarSlot() != selected) {
			SimStack before = held();
			selected = in.hotbarSlot();
			heldChanged(before);
			if (using == Use.EAT || using == Use.BOW) {
				using = Use.NONE; // the item left the hand
			}
		}
	}

	/** Player#tick: a different item in hand starts the charge again. */
	private void heldChanged(SimStack before) {
		SimStack now = held();
		String a = before == null ? "" : before.id();
		String b = now == null ? "" : now.id();
		if (!a.equals(b)) {
			attackTicker = 0;
		}
	}

	/** A number-key click over an inventory slot (ContainerInput.SWAP): the slot and the hotbar slot trade places. */
	private void swap(InventoryClick click) {
		int from = click.slot();
		int to = click.button();
		if (from < 0 || from >= InventoryState.SIZE || to < 0 || to >= InventoryState.HOTBAR_SIZE) {
			return;
		}
		SimStack before = held();
		SimStack a = slots[from];
		slots[from] = slots[to];
		slots[to] = a;
		heldChanged(before);
	}

	/**
	 * The clicks of one tick (Minecraft#handleKeybinds as ClientEmulator#apply does it): while an item is
	 * in use, attack clicks are swallowed and letting go of right click releases it (a bow shoots);
	 * otherwise a left click attacks and a right click press uses the held item, repeated every 4 ticks
	 * while held.
	 */
	void act(Inputs in, SimFighter other, Rng rng) {
		if (rightClickDelay > 0) {
			rightClickDelay--;
		}
		if (missTime > 0) {
			missTime--;
		}
		if (in.inventoryOpen()) {
			release(rng);
			useHeld = false;
			return;
		}
		if (using != Use.NONE) {
			if (!in.use()) {
				release(rng);
			}
		} else {
			if (in.attack()) {
				click(other, rng);
			}
			if (in.use() && !useHeld) {
				startUse(other);
			}
		}
		if (in.use() && rightClickDelay == 0 && using == Use.NONE) {
			startUse(other);
		}
		useHeld = in.use();
		if (in.holdAttack() && using == Use.NONE) {
			continueDestroy(other);
		} else {
			destroying = false;
			destroyProgress = 0;
		}
	}

	/**
	 * Minecraft#continueAttack with the button held on a cobweb: MultiPlayerGameMode#continueDestroyBlock
	 * starts on the block, then adds BlockState#getDestroyProgress each tick and breaks it at 1, then waits
	 * 5 ticks. Bots break only cobwebs (they don't dig the arena).
	 */
	private void continueDestroy(SimFighter other) {
		if (destroyDelay > 0) {
			destroyDelay--;
			return;
		}
		SimWorld.Hit hit = crosshairBlock(other);
		if (hit == null || world.type(hit.x(), hit.y(), hit.z()) != SimWorld.WEB) {
			destroying = false;
			destroyProgress = 0;
			return;
		}
		if (!destroying || hit.x() != destroyX || hit.y() != destroyY || hit.z() != destroyZ) {
			destroying = true;
			destroyX = hit.x();
			destroyY = hit.y();
			destroyZ = hit.z();
			destroyProgress = 0;
			return;
		}
		destroyProgress += webProgress();
		if (destroyProgress >= 1.0F) {
			world.set(destroyX, destroyY, destroyZ, SimWorld.AIR);
			destroying = false;
			destroyProgress = 0;
			destroyDelay = 5;
			websBroken++;
		}
	}

	/**
	 * BlockState#getDestroyProgress for a cobweb (hardness 4): a sword is the right tool (speed 15, / 30),
	 * anything else is speed 1 and the wrong tool (/ 100); a fifth as fast with the eyes under water or
	 * off the ground (Player#getDestroySpeed).
	 */
	private float webProgress() {
		boolean sword = heldKind() == ItemKind.SWORD;
		float speed = sword ? 15.0F : 1.0F;
		if (eyesInWater) {
			speed *= 0.2F;
		}
		if (!onGround) {
			speed *= 0.2F;
		}
		return speed / 4.0F / (sword ? 30.0F : 100.0F);
	}

	/** RELEASE_USE_ITEM: lowers a shield, stops eating, or shoots the bow. */
	private void release(Rng rng) {
		if (using == Use.BOW) {
			shoot(rng);
		}
		using = Use.NONE;
	}

	/** BowItem#releaseUsing: power from how long it was drawn; full power is a critical arrow. */
	private void shoot(Rng rng) {
		float t = useTicks / 20.0F;
		float pow = Math.min(1.0F, (t * t + t * 2.0F) / 3.0F);
		int arrowSlot = slotOf(ItemKind.ARROW);
		SimStack bow = held();
		if (pow < 0.1 || arrowSlot < 0 || bow == null || bow.kind() != ItemKind.BOW) {
			return;
		}
		slots[arrowSlot] = slots[arrowSlot].withCount(slots[arrowSlot].count() - 1);
		// Projectile#shootFromRotation: along the look, a little spread, plus the shooter's own motion.
		Vec3 dir = look();
		double spread = 0.0172275;
		Vec3 v = new Vec3(dir.x() + rng.triangle(spread), dir.y() + rng.triangle(spread), dir.z() + rng.triangle(spread)).normalize().scale(pow * 3.0);
		v = v.add(new Vec3(x - lastX, onGround ? 0 : y - lastY, z - lastZ));
		shot.add(new SimArrow(this, x, y + EYE_HEIGHT - 0.1, z, v, pow >= 1.0F, 2.0 + bow.power()));
		arrowsShot++;
	}

	/** First inventory slot holding an item of this kind, or -1. */
	private int slotOf(ItemKind kind) {
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] != null && slots[i].kind() == kind) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Minecraft#startUseItem: a block or cobweb goes on the block under the crosshair; an item with a use
	 * of its own (food, a bow, a bucket) is used; anything else falls through to the offhand shield.
	 */
	private void startUse(SimFighter other) {
		rightClickDelay = RIGHT_CLICK_DELAY;
		SimStack main = held();
		ItemKind kind = main == null ? ItemKind.EMPTY : main.kind();
		if (kind == ItemKind.BLOCK || kind == ItemKind.COBWEB) {
			if (place(main, other)) {
				return;
			}
		} else if (kind == ItemKind.GOLDEN_APPLE || kind == ItemKind.ENCHANTED_GOLDEN_APPLE) {
			using = Use.EAT;
			useTicks = 0;
			return;
		} else if (kind == ItemKind.BOW) {
			if (slotOf(ItemKind.ARROW) >= 0) {
				using = Use.BOW;
				useTicks = 0;
				return;
			}
		} else if (kind == ItemKind.WATER_BUCKET || kind == ItemKind.LAVA_BUCKET || kind == ItemKind.BUCKET) {
			bucket(main, other);
			return; // a bucket has a use of its own, so the click never reaches the offhand
		}
		if (loadout.shield() && shieldCooldown == 0) {
			using = Use.SHIELD;
			useTicks = 0;
		}
	}

	/** The block the crosshair is on (LocalPlayer#pick): null if none within block reach or the opponent is in front of it. */
	private SimWorld.Hit crosshairBlock(SimFighter other) {
		Vec3 eye = eye();
		Vec3 dir = look();
		SimWorld.Hit hit = world.clip(eye, dir, BLOCK_REACH, SimWorld.Clip.OUTLINE);
		if (hit == null) {
			return null;
		}
		double entity = rayEntry(eye, dir, other.boxMin(), other.boxMax(), hit.distance());
		return entity >= 0 && entity < hit.distance() ? null : hit;
	}

	/** BlockItem#place: into the space next to the clicked face, if it is free and (for a solid block) no body is in it. */
	private boolean place(SimStack stack, SimFighter other) {
		SimWorld.Hit hit = crosshairBlock(other);
		if (hit == null) {
			return false;
		}
		int px = hit.x() + faceX(hit.face());
		int py = hit.y() + faceY(hit.face());
		int pz = hit.z() + faceZ(hit.face());
		if (!world.replaceable(px, py, pz)) {
			return false;
		}
		boolean web = stack.kind() == ItemKind.COBWEB;
		if (!web && (overlapsBlock(px, py, pz) || other.overlapsBlock(px, py, pz))) {
			return false;
		}
		world.set(px, py, pz, web ? SimWorld.WEB : SimWorld.COBBLE);
		slots[selected] = stack.withCount(stack.count() - 1);
		if (web) {
			websPlaced++;
		} else {
			blocksPlaced++;
		}
		return true;
	}

	/** BucketItem#use: a filled bucket empties next to the clicked block; an empty one takes a source fluid. */
	private void bucket(SimStack stack, SimFighter other) {
		Vec3 eye = eye();
		Vec3 dir = look();
		if (stack.kind() == ItemKind.BUCKET) {
			SimWorld.Hit hit = world.clip(eye, dir, BLOCK_REACH, SimWorld.Clip.SOURCE);
			if (hit == null || !world.isSource(hit.x(), hit.y(), hit.z())) {
				return;
			}
			byte fluid = world.type(hit.x(), hit.y(), hit.z());
			world.set(hit.x(), hit.y(), hit.z(), SimWorld.AIR);
			slots[selected] = fluid == SimWorld.WATER ? SimStack.of(ItemKind.WATER_BUCKET, "minecraft:water_bucket")
				: SimStack.of(ItemKind.LAVA_BUCKET, "minecraft:lava_bucket");
			attackTicker = 0;
			scoops++;
			return;
		}
		SimWorld.Hit hit = world.clip(eye, dir, BLOCK_REACH, SimWorld.Clip.OUTLINE);
		if (hit == null) {
			return;
		}
		int px = hit.x() + faceX(hit.face());
		int py = hit.y() + faceY(hit.face());
		int pz = hit.z() + faceZ(hit.face());
		byte there = world.type(px, py, pz);
		// BucketItem#emptyContents: into air or a fluid; not into a cobweb (not replaceable by a fluid).
		if (there != SimWorld.AIR && there != SimWorld.WATER && there != SimWorld.LAVA) {
			return;
		}
		boolean lava = stack.kind() == ItemKind.LAVA_BUCKET;
		world.set(px, py, pz, lava ? SimWorld.LAVA : SimWorld.WATER);
		slots[selected] = SimStack.of(ItemKind.BUCKET, "minecraft:bucket");
		attackTicker = 0;
		if (lava) {
			lavaPours++;
		} else {
			waterPours++;
		}
	}

	private static int faceX(int face) {
		return face == SimWorld.EAST ? 1 : face == SimWorld.WEST ? -1 : 0;
	}

	private static int faceY(int face) {
		return face == SimWorld.UP ? 1 : face == SimWorld.DOWN ? -1 : 0;
	}

	private static int faceZ(int face) {
		return face == SimWorld.SOUTH ? 1 : face == SimWorld.NORTH ? -1 : 0;
	}

	Vec3 boxMin() {
		return new Vec3(x - HALF_WIDTH, y, z - HALF_WIDTH);
	}

	Vec3 boxMax() {
		return new Vec3(x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
	}

	/** Whether the body overlaps a block space (Level#isUnobstructed for a full block). */
	boolean overlapsBlock(int bx, int by, int bz) {
		return x + HALF_WIDTH > bx + EPS && x - HALF_WIDTH < bx + 1 - EPS && z + HALF_WIDTH > bz + EPS && z - HALF_WIDTH < bz + 1 - EPS
			&& y + HEIGHT > by + EPS && y < by + 1 - EPS;
	}

	/**
	 * A left click (Minecraft#startAttack with LocalPlayer#pick): the opponent if the crosshair is on them
	 * within reach and no block outline comes first; a block takes the click (bots never mine); nothing
	 * at all is a miss, which locks clicks for 10 ticks.
	 */
	void click(SimFighter other, Rng rng) {
		if (using != Use.NONE || missTime > 0) {
			return; // clicks do nothing while an item is in use or after a miss
		}
		swings++;
		ticksSinceSwing = 0;
		Vec3 eye = eye();
		Vec3 dir = look();
		SimWorld.Hit block = world.clip(eye, dir, BLOCK_REACH, SimWorld.Clip.OUTLINE);
		double blockDistance = block == null ? Double.POSITIVE_INFINITY : block.distance();
		double entry = rayEntry(eye, dir, other.boxMin(), other.boxMax(), Math.min(BLOCK_REACH, blockDistance));
		if (entry >= 0 && entry < blockDistance) {
			if (entry <= REACH) {
				attack(other, rng);
				return;
			}
			missTime = MISS_TIME;
		} else if (block == null) {
			missTime = MISS_TIME;
		}
	}

	private void attack(SimFighter other, Rng rng) {
		float strength = attackStrength();
		SimStack weapon = held();
		ItemKind kind = heldKind();
		double itemDamage = weapon == null ? SimStack.HAND_DAMAGE : weapon.attackDamage();
		double base = itemDamage * (0.2 + strength * strength * 0.8);
		double magic = weapon == null ? 0 : strength * weapon.magic();
		attackTicker = 0; // Player#onAttack
		boolean full = strength > 0.9F;
		boolean knockbackAttack = sprinting && full;
		boolean crit = full && fallDistance > 0 && !onGround && !inWater && !sprinting;
		if (crit) {
			base *= 1.5;
		}
		float damage = (float) (base + magic);
		Vec3 oldMovement = new Vec3(other.vx, other.vy, other.vz);
		int landed = other.hurt(damage, Damage.MELEE, this, kind == ItemKind.AXE, x, z);
		boolean fullHit = landed == FULL;
		if (landed != MISSED) {
			hits++;
			if (crit) {
				crits++;
			}
		}
		if (fullHit && knockbackAttack) {
			sprintHits++;
			// Player#causeExtraKnockback: along the attacker's facing; the attacker slows down and stops sprinting.
			double rad = Math.toRadians(yaw);
			other.knockback(SPRINT_KNOCKBACK, Math.sin(rad), -Math.cos(rad), oldMovement);
			vx *= 0.6;
			vz *= 0.6;
			sprinting = false;
		}
	}

	static final int MISSED = 0;
	static final int PARTIAL = 1;
	static final int FULL = 2;
	static final int BLOCKED = 3;

	/**
	 * LivingEntity#hurtServer: {@link #FULL} (with knockback away from the source at {@code srcX, srcZ}),
	 * {@link #PARTIAL} (the difference, while invulnerable), {@link #BLOCKED} or {@link #MISSED}.
	 */
	int hurt(float damage, Damage type, SimFighter attacker, boolean axe, double srcX, double srcZ) {
		if (dead()) {
			return MISSED;
		}
		if (type.shieldable && blocking() && facingTowards(srcX, srcZ)) {
			// BlocksAttacks: all of it blocked. An axe disables the shield; the hit still starts the
			// invulnerability (hurtServer carries on with 0 damage); it isn't marked hurt, so nobody is pushed.
			blockedHits++;
			if (axe) {
				shieldCooldown = SHIELD_DISABLE_TICKS;
				using = Use.NONE;
			}
			if (invulnerableTime > 10) {
				return MISSED;
			}
			lastHurt = 0;
			invulnerableTime = 20;
			hurtTime = 10;
			return BLOCKED;
		}
		boolean full;
		float dealt;
		if (invulnerableTime > 10) {
			if (damage <= lastHurt) {
				return MISSED;
			}
			dealt = actualDamage(damage - lastHurt, type);
			lastHurt = damage;
			full = false;
		} else {
			lastHurt = damage;
			invulnerableTime = 20;
			hurtTime = 10;
			dealt = actualDamage(damage, type);
			full = true;
		}
		float soaked = Math.min(absorption, dealt);
		absorption -= soaked;
		health -= dealt - soaked;
		if (attacker != null) {
			attacker.damageDealt += dealt;
		}
		switch (type) {
			case LAVA -> lavaDamage += dealt;
			case FIRE -> fireDamage += dealt;
			case FALL -> fallDamage += dealt;
			default -> {
			}
		}
		if (full && type.knockback) {
			knockback(DEFAULT_KNOCKBACK, srcX - x, srcZ - z, new Vec3(vx, vy, vz));
		}
		return full ? FULL : PARTIAL;
	}

	/** LivingEntity#actuallyHurt: armor (unless it bypasses armor), then Protection. */
	private float actualDamage(float damage, Damage type) {
		float d = type.armor ? afterArmor(damage) : damage;
		return d * (float) (1.0 - Math.min(20, loadout.protection()) / 25.0);
	}

	/** Whether a source at {@code sx, sz} is within 90 degrees of where this fighter faces (a shield covers that). */
	private boolean facingTowards(double sx, double sz) {
		double rad = Math.toRadians(yaw);
		double lx = -Math.sin(rad);
		double lz = Math.cos(rad);
		double dx = sx - x;
		double dz = sz - z;
		double len = Math.sqrt(dx * dx + dz * dz);
		return len < 1e-6 || (lx * dx + lz * dz) / len >= 0;
	}

	/** CombatRules#getDamageAfterAbsorb. */
	private float afterArmor(float damage) {
		double toughness = 2.0 + loadout.toughness() / 4.0;
		double armor = Math.max(loadout.armor() * 0.2, Math.min(20.0, loadout.armor() - damage / toughness));
		return (float) (damage * (1.0 - armor / 25.0));
	}

	/**
	 * LivingEntity#knockback, computed on the velocity the server knows; the result reaches the fighter
	 * at the start of its next tick. Several pushes in one tick build on each other.
	 */
	void knockback(double power, double dx, double dz, Vec3 base) {
		Vec3 now = pendingMotion != null ? pendingMotion : base;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-5) {
			dx = 0.01;
			dz = 0;
			len = 0.01;
		}
		double px = dx / len * power;
		double pz = dz / len * power;
		pendingMotion = new Vec3(now.x() / 2.0 - px, onGround ? Math.min(0.4, now.y() / 2.0 + power) : now.y(), now.z() / 2.0 - pz);
	}

	/** One tick of the body: Entity#baseTick, sprinting, jumping, travel, blocks touched, timers. */
	void move() {
		Inputs in = inventoryOpen ? Inputs.IDLE : keys;
		ticks++;
		if (in.strafe() != 0) {
			strafeTicks++;
		}
		// Entity#baseTick.
		updateFluids(true);
		if (fireTicks > 0) {
			if (fireTicks % 20 == 0 && !inLava) {
				hurt(1.0F, Damage.FIRE, null, false, x, z);
			}
			fireTicks--;
		}
		if (inLava) {
			fallDistance *= 0.5;
		}

		// ClientEmulator#updateSprint: the sprint key starts a sprint while moving forward, not in shallow water.
		boolean forwardKey = in.forward() > 0;
		boolean possible = !(inWater && !eyesInWater);
		if (!sprinting && forwardKey && in.sprint() && using == Use.NONE && possible) {
			sprinting = true;
		} else if (sprinting && (!forwardKey || horizontalCollision || !possible)) {
			sprinting = false;
		}

		// LocalPlayer#modifyInput.
		double left = in.strafe();
		double forward = in.forward();
		double length = Math.sqrt(left * left + forward * forward);
		if (length > 0) {
			double scale = using != Use.NONE ? 0.98 * USE_SLOWDOWN : 0.98;
			left = left / length * scale;
			forward = forward / length * scale;
			double scaled = Math.sqrt(left * left + forward * forward);
			double dirX = Math.abs(left / scaled);
			double dirY = Math.abs(forward / scaled);
			double tan = dirY > dirX ? dirX / dirY : dirY / dirX;
			double modified = Math.min(scaled * Math.sqrt(1.0 + tan * tan), 1.0);
			left = left / scaled * modified;
			forward = forward / scaled * modified;
		}

		// LivingEntity#aiStep: jumping, in fluids too.
		if (noJumpDelay > 0) {
			noJumpDelay--;
		}
		if (in.jump()) {
			double fluid = inLava ? lavaHeight : waterHeight;
			boolean waterDepth = inWater && fluid > 0;
			if (!waterDepth || onGround && !(fluid > FLUID_JUMP_THRESHOLD)) {
				if (!inLava || onGround && lavaHeight <= FLUID_JUMP_THRESHOLD) {
					if ((onGround || waterDepth && fluid <= FLUID_JUMP_THRESHOLD) && noJumpDelay == 0) {
						jumpFromGround();
						noJumpDelay = JUMP_DELAY;
					}
				} else {
					vy += 0.04F;
				}
			} else {
				vy += 0.04F;
			}
		} else {
			noJumpDelay = 0;
		}

		lastX = x;
		lastY = y;
		lastZ = z;
		if (inWater || inLava) {
			travelInFluid(left, forward);
		} else {
			travelInAir(left, forward);
		}
		insideBlocks();
		timers();
	}

	private void jumpFromGround() {
		vy = Math.max(JUMP_POWER, vy);
		if (sprinting) {
			double rad = Math.toRadians(yaw);
			vx += -Math.sin(rad) * SPRINT_JUMP_BOOST;
			vz += Math.cos(rad) * SPRINT_JUMP_BOOST;
		}
	}

	/** Entity#moveRelative. */
	private void moveRelative(double speed, double left, double forward) {
		double inLength = left * left + forward * forward;
		if (inLength < 1.0E-7) {
			return;
		}
		double norm = inLength > 1.0 ? Math.sqrt(inLength) : 1.0;
		double ix = left / norm * speed;
		double iz = forward / norm * speed;
		double rad = Math.toRadians(yaw);
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		vx += ix * cos - iz * sin;
		vz += iz * cos + ix * sin;
	}

	private void travelInAir(double left, double forward) {
		boolean grounded = onGround;
		double speed = grounded ? WALK_SPEED * (sprinting ? SPRINT_FACTOR : 1.0) : sprinting ? AIR_SPEED_SPRINTING : AIR_SPEED;
		moveRelative(speed, left, forward);
		moveBody();
		vy -= GRAVITY;
		double friction = grounded ? GROUND_FRICTION : AIR_FRICTION;
		vx *= friction;
		vz *= friction;
		vy *= VERTICAL_DRAG;
	}

	/** LivingEntity#travelInWater / #travelInLava. */
	private void travelInFluid(double left, double forward) {
		boolean falling = vy <= 0.0;
		double oldY = y;
		if (inWater) {
			double slowDown = sprinting ? 0.9 : 0.8;
			moveRelative(0.02, left, forward);
			moveBody();
			vx *= slowDown;
			vy *= 0.8F;
			vz *= slowDown;
			fluidFallAdjust(falling);
		} else {
			moveRelative(0.02, left, forward);
			moveBody();
			if (lavaHeight <= FLUID_JUMP_THRESHOLD) {
				vx *= 0.5;
				vy *= 0.8F;
				vz *= 0.5;
				fluidFallAdjust(falling);
			} else {
				vx *= 0.5;
				vy *= 0.5;
				vz *= 0.5;
			}
			vy -= GRAVITY / 4.0;
		}
		// LivingEntity#jumpOutOfFluid: against an edge with dry room 0.6 higher, a hop out (Entity#isFree).
		double hopY = vy + 0.6 + oldY;
		if (horizontalCollision && !world.collides(x - HALF_WIDTH + vx, hopY, z - HALF_WIDTH + vz, x + HALF_WIDTH + vx, hopY + HEIGHT, z + HALF_WIDTH + vz)
			&& !world.anyFluid(x - HALF_WIDTH + vx, hopY, z - HALF_WIDTH + vz, x + HALF_WIDTH + vx, hopY + HEIGHT, z + HALF_WIDTH + vz)) {
			vy = 0.3F;
		}
	}

	/** LivingEntity#getFluidFallingAdjustedMovement. */
	private void fluidFallAdjust(boolean falling) {
		if (sprinting) {
			return;
		}
		if (falling && Math.abs(vy - 0.005) >= 0.003 && Math.abs(vy - GRAVITY / 16.0) < 0.003) {
			vy = -0.003;
		} else {
			vy -= GRAVITY / 16.0;
		}
	}

	/** Entity#move: web slowdown, collisions with step-up, landing, fall damage, velocity into blocks zeroed. */
	private void moveBody() {
		double dx = vx;
		double dy = vy;
		double dz = vz;
		if (stuck != null) {
			dx *= stuck[0];
			dy *= stuck[1];
			dz *= stuck[2];
			stuck = null;
			vx = 0;
			vy = 0;
			vz = 0;
		}
		double[] m = collide(dx, dy, dz);
		x += m[0];
		y += m[1];
		z += m[2];
		boolean xCollision = Math.abs(dx - m[0]) > 1.0E-9;
		boolean zCollision = Math.abs(dz - m[2]) > 1.0E-9;
		horizontalCollision = xCollision || zCollision;
		boolean verticalCollision = Math.abs(dy - m[1]) > 1.0E-9;
		onGround = verticalCollision && dy < 0.0;
		// LivingEntity#checkFallDamage / Entity#checkFallDamage.
		if (!inWater) {
			updateFluids(true);
		}
		if (!inWater && m[1] < 0.0) {
			fallDistance -= m[1];
		}
		if (onGround) {
			if (fallDistance > 0.0) {
				int damage = (int) Math.floor(fallDistance + 1.0E-6 - 3.0);
				if (damage > 0) {
					hurt(damage, Damage.FALL, null, false, x, z);
				}
			}
			fallDistance = 0;
		}
		// Entity#restituteMovementAfterCollisions (no bounce).
		if (xCollision) {
			vx = 0;
		}
		if (zCollision) {
			vz = 0;
		}
		if (verticalCollision) {
			vy = 0;
		}
	}

	/** Entity#collide: axis by axis against solid blocks (Y first), then the step-up of 0.6 if that gets further. */
	private double[] collide(double dx, double dy, double dz) {
		double minX = x - HALF_WIDTH;
		double minY = y;
		double minZ = z - HALF_WIDTH;
		double[] step = collideBox(minX, minY, minZ, dx, dy, dz);
		boolean xCol = Math.abs(dx - step[0]) > 1.0E-9;
		boolean zCol = Math.abs(dz - step[2]) > 1.0E-9;
		boolean landed = Math.abs(dy - step[1]) > 1.0E-9 && dy < 0;
		if ((landed || onGround) && (xCol || zCol)) {
			double groundY = landed ? minY + step[1] : minY;
			// collectCandidateStepUpHeights: block tops within 0.6 above the feet (full blocks: whole numbers).
			double top = Math.floor(groundY + EPS) + 1.0;
			for (double candidate = top - groundY; candidate <= STEP_HEIGHT + EPS; candidate += 1.0) {
				if (candidate <= EPS || Math.abs(candidate - step[1]) < 1.0E-6) {
					continue;
				}
				double[] up = collideBox(minX, groundY, minZ, dx, candidate, dz);
				if (up[0] * up[0] + up[2] * up[2] > step[0] * step[0] + step[2] * step[2]) {
					return new double[] {up[0], up[1] - (minY - groundY), up[2]};
				}
			}
		}
		return step;
	}

	/** Shapes#collide with full blocks: Y, then the larger horizontal axis first (Direction#axisStepOrder). */
	private double[] collideBox(double minX, double minY, double minZ, double dx, double dy, double dz) {
		double w = 2 * HALF_WIDTH;
		double my = world.collide(1, minX, minY, minZ, minX + w, minY + HEIGHT, minZ + w, dy);
		minY += my;
		double mx;
		double mz;
		if (Math.abs(dx) < Math.abs(dz)) {
			mz = world.collide(2, minX, minY, minZ, minX + w, minY + HEIGHT, minZ + w, dz);
			minZ += mz;
			mx = world.collide(0, minX, minY, minZ, minX + w, minY + HEIGHT, minZ + w, dx);
		} else {
			mx = world.collide(0, minX, minY, minZ, minX + w, minY + HEIGHT, minZ + w, dx);
			minX += mx;
			mz = world.collide(2, minX, minY, minZ, minX + w, minY + HEIGHT, minZ + w, dz);
		}
		return new double[] {mx, my, mz};
	}

	/** Entity#updateFluidInteraction: in water or lava, how deep, eyes under, the current's push. */
	private void updateFluids(boolean push) {
		double d = 0.001;
		double ex = x;
		double ey = y + EYE_HEIGHT;
		double ez = z;
		SimWorld.FluidContact water = world.fluidContact(SimWorld.WATER, x - HALF_WIDTH + d, y + d, z - HALF_WIDTH + d, x + HALF_WIDTH - d, y + HEIGHT - d,
			z + HALF_WIDTH - d, ex, ey, ez);
		SimWorld.FluidContact lava = world.fluidContact(SimWorld.LAVA, x - HALF_WIDTH + d, y + d, z - HALF_WIDTH + d, x + HALF_WIDTH - d, y + HEIGHT - d,
			z + HALF_WIDTH - d, ex, ey, ez);
		waterHeight = water.height;
		lavaHeight = lava.height;
		inWater = water.height > 0;
		inLava = lava.height > 0;
		eyesInWater = water.eyes;
		if (inWater) {
			fallDistance = 0;
		}
		if (push) {
			if (inWater) {
				current(water, WATER_CURRENT);
			}
			if (inLava) {
				current(lava, LAVA_CURRENT);
			}
		}
	}

	/** EntityFluidInteraction.Tracker#applyCurrentTo for a player: the mean flow, scaled. */
	private void current(SimWorld.FluidContact c, double scale) {
		if (c.flows == 0 || c.flowX * c.flowX + c.flowZ * c.flowZ < 1.0E-5) {
			return;
		}
		double ix = c.flowX / c.flows * scale;
		double iz = c.flowZ / c.flows * scale;
		if (Math.abs(vx) < 0.003 && Math.abs(vz) < 0.003) {
			double len = Math.sqrt(ix * ix + iz * iz);
			if (len < 0.0045) {
				ix = ix / len * 0.0045;
				iz = iz / len * 0.0045;
			}
		}
		vx += ix;
		vz += iz;
	}

	/** Entity#applyEffectsFromBlocks: cobwebs catch, lava burns, water puts fire out. */
	private void insideBlocks() {
		double d = 1.0E-5;
		double minX = x - HALF_WIDTH + d;
		double maxX = x + HALF_WIDTH - d;
		double minZ = z - HALF_WIDTH + d;
		double maxZ = z + HALF_WIDTH - d;
		double minY = y + d;
		double maxY = y + HEIGHT - d;
		inWeb = world.touches(SimWorld.WEB, minX, minY, minZ, maxX, maxY, maxZ);
		if (inWeb) {
			stuck = new double[] {0.25, 0.05F, 0.25};
			fallDistance = 0;
		}
		updateFluids(false);
		if (inLava) {
			fireTicks = Math.max(fireTicks, LAVA_FIRE_TICKS);
			hurt(LAVA_DAMAGE, Damage.LAVA, null, false, x, z);
		}
		if (inWater) {
			fireTicks = 0;
		}
	}

	private void timers() {
		attackTicker++;
		ticksSinceSwing++;
		if (using != Use.NONE) {
			useTicks++;
			if (using == Use.EAT && useTicks >= EAT_TICKS) {
				// A golden apple: Absorption I and Regeneration II.
				using = Use.NONE;
				SimStack s = held();
				if (s != null) {
					slots[selected] = s.withCount(s.count() - 1);
				}
				absorption = Math.max(absorption, GAPPLE_ABSORPTION);
				regenTicks = REGEN_TICKS;
				gapplesEaten++;
			}
		}
		if (shieldCooldown > 0) {
			shieldCooldown--;
		}
		if (regenTicks > 0) {
			if (regenTicks % REGEN_INTERVAL == 0) {
				health = Math.min(MAX_HEALTH, health + 1);
			}
			regenTicks--;
		}
		if (invulnerableTime > 0) {
			invulnerableTime--;
		}
		if (hurtTime > 0) {
			hurtTime--;
		}
	}

	boolean dead() {
		return health <= 0;
	}

	/** Distance along the ray at which it enters the box, or -1 if it misses within {@code maxDistance}. */
	static double rayEntry(Vec3 origin, Vec3 dir, Vec3 min, Vec3 max, double maxDistance) {
		double tMin = 0;
		double tMax = maxDistance;
		double[] o = {origin.x(), origin.y(), origin.z()};
		double[] d = {dir.x(), dir.y(), dir.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1e-9) {
				if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
					return -1;
				}
			} else {
				double t1 = (lo[axis] - o[axis]) / d[axis];
				double t2 = (hi[axis] - o[axis]) / d[axis];
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
				if (tMin > tMax) {
					return -1;
				}
			}
		}
		return tMin;
	}
}

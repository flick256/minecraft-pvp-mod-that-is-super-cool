package io.github.flick256.sparbot.core.act;

import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The last gate between any policy (scripted or ML) and the bot's body. Whatever the policy asks
 * for, the output is limited to what a human could physically do:
 * <ul>
 *   <li>rotation per tick is capped (profile cap, never above {@link #ABSOLUTE_MAX_TURN_DEG})</li>
 *   <li>left clicks are rate-limited by the profile's CPS (never above {@link #ABSOLUTE_MAX_CPS})</li>
 *   <li>inputs arrive {@code ping / 2} late, as they would over a real connection</li>
 *   <li>while the inventory screen is open nothing else can be pressed, the first click needs the
 *       screen open for {@link #MIN_TICKS_OPEN_BEFORE_CLICK} ticks, and clicks / offhand swaps are
 *       rate limited</li>
 * </ul>
 */
public final class InputShaper {
	/** No human flicks more than this per 50 ms tick in a sustained fight. */
	public static final float ABSOLUTE_MAX_TURN_DEG = 60.0F;
	/** Server-wide hard cap, also the most a tick-based bot can physically send (one click per tick). */
	public static final double ABSOLUTE_MAX_CPS = 20.0;
	private static final int PING_RESAMPLE_TICKS = 40;
	/** Opening the inventory and moving the mouse onto a slot takes at least 100 ms. */
	public static final int MIN_TICKS_OPEN_BEFORE_CLICK = 2;
	/** Fastest sustained inventory clicking / swap-key pressing: one action per 2 ticks (10 per second). */
	public static final int MIN_TICKS_BETWEEN_INVENTORY_ACTIONS = 2;

	private final SkillProfile profile;
	private final Rng rng;
	private final double maxCps;
	private final Deque<Inputs> latency = new ArrayDeque<>();
	private int latencyTicks;
	private int targetLatencyTicks;
	private int ticksUntilPingResample;
	private double nextClickAllowedAt;
	private double nextTapAllowedAt;
	private long tick;
	private Inputs lastEmitted = Inputs.IDLE;
	private int inventoryOpenTicks;
	private long lastInventoryActionTick = Long.MIN_VALUE / 2;
	private long lastSwapTick = Long.MIN_VALUE / 2;

	public InputShaper(SkillProfile profile, Rng rng, double serverMaxCps) {
		this.profile = profile;
		this.rng = rng;
		this.maxCps = Math.min(ABSOLUTE_MAX_CPS, serverMaxCps);
		this.targetLatencyTicks = sampleLatencyTicks();
		this.latencyTicks = targetLatencyTicks;
	}

	/** Pushes this tick's desired inputs and returns the inputs that actually reach the server now. */
	public Inputs shape(Inputs desired) {
		tick++;
		Inputs delayed = delay(desired);
		return limit(delayed);
	}

	public int latencyTicks() {
		return latencyTicks;
	}

	public void reset() {
		latency.clear();
		lastEmitted = Inputs.IDLE;
		nextClickAllowedAt = 0;
		nextTapAllowedAt = 0;
		inventoryOpenTicks = 0;
	}

	private Inputs delay(Inputs desired) {
		if (--ticksUntilPingResample <= 0) {
			targetLatencyTicks = sampleLatencyTicks();
			ticksUntilPingResample = PING_RESAMPLE_TICKS;
		}
		// Latency drifts by at most one tick per tick so inputs are never reordered.
		if (latencyTicks < targetLatencyTicks) {
			latencyTicks++;
		} else if (latencyTicks > targetLatencyTicks) {
			latencyTicks--;
		}

		latency.addLast(desired);
		if (latency.size() <= latencyTicks) {
			// Nothing has "arrived" yet: keep holding the previous keys, no new mouse movement or clicks.
			return new Inputs(0, 0, lastEmitted.forward(), lastEmitted.strafe(), lastEmitted.jump(), lastEmitted.sneak(),
				lastEmitted.sprint(), false, lastEmitted.use(), -1, false, lastEmitted.inventoryOpen(), null, lastEmitted.holdAttack());
		}
		Inputs out = latency.pollFirst();
		// If latency shrank, two packets arrive in the same tick: merge them (mouse deltas add up).
		while (latency.size() > latencyTicks) {
			Inputs next = latency.pollFirst();
			out = new Inputs(out.yawDelta() + next.yawDelta(), out.pitchDelta() + next.pitchDelta(), next.forward(), next.strafe(),
				next.jump(), next.sneak(), next.sprint(), out.attack() || next.attack(), next.use(),
				next.hotbarSlot() >= 0 ? next.hotbarSlot() : out.hotbarSlot(), out.swapOffhand() || next.swapOffhand(),
				next.inventoryOpen(), next.inventoryClick() != null ? next.inventoryClick() : out.inventoryClick(), next.holdAttack());
		}
		return out;
	}

	private Inputs limit(Inputs in) {
		if (in.inventoryOpen()) {
			inventoryOpenTicks++;
			InventoryClick click = in.inventoryClick();
			if (click != null && (inventoryOpenTicks <= MIN_TICKS_OPEN_BEFORE_CLICK
				|| tick - lastInventoryActionTick < MIN_TICKS_BETWEEN_INVENTORY_ACTIONS)) {
				click = null;
			}
			if (click != null) {
				lastInventoryActionTick = tick;
			}
			Inputs out = Inputs.inInventory(click);
			lastEmitted = out;
			return out;
		}
		inventoryOpenTicks = 0;

		float cap = (float) Math.min(ABSOLUTE_MAX_TURN_DEG, profile.aim().maxTurnDegPerTick());
		float yaw = in.yawDelta();
		float pitch = in.pitchDelta();
		double magnitude = Math.sqrt(yaw * yaw + pitch * pitch);
		if (magnitude > cap) {
			float scale = (float) (cap / magnitude);
			yaw *= scale;
			pitch *= scale;
		}

		boolean attack = in.attack();
		if (attack) {
			if (tick < nextClickAllowedAt) {
				attack = false;
			} else {
				double cps = Math.min(maxCps, profile.clicking().cps().sample(rng));
				nextClickAllowedAt = tick + 20.0 / cps;
			}
		}

		// Fast right-clicks are clicks too: no faster than the profile's click rate.
		boolean tap = in.tapUse();
		if (tap) {
			if (tick < nextTapAllowedAt) {
				tap = false;
			} else {
				double cps = Math.min(maxCps, profile.clicking().cps().sample(rng));
				nextTapAllowedAt = tick + 20.0 / cps;
			}
		}

		boolean swap = in.swapOffhand() && tick - lastSwapTick >= MIN_TICKS_BETWEEN_INVENTORY_ACTIONS;
		if (swap) {
			lastSwapTick = tick;
		}

		// Holding the button down is not clicking: it isn't held to the click rate.
		Inputs out = new Inputs(yaw, pitch, in.forward(), in.strafe(), in.jump(), in.sneak(), in.sprint(), attack, in.use(), in.hotbarSlot(),
			swap, false, null, in.holdAttack(), tap);
		lastEmitted = out;
		return out;
	}

	private int sampleLatencyTicks() {
		// One-way latency is half the round trip; convert ms to 50 ms ticks.
		return (int) Math.round(profile.pingMs().sample(rng) / 2.0 / 50.0);
	}
}

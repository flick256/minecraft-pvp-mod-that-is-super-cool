package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.act.Inputs;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The scripted moves of a drill's bot when it isn't fighting: keys and mouse like a player's, so the
 * bot's human limits (turn speed, ping, click rate) still apply to them. Each is predictable on purpose:
 * a drill's first two stages teach against a bot you can read.
 */
final class DrillPatterns {
	private DrillPatterns() {
	}

	/** A pattern with something to tell the drill (when it swings, what it is doing). */
	interface Watched extends Function<BotPlayer, Inputs> {
	}

	/** Stands on its mark facing you, walking back to it when a hit knocks it off. */
	static Function<BotPlayer, Inputs> still(Supplier<@Nullable ServerPlayer> target, Vec3 mark) {
		return body -> {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			double dx = mark.x - body.getX();
			double dz = mark.z - body.getZ();
			if (dx * dx + dz * dz < 0.36) {
				return face(body, t, 0, 0, false);
			}
			// The way back in the bot's own frame: forward along its look, left across it (as the movement keys work).
			double yaw = Math.toRadians(body.getYRot());
			double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
			double left = dx * Math.cos(yaw) + dz * Math.sin(yaw);
			int f = Math.abs(forward) > 0.25 ? (int) Math.signum(forward) : 0;
			int l = Math.abs(left) > 0.25 ? (int) Math.signum(left) : 0;
			return face(body, t, f, l, false);
		};
	}

	/**
	 * Walks from side to side facing you, changing direction every second and a half or so, keeping about
	 * {@code distance} away; with {@code shield} its shield is up the whole time.
	 */
	static Function<BotPlayer, Inputs> pace(Supplier<@Nullable ServerPlayer> target, double distance, boolean shield) {
		return body -> {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			long tick = body.level().getGameTime();
			// A new direction (or the same again) every 23 ticks, at random, so the turn can't be timed.
			long phase = (tick + body.getId() * 7L) / 23;
			int strafe = (phase * 0x9E3779B97F4A7C15L >>> 40 & 1) == 0 ? 1 : -1;
			double d = body.distanceTo(t);
			int forward = d > distance + 1.5 ? 1 : d < distance - 1.5 ? -1 : 0;
			return face(body, t, forward, strafe, shield);
		};
	}

	/**
	 * Keeps just inside its reach and swings on a rhythm: every {@code interval} ticks exactly while you learn
	 * it ({@code varied} false), give or take half a second when you practise. Between swings it steps back
	 * out so you can't just stand on it. The drill knows when the next swing comes (for the cues).
	 */
	static final class Attacker implements Watched {
		private final Supplier<@Nullable ServerPlayer> target;
		private final int interval;
		private final boolean varied;
		private final java.util.Random random;
		private long nextSwing = -1;
		long lastSwing = -1;

		Attacker(Supplier<@Nullable ServerPlayer> target, int interval, boolean varied, long seed) {
			this.target = target;
			this.interval = interval;
			this.varied = varied;
			this.random = new java.util.Random(seed);
		}

		/** Ticks until it means to swing (negative: overdue, waiting to be in range). */
		long ticksToSwing(long now) {
			return nextSwing < 0 ? interval : nextSwing - now;
		}

		@Override
		public Inputs apply(BotPlayer body) {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			long now = body.level().getGameTime();
			if (nextSwing < 0) {
				nextSwing = now + interval;
			}
			double d = body.distanceTo(t);
			long left = nextSwing - now;
			// Close in for the swing; drift back out between swings.
			double want = left <= 10 ? 2.4 : 3.6;
			int forward = d > want + 0.4 ? 1 : d < want - 0.4 ? -1 : 0;
			boolean attack = left <= 0 && d <= 3.1 && body.getAttackStrengthScale(0.5F) >= 0.95F;
			if (attack) {
				lastSwing = now;
				nextSwing = now + interval + (varied ? random.nextInt(-10, 11) : 0);
			}
			Inputs in = face(body, t, forward, 0, false);
			return attack ? in.withAttack(true) : in;
		}
	}

	/**
	 * Steps up to just outside your reach, swings at nothing, then stands there recharging for most of a
	 * second (the window to punish it) before backing off and coming again. Come in before it swings and its
	 * swing lands on you instead.
	 */
	static final class Whiffer implements Watched {
		enum State {
			APPROACH,
			OPEN,
			BACK
		}

		private final Supplier<@Nullable ServerPlayer> target;
		State state = State.BACK;
		private long since;
		/** When it last swung (the drill checks whether the swing found you). */
		long lastSwing = -1;

		Whiffer(Supplier<@Nullable ServerPlayer> target) {
			this.target = target;
		}

		@Override
		public Inputs apply(BotPlayer body) {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			long now = body.level().getGameTime();
			double d = body.distanceTo(t);
			boolean charged = body.getAttackStrengthScale(0.5F) >= 0.95F;
			switch (state) {
				case APPROACH -> {
					// A swing from 3.9 blocks (centre to centre) misses a player standing still; one who walked in gets hit.
					if (charged && d <= 3.9) {
						lastSwing = now;
						state = State.OPEN;
						since = now;
						return face(body, t, 0, 0, false).withAttack(true);
					}
					return face(body, t, d > 3.9 ? 1 : 0, 0, false);
				}
				case OPEN -> {
					// Recharging, standing still: the punish window.
					if (now - since >= 18) {
						state = State.BACK;
						since = now;
					}
					return face(body, t, 0, 0, false);
				}
				default -> {
					if (now - since >= 30 && d >= 5.0 || now - since >= 60) {
						state = State.APPROACH;
						since = now;
					}
					return face(body, t, d < 6.0 ? -1 : 0, 0, false);
				}
			}
		}
	}

	/** Strafes at random (a new direction every 6 to 18 ticks), now and then jumps, close in, never attacking. */
	static Function<BotPlayer, Inputs> strafer(Supplier<@Nullable ServerPlayer> target, double distance, long seed) {
		java.util.Random random = new java.util.Random(seed);
		int[] strafe = {1};
		long[] switchAt = {0};
		return body -> {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			long now = body.level().getGameTime();
			if (now >= switchAt[0]) {
				strafe[0] = random.nextBoolean() ? 1 : -1;
				switchAt[0] = now + random.nextInt(6, 19);
			}
			double d = body.distanceTo(t);
			int forward = d > distance + 0.8 ? 1 : d < distance - 0.8 ? -1 : 0;
			boolean jump = body.onGround() && random.nextInt(25) == 0;
			Inputs in = face(body, t, forward, strafe[0], false);
			return jump ? in.withJump(true) : in;
		};
	}

	/**
	 * Waits a moment, then sprints at you, drops to a walk for the last stretch and swings once you are 3
	 * blocks away (centre to centre): an opponent who swings as it comes into their reach, a step before it
	 * can, wins the exchange; one who waits for it to arrive loses it. The drill decides who hit first and
	 * puts you both back for the next exchange ({@link #restart}).
	 */
	static final class Rusher implements Watched {
		private final Supplier<@Nullable ServerPlayer> target;
		private long rushAt = -1;
		boolean swung;

		Rusher(Supplier<@Nullable ServerPlayer> target) {
			this.target = target;
		}

		/** A new exchange: wait {@code delay} ticks, then come. */
		void restart(long now, int delay) {
			rushAt = now + delay;
			swung = false;
		}

		boolean rushing(long now) {
			return rushAt >= 0 && now >= rushAt;
		}

		@Override
		public Inputs apply(BotPlayer body) {
			ServerPlayer t = target.get();
			if (t == null) {
				return Inputs.IDLE;
			}
			long now = body.level().getGameTime();
			if (rushAt < 0) {
				restart(now, 30);
			}
			if (now < rushAt) {
				return face(body, t, 0, 0, false);
			}
			double d = body.distanceTo(t);
			boolean attack = !swung && d <= 3.0 && body.getAttackStrengthScale(0.5F) >= 0.95F;
			if (attack) {
				swung = true;
			}
			Inputs in = face(body, t, d > 1.5 ? 1 : 0, 0, false).withSprint(d > 4.5);
			return attack ? in.withAttack(true) : in;
		}
	}

	private static Inputs face(BotPlayer body, ServerPlayer t, int forward, int strafe, boolean use) {
		double dx = t.getX() - body.getX();
		double dz = t.getZ() - body.getZ();
		double dy = t.getEyeY() - body.getEyeY();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		float yawDelta = Mth.wrapDegrees(yaw - body.getYRot());
		float pitchDelta = pitch - body.getXRot();
		return new Inputs(yawDelta, pitchDelta, forward, strafe, false, false, false, false, use, 0);
	}
}

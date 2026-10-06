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
 * bot's human limits (turn speed, reaction) still apply to them.
 */
final class DrillPatterns {
	private DrillPatterns() {
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

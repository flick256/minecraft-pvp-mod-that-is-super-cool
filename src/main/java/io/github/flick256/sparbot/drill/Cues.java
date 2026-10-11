package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.drill.Drill;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cues of a drill's first stage: what to do right now, worked out every few ticks from your state and
 * the bot's (your charge, the distance, whether you are falling, when its next swing comes). They teach
 * the timing until you no longer need them; the later stages leave them out.
 */
final class Cues {
	private Cues() {
	}

	/** The cue for a drill's rep right now, or "" for none. */
	static String cue(Drill.Rep rep, ServerPlayer p, BotPlayer bot, @Nullable Object pattern, long now, long whiffAt, long stunAt) {
		float charge = p.getAttackStrengthScale(0.5F);
		double reach = reach(p, bot);
		return switch (rep) {
			case CHARGE -> charge >= 0.95F ? "SWING NOW" : bar(charge) + " wait for it";
			case REACH -> reach > 3.0 ? String.format(Locale.ROOT, "%.1f: step in", reach)
				: reach >= 2.6 ? String.format(Locale.ROOT, "%.1f: HIT NOW", reach) : String.format(Locale.ROOT, "%.1f: too close, back off", reach);
			case CRIT -> crit(p, charge);
			case SPRINT_HIT -> !p.isSprinting() ? "Let go of W, then press it again" : charge > 0.9F ? "Sprinting: HIT NOW" : "Sprinting: wait for the charge";
			case JUMP_RESET, BLOCK -> rhythm(rep, pattern, now);
			case AIM -> onTarget(p, bot) ? (charge > 0.9F ? "On target: SWING" : "On target: wait for the charge") : "Keep your crosshair on it";
			case PUNISH -> punish(pattern, now, whiffAt);
			case FIRST_HIT -> pattern instanceof DrillPatterns.Rusher r && !r.rushing(now) ? "Get ready: it is coming"
				: String.format(Locale.ROOT, "%.1f: hit at 3.0", reach);
			case COMBO -> charge > 0.9F ? (reach <= 3.0 ? "HIT" : "Follow them in") : "W-tap: let go of W, press it again";
			case TRADE -> bot.getAttackStrengthScale(0.5F) >= 0.9F ? "It is charged: stay out of reach" : "It is recharging: go in and hit";
			case STUN -> now - stunAt <= 20 && stunAt >= 0 ? "Shield down: SWORD, HIT NOW" : bot.isBlocking() ? "Shield up: hit it with your axe"
				: "Wait for its shield";
			case SHIELD_DEFENCE -> bot.getMainHandItem().is(ItemTags.AXES) ? "AXE: drop your shield" : p.isBlocking() ? "Blocking: good" : "Raise your shield";
			case POT -> p.getHealth() < 14 ? "Look down and throw a pot" : "";
			case WEB -> p.level().getBlockState(p.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB) ? "Caught: water at your feet, now"
				: "";
			case LAVA -> reach < 5 ? "Lava at its feet, then scoop it back" : "Get within 4 blocks";
			case ARROW -> p.isUsingItem() && p.getUseItem().is(Items.BOW) ? (p.getTicksUsingItem() >= 20 ? "Full draw: aim and let go" : "Keep drawing")
				: "Draw your bow";
			case RETOTEM -> p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? "" : "Totem to your offhand: F or the inventory";
			default -> "";
		};
	}

	/** Whether a cue is the moment to act (shown big in the middle of the screen as well). */
	static boolean urgent(String cue) {
		return cue.contains("NOW") || cue.startsWith("JUMP") || cue.contains("GO,") || cue.equals("SHIELD UP") || cue.startsWith("AXE")
			|| cue.endsWith("SWING") || cue.equals("HIT");
	}

	private static String crit(ServerPlayer p, float charge) {
		if (p.onGround()) {
			if (charge < 0.6F) {
				return "Wait for the charge";
			}
			return p.isSprinting() ? "Let go of W, then JUMP" : "JUMP";
		}
		if (p.fallDistance <= 0) {
			return "Rising: wait...";
		}
		return charge > 0.9F ? "Falling: HIT NOW" : "Falling: not charged yet";
	}

	/** The attacker's rhythm: when to jump (a jump reset) or have the shield up (blocking). */
	private static String rhythm(Drill.Rep rep, @Nullable Object pattern, long now) {
		if (!(pattern instanceof DrillPatterns.Attacker a)) {
			return "";
		}
		long left = a.ticksToSwing(now);
		if (rep == Drill.Rep.BLOCK) {
			return left <= 8 ? "SHIELD UP" : "Lower it and hit";
		}
		// The cue goes out a tick or two early: it reaches you a ping late.
		return left <= 2 ? "JUMP!" : left <= 12 ? "Get ready..." : "Watch its arm";
	}

	private static String punish(@Nullable Object pattern, long now, long whiffAt) {
		if (whiffAt >= 0 && now - whiffAt <= 12) {
			return "It missed: GO, HIT IT";
		}
		if (pattern instanceof DrillPatterns.Whiffer w) {
			return switch (w.state) {
				case APPROACH -> "Stay out of reach: let it swing first";
				case OPEN -> "Too late: back off";
				default -> "Wait, it is coming again";
			};
		}
		return "Watch its arm";
	}

	private static String bar(float charge) {
		int filled = Math.round(charge * 8);
		return "▮".repeat(filled) + "▯".repeat(8 - filled) + " " + Math.round(charge * 100) + "%";
	}

	/** Eye to the nearest point of the bot's hitbox: what reach is measured as. */
	static double reach(ServerPlayer p, BotPlayer bot) {
		Vec3 eye = p.getEyePosition();
		AABB box = bot.getBoundingBox();
		double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Whether the player's crosshair is on the bot within reach. */
	static boolean onTarget(ServerPlayer p, BotPlayer bot) {
		Vec3 eye = p.getEyePosition();
		Vec3 to = eye.add(p.getViewVector(1.0F).scale(3.0));
		return bot.getBoundingBox().clip(eye, to).isPresent() || bot.getBoundingBox().contains(eye);
	}
}

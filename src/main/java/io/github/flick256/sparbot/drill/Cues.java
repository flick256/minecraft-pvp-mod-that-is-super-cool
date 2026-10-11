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
	static String cue(Drill.Rep rep, ServerPlayer p, BotPlayer bot, @Nullable Object pattern, long now, long whiffAt, long stunAt, int target) {
		boolean w = p.getLastClientInput().forward();
		float charge = p.getAttackStrengthScale(0.5F);
		double reach = reach(p, bot);
		return switch (rep) {
			case CHARGE -> charge > 0.9F ? "SWING NOW" : bar(charge) + " wait for it";
			case REACH -> reach > 3.0 ? String.format(Locale.ROOT, "%.1f: step in", reach)
				: reach >= 2.6 ? (charge > 0.9F ? String.format(Locale.ROOT, "%.1f: HIT NOW", reach) : String.format(Locale.ROOT, "%.1f: hold here, charging", reach))
				: String.format(Locale.ROOT, "%.1f: too close, back off", reach);
			case CRIT -> crit(p, charge, w);
			case SPRINT_HIT -> !p.isSprinting() ? (w ? "Not sprinting: let go of W, press it again" : "Press W again to sprint")
				: charge > 0.9F ? (reach <= 3.0 ? "Sprinting: HIT, then let go of W" : "Sprinting: step in") : "Sprinting: wait for the charge";
			case JUMP_RESET -> !p.getLastClientInput().forward() || !p.isSprinting() ? "Sprint into it: hold W (and sprint)" : rhythm(rep, pattern, now);
			case BLOCK -> rhythm(rep, pattern, now);
			case AIM -> onTarget(p, bot) ? (charge > 0.9F ? "On target: SWING" : "On target: wait for the charge") : "Keep your crosshair on it";
			case PUNISH -> punish(pattern, now, whiffAt, target > 0 ? target : 10);
			case FIRST_HIT -> pattern instanceof DrillPatterns.Rusher r && !r.rushing(now) ? "Wait for it..."
				: reach > 3.4 ? String.format(Locale.ROOT, "%.1f: ready...", reach) : reach > 3.0 ? "SWING NOW" : String.format(Locale.ROOT, "%.1f: HIT", reach);
			case COMBO -> charge > 0.9F ? (reach <= 3.0 ? "HIT" : "Follow them in") : w ? "W-tap: let go of W, press it again" : "Back on W: follow them";
			case S_TAP -> sTap(p, charge, reach);
			case STUN -> stunAt >= 0 && now - stunAt <= 4 ? "HIT AGAIN NOW" : bot.isBlocking() ? "Shield up: axe it, then click again at once"
				: "Wait for its shield";
			case SHIELD_DEFENCE -> bot.getMainHandItem().is(ItemTags.AXES) ? "AXE: drop your shield" : p.isBlocking() ? "Blocking: good" : "Raise your shield";
			case POT -> p.getHealth() <= 14 ? (p.getXRot() > 60 ? "Looking down: THROW NOW" : "Hurt: look down and throw a pot")
				: "Pot only when you're down 3 hearts";
			case WEB -> p.level().getBlockState(p.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB) ? "Caught: water at your feet, now"
				: "";
			case LAVA -> reach < 5 ? "Lava at its feet, then scoop it back" : "Get within 4 blocks";
			case ARROW -> p.isUsingItem() && p.getUseItem().is(Items.BOW) ? (p.getTicksUsingItem() >= 20 ? "Full draw: aim and let go" : "Keep drawing")
				: "Draw your bow";
			case RETOTEM -> p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? "Totem ready: fight" : "TOTEM NOW: to your offhand (F or the inventory)";
			case ROD -> reach < 3.5 ? String.format(Locale.ROOT, "%.1f: too close for the rod, back off", reach)
				: reach <= 8 ? (p.getMainHandItem().is(Items.FISHING_ROD) ? String.format(Locale.ROOT, "%.1f: CAST NOW, a little above its head", reach)
					: String.format(Locale.ROOT, "%.1f: rod in hand, then cast", reach)) : String.format(Locale.ROOT, "%.1f: come within 8", reach);
			case CRYSTAL -> p.getMainHandItem().is(Items.END_CRYSTAL) ? "Crystal on the obsidian, then hit it" : p.getMainHandItem().is(Items.OBSIDIAN)
				? "Obsidian next to it" : "Obsidian, crystal, hit: one rhythm";
			case ANCHOR -> p.getMainHandItem().is(Items.RESPAWN_ANCHOR) ? "Anchor beside it" : p.getMainHandItem().is(Items.GLOWSTONE)
				? "Glowstone into the anchor, then swap" : "Charged? Click it with anything but glowstone";
			case CART -> p.getMainHandItem().is(Items.RAIL) ? "Rail at its feet" : p.getMainHandItem().is(Items.TNT_MINECART) ? "Cart on the rail"
				: "Fire at the cart NOW, then back off";
			case SMASH -> p.onGround() ? (p.getMainHandItem().is(Items.WIND_CHARGE) ? "Look down and throw it at your feet" : "Wind charge in hand for height")
				: p.fallDistance > 1.5 ? (reach <= 3 ? "Falling: SMASH NOW" : "Falling: steer over it") : "Rising: get above it";
			default -> "";
		};
	}

	/** Whether a cue is the moment to act (shown big in the middle of the screen as well). */
	static boolean urgent(String cue) {
		return cue.contains("NOW") || cue.startsWith("JUMP") || cue.contains("GO,") || cue.equals("SHIELD UP") || cue.startsWith("AXE")
			|| cue.endsWith("SWING") || cue.equals("HIT") || cue.startsWith("HIT") || cue.matches("[123]\\.\\.\\.") || cue.startsWith("TOTEM NOW") || cue.contains("CAST NOW") || cue.contains("SMASH NOW") || cue.contains("THROW NOW")
			|| cue.startsWith("Fire at the cart") || cue.startsWith("SWING");
	}

	private static String crit(ServerPlayer p, float charge, boolean w) {
		if (p.onGround()) {
			if (charge < 0.6F) {
				return "Wait for the charge";
			}
			return p.isSprinting() ? (w ? "Let go of W, then JUMP" : "Sprint dropping... JUMP") : "JUMP";
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
		// A count-in on its rhythm, so you jump on the beat rather than react late (a reaction takes 4 ticks or more).
		return left <= 1 ? "JUMP!" : left <= 9 ? "1..." : left <= 17 ? "2..." : left <= 25 ? "3..." : "Watch its arm";
	}

	private static String sTap(ServerPlayer p, float charge, double reach) {
		if (charge >= 0.9F) {
			return reach <= 3.0 ? "HIT, and tap S NOW" : "Charged: step in to 3 blocks";
		}
		return p.getLastClientInput().backward() ? "Tap, don't hold: back to W" : reach < 3.3 ? "Recharging: stay out of its reach" : "Wait for the charge";
	}

	private static String punish(@Nullable Object pattern, long now, long whiffAt, int window) {
		if (whiffAt >= 0 && now - whiffAt <= window) {
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

package io.github.flick256.sparbot.core.drill;

import io.github.flick256.sparbot.core.drill.Drill.Pattern;
import io.github.flick256.sparbot.core.drill.Drill.Rep;
import io.github.flick256.sparbot.core.drill.Drill.Stage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every skill drill, each taught in three stages (see {@link Drill}): learn it with cues against a
 * predictable bot, practise it without them, then do it under pressure against a bot that fights back.
 */
public final class Drills {
	private Drills() {
	}

	/** Tactics that aren't melee, switched off for a pure sword fight. */
	private static final List<String> UTILITY = List.of("ranged", "pearl", "kite", "pot", "refill", "buff", "mace", "spear", "crystal", "anchor", "cart",
		"defuse", "dodge", "lava", "web", "water", "wall", "backline", "cleanup", "boost", "breakweb", "retreat", "heal");

	/** Melee only, plus these tactics at these weights. */
	private static Map<String, Double> melee(Object... weights) {
		Map<String, Double> m = new HashMap<>();
		for (String t : UTILITY) {
			m.put(t, 0.0);
		}
		for (int i = 0; i < weights.length; i += 2) {
			m.put((String) weights[i], ((Number) weights[i + 1]).doubleValue());
		}
		return Map.copyOf(m);
	}

	/** Patterns are played at intermediate limits: quick enough to keep facing you, nothing more. */
	private static final String PATTERN_TIER = "intermediate";

	private static Stage learn(Pattern pattern, int reps, int seconds, double distance, double target) {
		return new Stage("Learn", PATTERN_TIER, pattern, Map.of(), List.of(), reps, seconds, distance, true, target);
	}

	private static Stage learn(Pattern pattern, int reps, int seconds, double distance) {
		return learn(pattern, reps, seconds, distance, 0);
	}

	private static Stage practice(Pattern pattern, int reps, int seconds, double distance, double target) {
		return new Stage("Practice", PATTERN_TIER, pattern, Map.of(), List.of(), reps, seconds, distance, false, target);
	}

	private static Stage practice(Pattern pattern, int reps, int seconds, double distance) {
		return practice(pattern, reps, seconds, distance, 0);
	}

	/** A fighting bot, with or without the cues. */
	private static Stage fight(String label, String profile, boolean cues, int reps, int seconds, double distance, double target, Map<String, Double> tactics,
		String... techniquesOff) {
		return new Stage(label, profile, Pattern.FIGHT, tactics, List.of(techniquesOff), reps, seconds, distance, cues, target);
	}

	private static Stage pressure(String profile, int reps, int seconds, double distance, double target, Map<String, Double> tactics, String... off) {
		String label = "Under pressure (" + Character.toUpperCase(profile.charAt(0)) + profile.substring(1) + " bot)";
		return fight(label, profile, false, reps, seconds, distance, target, tactics, off);
	}

	private static Stage pressure(String profile, int reps, int seconds, double distance, Map<String, Double> tactics, String... off) {
		return pressure(profile, reps, seconds, distance, 0, tactics, off);
	}

	private static final String SWORD = "sparbot_sword";

	public static final List<Drill> ALL = List.of(
		// Sword.
		new Drill("charge", "Full charge", "Sword", "Hitting only at full attack charge",
			"Wait for the cooldown under your crosshair to fill before each swing: a half-charged hit does a fraction of the damage and knockback.",
			List.of("A sword takes 12.5 ticks (0.6 s) to charge.", "Above 90% a hit is a full hit: full damage, crits, sprint knockback.",
				"Clicking faster doesn't hit faster: every click restarts the charge."),
			SWORD, SWORD, Rep.CHARGE, List.of(learn(Pattern.STILL, 8, 60, 3), practice(Pattern.STRAFER, 8, 60, 3),
				pressure("intermediate", 8, 90, 6, melee()))),
		new Drill("reach", "Edge of reach", "Sword", "Landing hits from as far away as you can",
			"Hit from just inside 3 blocks, so the opponent has to step in to hit you back. Back off between swings instead of standing on them.",
			List.of("A hit reaches 3 blocks from your eyes to their hitbox.", "Swing as they come into range, not once they are on you.",
				"Between swings, step back out (S-tap) instead of pressing forward."),
			SWORD, SWORD, Rep.REACH, List.of(learn(Pattern.PACE, 6, 60, 4, 2.6), practice(Pattern.STRAFER, 8, 60, 4, 2.6),
				pressure("intermediate", 8, 90, 6, 2.6, melee()))),
		new Drill("crits", "Critical hits", "Sword", "Falling crits at full charge",
			"Jump, and swing as you come down at full charge without sprinting: a crit does half as much again.",
			List.of("A crit needs: over 90% charge, falling (not rising), off the ground, not sprinting.",
				"Let go of W for a moment before the jump to drop your sprint.", "Jump when they can't hit back: while they fly from your hit, or recharge."),
			SWORD, SWORD, Rep.CRIT, List.of(learn(Pattern.STILL, 5, 60, 3), practice(Pattern.PACE, 6, 60, 3), pressure("intermediate", 5, 90, 6, melee()))),
		new Drill("wtap", "W-tap", "Sword", "Resetting your sprint between hits",
			"Let go of W (or tap S) for a moment after each hit and press it again: every hit is then a sprint hit with extra knockback.",
			List.of("A sprint hit knocks them further, then stops your sprint.", "Let go of W right after the hit, and press it again a moment later.",
				"Time it so you are sprinting again just as your charge is full."),
			SWORD, SWORD, Rep.SPRINT_HIT, List.of(learn(Pattern.STILL, 6, 60, 4), practice(Pattern.PACE, 8, 60, 4), pressure("intermediate", 8, 90, 6,
				melee()))),
		new Drill("jumpreset", "Jump reset", "Sword", "Taking less knockback when you are being comboed",
			"Jump the moment a hit lands on you: you take less knockback, stay in range, and can hit back.",
			List.of("Jump on the same tick their hit lands: a tick or two either way still works.", "Watch their arm: the hit comes as it swings.",
				"Too early and you are already in the air: the hit sends you flying further."),
			SWORD, SWORD, Rep.JUMP_RESET, List.of(learn(Pattern.ATTACKER, 5, 75, 3), practice(Pattern.ATTACKER, 8, 75, 3),
				pressure("advanced", 6, 90, 5, melee()))),
		new Drill("combo", "Combos", "Sword", "Hitting again and again without being hit back",
			"After a hit, follow the knockback and hit again as they land, at full charge and the edge of reach: W-tap to keep each hit a sprint hit.",
			List.of("After your hit they fly back: follow, don't stand still.", "W-tap so the next hit is a sprint hit too.",
				"Hit as they come back into 3 blocks, at full charge: they never get to swing."),
			SWORD, SWORD, Rep.COMBO, List.of(learn(Pattern.PACE, 3, 75, 4, 3), fight("Practice", "beginner", false, 3, 90, 6, 3, melee(), "jumpreset"),
				pressure("intermediate", 3, 120, 6, 4, melee()))),
		new Drill("aim", "Aim and tracking", "Sword", "Keeping your crosshair on a strafing opponent",
			"Track their hitbox with your mouse as they strafe, and only swing when your crosshair is on them.",
			List.of("Follow their body with small, steady mouse movements.", "Only swing with your crosshair on them: a miss wastes your charge.",
				"Every miss takes one good swing back."),
			SWORD, SWORD, Rep.AIM, List.of(learn(Pattern.STRAFER, 10, 60, 3), practice(Pattern.STRAFER, 15, 75, 3), pressure("intermediate", 12, 90, 6,
				melee()))),
		new Drill("punish", "Whiff punish", "Sword", "Hitting an opponent who just swung and missed",
			"When they swing and miss, their charge is gone for half a second: step in and hit before it comes back.",
			List.of("Stay just outside their reach and watch their arm.", "The moment a swing misses you, sprint in and hit.",
				"Rushing in before they swing walks you into a charged hit."),
			SWORD, SWORD, Rep.PUNISH, List.of(learn(Pattern.WHIFFER, 4, 75, 5, 10), practice(Pattern.WHIFFER, 6, 75, 5, 10),
				pressure("pro", 4, 120, 6, 10, melee()))),
		new Drill("firsthit", "First hit", "Sword", "Winning the opening hit of every exchange",
			"As you close in, the first hit decides the exchange: swing the moment they come into 3 blocks, a step before they can.",
			List.of("Sprint in, but watch the distance: hit at 3 blocks.", "A sprinting opponent closes half a block a tick: swing a little early.",
				"Strafing as you come in makes them miss."),
			SWORD, SWORD, Rep.FIRST_HIT, List.of(learn(Pattern.RUSHER, 4, 75, 9), practice(Pattern.RUSHER, 6, 75, 9), pressure("intermediate", 6, 120, 8,
				melee()))),
		new Drill("stap", "S-tap and spacing", "Sword", "Winning trades by keeping your distance",
			"Tap S just before and after you swing, so you hit at the edge of reach and are out of theirs when they swing back.",
			List.of("Every hit you land counts, every hit you take takes one back.", "Tap S as you swing, step back in as your charge returns.",
				"Don't chase a charged opponent: let them come to you."),
			SWORD, SWORD, Rep.TRADE, List.of(fight("Learn", "casual", true, 8, 75, 6, 0, melee()), fight("Practice", "intermediate", false, 10, 90, 6, 0,
				melee()), pressure("advanced", 12, 120, 6, melee()))),
		// Shield.
		new Drill("blockhit", "Block-hitting", "Shield", "Raising your shield between your swings",
			"Right-click your shield as soon as you have swung and drop it to swing again: their hits land on the shield, yours still land.",
			List.of("A shield blocks a quarter second after it goes up.", "Raise it right after your swing, lower it as your charge fills.",
				"Every hit you take takes one block back."),
			"sparbot_combat", "sparbot_combat", Rep.BLOCK, List.of(learn(Pattern.ATTACKER, 6, 60, 3), practice(Pattern.ATTACKER, 8, 75, 3),
				pressure("intermediate", 10, 90, 6, melee(), "shieldstun"))),
		new Drill("shieldstun", "Shield stun", "Shield", "Disabling a shield with an axe and punishing it",
			"Swap to your axe and hit their shield: it is down for a few seconds. Swap straight back to your sword and hit them before they back off.",
			List.of("An axe hit on a raised shield disables it for 5 seconds.", "Keep the axe next to your sword so the swap is one key.",
				"The follow-up hit only counts within a second."),
			"sparbot_combat", "sparbot_combat", Rep.STUN, List.of(learn(Pattern.SHIELD, 3, 60, 4), practice(Pattern.SHIELD, 4, 60, 4),
				pressure("intermediate", 3, 120, 6, melee()))),
		new Drill("shielddefence", "Shield defence", "Shield", "Blocking hits without losing your shield to an axe",
			"Hold your shield up against sword hits, but drop it (or strafe away) when they swap to an axe, so it is never disabled.",
			List.of("Watch their hand: a sword, block; an axe, drop the shield.", "A lost shield takes three good blocks back."),
			"sparbot_combat", "sparbot_combat", Rep.SHIELD_DEFENCE, List.of(fight("Learn", "intermediate", true, 6, 75, 6, 0, melee()),
				fight("Practice", "advanced", false, 8, 90, 6, 0, melee()), pressure("pro", 8, 120, 6, melee()))),
		// NoDebuff.
		new Drill("pots", "Health pots", "NoDebuff", "Throwing healing pots so all of the splash lands on you",
			"Look down (or at your feet while backing off) and throw: a pot that splashes right on you heals the full amount.",
			List.of("Look straight down, or at your feet while backing off.", "Throw between their hits, not while they are on you."),
			"sparbot_nodebuff", "sparbot_nodebuff", Rep.POT, List.of(learn(Pattern.STILL, 4, 60, 6), fight("Practice", "casual", false, 5, 90, 6, 0,
				melee("pot", 1)), pressure("advanced", 5, 120, 6, melee("pot", 1)))),
		// UHC.
		new Drill("webs", "Out of webs", "UHC", "Getting out of a cobweb fast",
			"Place water on the web (or break it with a sword) the moment you are caught: every second in a web is free hits and lava for them.",
			List.of("Keep a water bucket in your hotbar.", "Caught: look at your feet and place water, then pick it back up.",
				"No water: break the web with your sword."),
			"pvphq_uhc", "pvphq_uhc", Rep.WEB, List.of(fight("Learn", "intermediate", true, 2, 90, 5, 2.0, melee("web", 3)),
				fight("Practice", "advanced", false, 3, 120, 5, 1.5, melee("web", 3, "lava", 1)), pressure("pro", 3, 120, 5, 1.0, melee("web", 3, "lava", 2)))),
		new Drill("lava", "Lava", "UHC", "Setting your opponent on fire with lava and picking it back up",
			"Pour lava at their feet when they are stuck or eating, then scoop it back before it spreads to you.",
			List.of("Pour at their feet from 2 to 4 blocks.", "Scoop it back straight away: lava left lying burns you too."),
			"pvphq_uhc", "pvphq_uhc", Rep.LAVA, List.of(learn(Pattern.STILL, 2, 60, 5), practice(Pattern.PACE, 3, 75, 5), pressure("intermediate", 2, 120, 6,
				melee("water", 1, "cleanup", 1)))),
		new Drill("bow", "Bow shots", "UHC", "Landing arrows at range",
			"Draw fully, lead a moving target by its speed, and shoot when they eat or are stuck.",
			List.of("Draw for a full second: a half-drawn arrow drops short.", "Aim a little above them at range, and ahead of them when they move."),
			"sparbot_ranged", "sparbot_ranged", Rep.ARROW, List.of(learn(Pattern.STILL, 4, 60, 14), practice(Pattern.PACE, 5, 75, 14),
				pressure("intermediate", 4, 120, 14, Map.of("ranged", 2.0, "kite", 2.0)))),
		new Drill("rod", "Rod hits", "UHC", "Hooking your opponent with a fishing rod",
			"Cast at them from 4 to 8 blocks as they come in: a hook stops their sprint and sets up your first hit.",
			List.of("Cast from 4 to 8 blocks, a little above their head.", "Swap back to your sword straight after the hook."),
			"sparbot_drill_rod", "sparbot_drill_rod", Rep.ROD, List.of(learn(Pattern.STILL, 4, 60, 6), practice(Pattern.PACE, 5, 75, 7),
				pressure("casual", 4, 90, 8, melee()))),
		// Crystal.
		new Drill("crystals", "Crystal speed", "Crystal", "Placing and setting off crystals fast",
			"Obsidian next to them, crystal on it, hit it, and again: keep your crosshair on the obsidian and click in rhythm.",
			List.of("Obsidian beside them, a crystal on it, hit it.", "Keep your crosshair on the obsidian: place and hit in one rhythm."),
			"pvphq_crystal", "pvphq_crystal", Rep.CRYSTAL, List.of(learn(Pattern.STILL, 8, 60, 5), practice(Pattern.PACE, 8, 60, 5),
				pressure("intermediate", 6, 90, 6, Map.of()))),
		new Drill("anchors", "Anchor chain", "Crystal", "Anchor, glowstone and blast in one quick chain",
			"Place the anchor beside them, charge it with glowstone and use it with anything but glowstone in hand.",
			List.of("Anchor beside them, glowstone into it, then use it holding anything else.", "Stand where the blast won't reach you."),
			"pvphq_crystal", "pvphq_crystal", Rep.ANCHOR, List.of(learn(Pattern.STILL, 4, 75, 5), practice(Pattern.PACE, 4, 75, 5),
				pressure("intermediate", 3, 120, 6, Map.of()))),
		new Drill("retotem", "Re-totem", "Crystal", "A new totem in your offhand as soon as one pops",
			"When your totem pops, open your inventory and move the next one to your offhand (or keep one on a hotbar slot and swap with F).",
			List.of("Keep a totem on a hotbar slot: F swaps it to your offhand.", "Re-totem first, fight second."),
			"pvphq_crystal", "pvphq_crystal", Rep.RETOTEM, List.of(fight("Learn", "intermediate", true, 2, 120, 5, 2.0, Map.of()),
				fight("Practice", "advanced", false, 3, 120, 5, 1.5, Map.of()), pressure("pro", 3, 150, 5, 1.0, Map.of()))),
		// Cart.
		new Drill("carts", "Inst-cart", "Cart", "Rail, cart and fire in one quick motion",
			"Rail at their feet, TNT cart on it, then light it at once with a flame arrow or crossbow so they can't run.",
			List.of("Rail at their feet, the cart on it, then fire straight away.", "Back off a step before it goes."),
			"pvphq_cart_high", "pvphq_cart_high", Rep.CART, List.of(learn(Pattern.STILL, 3, 75, 6), practice(Pattern.PACE, 3, 75, 6),
				pressure("intermediate", 2, 120, 8, Map.of()))),
		// Mace.
		new Drill("mace", "Mace smash", "Mace", "Landing a mace hit from a fall",
			"Get height with a wind charge, then hit them with the mace as you come down: the further you fall, the harder it hits.",
			List.of("Wind charge at your feet for height.", "Hit them on the way down: the further the fall, the harder the smash."),
			"pvphq_mace", "pvphq_mace", Rep.SMASH, List.of(learn(Pattern.STILL, 3, 75, 5), practice(Pattern.PACE, 3, 75, 5), pressure("intermediate", 2, 120,
				6, Map.of("mace", 1.0)))));

	private static final Map<String, Drill> BY_ID = new LinkedHashMap<>();

	static {
		for (Drill d : ALL) {
			BY_ID.put(d.id(), d);
		}
	}

	public static Optional<Drill> get(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	/** The disciplines, in order. */
	public static List<String> disciplines() {
		return ALL.stream().map(Drill::discipline).distinct().toList();
	}
}

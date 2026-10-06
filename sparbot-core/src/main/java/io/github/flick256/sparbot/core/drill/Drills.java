package io.github.flick256.sparbot.core.drill;

import io.github.flick256.sparbot.core.drill.Drill.Metric;
import io.github.flick256.sparbot.core.drill.Drill.Pattern;
import io.github.flick256.sparbot.core.drill.Drill.Stage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every skill drill. Pass marks are first estimates from what the bot tiers do: a stage's mark is what the
 * tier it is named after manages, so gold means you do the skill as well as an advanced or pro bot does.
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

	private static Stage still(double pass, double distance) {
		return new Stage("Dummy", "beginner", Pattern.STILL, Map.of(), List.of(), pass, distance);
	}

	private static Stage pace(double pass, double distance) {
		return new Stage("Moving", "beginner", Pattern.PACE, Map.of(), List.of(), pass, distance);
	}

	private static Stage shield(double pass, double distance) {
		return new Stage("Shield up", "beginner", Pattern.SHIELD, Map.of(), List.of(), pass, distance);
	}

	private static Stage fight(String profile, double pass, double distance, Map<String, Double> tactics, String... techniquesOff) {
		String label = Character.toUpperCase(profile.charAt(0)) + profile.substring(1) + " bot";
		return new Stage(label, profile, Pattern.FIGHT, tactics, List.of(techniquesOff), pass, distance);
	}

	public static final List<Drill> ALL = List.of(
		// Sword.
		new Drill("charge", "Full charge", "Sword", "Hitting only at full attack charge",
			"Wait for the cooldown under your crosshair to fill before each swing: a half-charged hit does a fraction of the damage and knockback.",
			"pvphq_sword", "pvphq_sword", Metric.FULL_CHARGE, 30, List.of(still(0.9, 3), pace(0.85, 4), fight("intermediate", 0.75, 6, melee()))),
		new Drill("reach", "Edge of reach", "Sword", "Landing hits from as far away as you can",
			"Hit from just inside 3 blocks, so the opponent has to step in to hit you back. Back off between swings instead of standing on them.",
			"pvphq_sword", "pvphq_sword", Metric.REACH, 30, List.of(still(2.7, 4), pace(2.6, 5), fight("intermediate", 2.55, 6, melee()))),
		new Drill("crits", "Critical hits", "Sword", "Falling crits at full charge",
			"Jump, and swing as you come down at full charge without sprinting: a crit does half as much again.",
			"pvphq_sword", "pvphq_sword", Metric.CRITS, 30, List.of(still(0.7, 3), pace(0.55, 4), fight("intermediate", 0.35, 6, melee()))),
		new Drill("wtap", "W-tap", "Sword", "Resetting your sprint between hits",
			"Let go of W (or tap S) for a moment after each hit and press it again: every hit is then a sprint hit with extra knockback.",
			"pvphq_sword", "pvphq_sword", Metric.SPRINT_HITS, 30, List.of(still(0.7, 4), pace(0.6, 5), fight("intermediate", 0.5, 6, melee()))),
		new Drill("stap", "S-tap and spacing", "Sword", "Winning trades by keeping your distance",
			"Tap S just before and after you swing, so you hit at the edge of reach and are out of theirs when they swing back.",
			"pvphq_sword", "pvphq_sword", Metric.HIT_SHARE, 40, List.of(fight("casual", 0.6, 8, melee()), fight("intermediate", 0.55, 8, melee()),
				fight("advanced", 0.5, 8, melee()))),
		new Drill("combo", "Combos", "Sword", "Hitting again and again without being hit back",
			"After a hit, follow the knockback and hit again as they land, at full charge and the edge of reach: W-tap to keep each hit a sprint hit.",
			"pvphq_sword", "pvphq_sword", Metric.COMBO, 40, List.of(fight("beginner", 5, 6, melee(), "jumpreset"),
				fight("intermediate", 5, 6, melee(), "jumpreset"), fight("advanced", 4, 6, melee()))),
		new Drill("jumpreset", "Jump reset", "Sword", "Taking less knockback when you are being comboed",
			"Jump the moment a hit lands on you: you take less knockback, stay in range, and can hit back.",
			"pvphq_sword", "pvphq_sword", Metric.JUMP_RESETS, 40, List.of(fight("intermediate", 0.3, 5, melee()), fight("advanced", 0.4, 5, melee()),
				fight("pro", 0.5, 5, melee()))),
		new Drill("aim", "Aim and tracking", "Sword", "Keeping your crosshair on a strafing opponent",
			"Track their hitbox with your mouse as they strafe, and only swing when your crosshair is on them.",
			"pvphq_sword", "pvphq_sword", Metric.ACCURACY, 30, List.of(pace(0.8, 3), fight("intermediate", 0.65, 6, melee()),
				fight("pro", 0.55, 6, melee()))),
		// Shield.
		new Drill("blockhit", "Block-hitting", "Shield", "Raising your shield between your swings",
			"Right-click your shield as soon as you have swung and drop it to swing again: their hits land on the shield, yours still land.",
			"sparbot_combat", "sparbot_combat", Metric.BLOCKED, 40, List.of(fight("casual", 0.3, 6, melee(), "shieldstun"),
				fight("intermediate", 0.35, 6, melee(), "shieldstun"), fight("advanced", 0.4, 6, melee(), "shieldstun"))),
		new Drill("shieldstun", "Shield stun", "Shield", "Disabling a shield with an axe and punishing it",
			"Swap to your axe and hit their shield: it is down for a few seconds. Swap straight back to your sword and hit them before they back off.",
			"sparbot_combat", "sparbot_combat", Metric.STUN_FOLLOW_UPS, 40, List.of(shield(4, 4), fight("intermediate", 3, 6, melee()),
				fight("advanced", 2, 6, melee()))),
		new Drill("shielddefence", "Shield defence", "Shield", "Blocking hits without losing your shield to an axe",
			"Hold your shield up against sword hits, but drop it (or strafe away) when they swap to an axe, so it is never disabled.",
			"sparbot_combat", "sparbot_combat", Metric.SHIELD_DEFENCE, 40, List.of(fight("intermediate", 4, 6, melee()), fight("advanced", 5, 6, melee()),
				fight("pro", 6, 6, melee()))),
		// NoDebuff.
		new Drill("pots", "Health pots", "NoDebuff", "Throwing healing pots so all of the splash lands on you",
			"Look down (or at your feet while backing off) and throw: a pot that splashes right on you heals the full amount.",
			"sparbot_nodebuff", "sparbot_nodebuff", Metric.POT_ACCURACY, 40, List.of(still(0.85, 6), fight("casual", 0.8, 6, melee("pot", 1)),
				fight("advanced", 0.85, 6, melee("pot", 1)))),
		// UHC.
		new Drill("webs", "Out of webs", "UHC", "Getting out of a cobweb fast",
			"Place water on the web (or break it with a sword) the moment you are caught: every second in a web is free hits and lava for them.",
			"pvphq_uhc", "pvphq_uhc", Metric.WEB_ESCAPE, 45, List.of(fight("intermediate", 1.5, 5, melee("web", 3)),
				fight("advanced", 1.0, 5, melee("web", 3, "lava", 1)), fight("pro", 0.8, 5, melee("web", 3, "lava", 2)))),
		new Drill("lava", "Lava", "UHC", "Setting your opponent on fire with lava and picking it back up",
			"Pour lava at their feet when they are stuck or eating, then scoop it back before it spreads to you.",
			"pvphq_uhc", "pvphq_uhc", Metric.BURNING, 40, List.of(still(0.3, 5), pace(0.25, 5), fight("intermediate", 0.15, 6, melee("water", 1, "cleanup", 1)))),
		new Drill("bow", "Bow shots", "UHC", "Landing arrows at range",
			"Draw fully, lead a moving target by its speed, and shoot when they eat or are stuck.",
			"sparbot_ranged", "sparbot_ranged", Metric.BOW_HITS, 40, List.of(still(0.6, 14), pace(0.4, 14), fight("intermediate", 0.3, 14,
				Map.of("ranged", 2.0, "kite", 2.0)))),
		new Drill("rod", "Rod hits", "UHC", "Hooking your opponent with a fishing rod",
			"Cast at them from 4 to 8 blocks as they come in: a hook stops their sprint and sets up your first hit.",
			"sparbot_drill_rod", "sparbot_drill_rod", Metric.ROD_HOOKS, 40, List.of(still(0.6, 6), pace(0.45, 7), fight("casual", 0.35, 8, melee()))),
		// Crystal.
		new Drill("crystals", "Crystal speed", "Crystal", "Placing and setting off crystals fast",
			"Obsidian next to them, crystal on it, hit it, and again: keep your crosshair on the obsidian and click in rhythm.",
			"pvphq_crystal", "pvphq_crystal", Metric.CRYSTAL_BLASTS, 30, List.of(still(12, 5), pace(9, 5), fight("intermediate", 6, 6, Map.of()))),
		new Drill("anchors", "Anchor chain", "Crystal", "Anchor, glowstone and blast in one quick chain",
			"Place the anchor beside them, charge it with glowstone and use it with anything but glowstone in hand.",
			"pvphq_crystal", "pvphq_crystal", Metric.ANCHOR_BLASTS, 40, List.of(still(5, 5), pace(4, 5), fight("intermediate", 3, 6, Map.of()))),
		new Drill("retotem", "Re-totem", "Crystal", "A new totem in your offhand as soon as one pops",
			"When your totem pops, open your inventory and move the next one to your offhand (or keep one on a hotbar slot and swap with F).",
			"pvphq_crystal", "pvphq_crystal", Metric.RETOTEM, 45, List.of(fight("intermediate", 1.5, 5, Map.of()), fight("advanced", 1.0, 5, Map.of()),
				fight("pro", 0.7, 5, Map.of()))),
		// Cart.
		new Drill("carts", "Inst-cart", "Cart", "Rail, cart and fire in one quick motion",
			"Rail at their feet, TNT cart on it, then light it at once with a flame arrow or crossbow so they can't run.",
			"pvphq_cart_high", "pvphq_cart_high", Metric.CART_BLASTS, 45, List.of(still(4, 6), pace(3, 6), fight("intermediate", 2, 8, Map.of()))),
		// Mace.
		new Drill("mace", "Mace smash", "Mace", "Landing a mace hit from a fall",
			"Get height with a wind charge, then hit them with the mace as you come down: the further you fall, the harder it hits.",
			"pvphq_mace", "pvphq_mace", Metric.SMASHES, 40, List.of(still(4, 5), pace(3, 5), fight("intermediate", 2, 6, Map.of("mace", 1.0)))));

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

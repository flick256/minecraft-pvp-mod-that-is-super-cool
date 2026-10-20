package io.github.flick256.sparbot.core.rival;

import java.util.List;
import java.util.Optional;

/** Built-in career rivals. IDs deliberately point at bundled modes, profiles, and styles. */
public final class Rivals {
	public static final List<Rival> ALL = List.of(
		new Rival("iron_echo", "Iron Echo", "sword_duel", "casual", "balanced", "A steady first test.", "Consistent spacing", "Slow to punish retreats", 0, false),
		new Rival("redline", "Redline", "sword_duel", "advanced", "wtap_combo", "A combo-focused sword specialist.", "Clean resets", "Predictable entries", 1, false),
		new Rival("blade_dancer", "Blade Dancer", "sword_duel", "pro", "heavy_strafe", "A mobile combo specialist.", "Sharp side-to-side pressure", "Can overrun a safe reset", 2, false),
		new Rival("crown_edge", "Crown Edge", "sword_duel", "demon", "aggressive_rusher", "The sword circuit champion.", "Relentless follow-through", "Aggression can be baited", 3, true),
		new Rival("trailblazer", "Trailblazer", "uhc_duel", "intermediate", "balanced", "A measured UHC opener.", "Uses healing efficiently", "Lets opponents reset", 0, false),
		new Rival("redstone_ace", "Redstone Ace", "uhc_duel", "advanced", "pearl_aggro", "A confident UHC contender.", "Pearl conversions", "Can spend resources early", 1, false),
		new Rival("golden_hour", "Golden Hour", "uhc_duel", "pro", "defensive_shield", "A patient UHC resource manager.", "Conserves healing", "Slow to close a lead", 2, false),
		new Rival("last_stand", "Last Stand", "uhc_duel", "demon", "wtap_combo", "A tournament finalist who punishes resets.", "Converts small openings", "Predictable close-range pressure", 3, false),
		new Rival("swight", "Swight", "uhc_duel", "swight", "pearl_aggro", "The UHC circuit champion.", "Resource timing and pressure", "Commits to pearl aggression", 4, true),
		new Rival("longshot", "Longshot", "cart_duel", "intermediate", "kiter", "A patient cart duelist.", "Range control", "Gives up ground", 0, false),
		new Rival("trackstar", "Trackstar", "cart_duel", "pro", "heavy_strafe", "A fast line-change specialist.", "Repositions quickly", "Wide turns expose a chase", 1, false),
		new Rival("iron_horse", "Iron Horse", "cart_duel", "demon", "aggressive_rusher", "The cart circuit champion.", "Keeps pressure through turns", "Can be pulled off the ideal line", 2, true),
		new Rival("glassfang", "Glassfang", "crystal_duel", "advanced", "aggressive_rusher", "A high-pressure crystal rival.", "Fast explosive setups", "Overcommits to close trades", 0, false),
		new Rival("obsidian_sage", "Obsidian Sage", "crystal_duel", "pro", "defensive_shield", "A careful crystal tactician.", "Patient placement", "Slow to recover after a missed setup", 1, false),
		new Rival("cinder", "Cinder", "crystal_duel", "demon", "pearl_aggro", "The crystal circuit champion.", "Chains pressure across the arena", "Commits hard to pearl entries", 2, true),
		new Rival("bellwether", "Bellwether", "mace_duel", "advanced", "heavy_strafe", "Aerial mace specialist.", "Controls height changes", "Landing recovery creates an opening", 0, false),
		new Rival("skybreaker", "Skybreaker", "mace_duel", "pro", "aggressive_rusher", "Aerial pressure with decisive follow-ups.", "Converts vertical openings", "Can be baited into a low swing", 1, false),
		new Rival("anvil", "Anvil", "mace_duel", "demon", "wtap_combo", "The mace circuit champion.", "Punishes predictable landings", "Needs a clean approach", 2, true)
	);

	private Rivals() { }

	public static Optional<Rival> get(String id) {
		return ALL.stream().filter(r -> r.id().equals(id)).findFirst();
	}

	public static List<Rival> inMode(String mode) {
		return ALL.stream().filter(r -> r.mode().equals(mode)).toList();
	}
}

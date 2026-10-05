package io.github.flick256.sparbot.core.practice;

import java.util.List;

/**
 * Where everything is in the practice world: a flat desert (bedrock at the bottom, 127 blocks of
 * deepslate, stone, sandstone and sand above it) with a hub at the origin and one arena per kind of
 * fight around it, each far enough from the others that blasts and lava never reach a neighbour.
 */
public final class PracticeLayout {
	/** Bump when the hub or an arena changes: worlds built with an older version are rebuilt. */
	public static final int VERSION = 3;
	/** The top block of the flat desert (sand). */
	public static final int SURFACE = 63;
	/** Where players stand on it. */
	public static final int FLOOR = SURFACE + 1;
	/** Bedrock, the bottom of the world (an overworld-type dimension starts at y = -64). */
	public static final int BEDROCK = -64;
	/** Hub pads sit on this circle round the origin. */
	public static final int PAD_RADIUS = 9;
	/** The plaza's radius. */
	public static final int HUB_RADIUS = 14;

	/**
	 * One arena.
	 *
	 * @param radius half the side of the fighting area
	 * @param depth blocks below the surface the arena (and its resets) reach
	 * @param height blocks above the floor it reaches
	 * @param spawnOffset each side spawns this far from the centre, facing the other
	 * @param modes the game modes fought here
	 * @param standStart the grandstands' first row is this far from the centre (outside the arena, and for
	 *     blast modes out of a blast's reach)
	 * @param standBase the first row's seats are this many blocks above the floor
	 * @param standRows rows of seats, each one block further out and one higher
	 * @param round round (colosseum) stands rather than straight ones along the sides
	 */
	public record Site(String id, String displayName, int centerX, int centerZ, int radius, int depth, int height, int spawnOffset, List<String> modes,
		int standStart, int standBase, int standRows, boolean round) {
		public String arenaId() {
			return "practice_" + id;
		}

		public int minX() {
			return centerX - radius - 2;
		}

		public int maxX() {
			return centerX + radius + 2;
		}

		public int minZ() {
			return centerZ - radius - 2;
		}

		public int maxZ() {
			return centerZ + radius + 2;
		}

		public int minY() {
			return Math.max(BEDROCK, SURFACE - depth);
		}

		public int maxY() {
			return FLOOR + height;
		}

		public long volume() {
			return (long) (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
		}

		/** Where a player arriving from the hub lands: the viewing box at the top of the south stand, looking in. */
		public int lobbyZ() {
			return centerZ + standStart + standRows - 2;
		}

		/** The viewing box's floor (where players stand). */
		public int lobbyY() {
			return FLOOR + standBase + standRows;
		}

		/** The outer edge of the stands (their back wall). */
		public int standEnd() {
			return standStart + standRows;
		}
	}

	public static final Site SWORD = new Site("sword", "Sword court", 160, 0, 16, 4, 10, 5,
		List.of("sword_duel", "combat_duel", "benchmark", "nodebuff", "spear_duel"), 19, 1, 8, false);
	/** Colosseum stands rising from the top of the meadow's wall. */
	public static final Site UHC = new Site("uhc", "UHC meadow", 0, 160, 30, 12, 16, 12, List.of("uhc_duel"), 33, 7, 9, true);
	/**
	 * The open desert, diggable and blastable all the way down to bedrock. Its amphitheatre starts 12
	 * blocks out, beyond any crystal or anchor blast set off inside (a power-6 blast breaks blocks about 10
	 * blocks away at most).
	 */
	public static final Site CRYSTAL = new Site("crystal", "Crystal desert", -160, 0, 24, SURFACE - BEDROCK, 24, 7, List.of("crystal_duel"), 36, 1, 9,
		true);
	/** Its stands start 14 blocks out, in deepslate: a crossbow cart's blast (up to power 8.7) dies before them. */
	public static final Site CART = new Site("cart", "Cart field", 0, -160, 20, 6, 12, 6, List.of("cart_duel", "cart_low", "cart_high"), 34, 1, 8,
		false);
	public static final Site MACE = new Site("mace", "Mace and bow court", 160, 160, 20, 4, 24, 7, List.of("mace_duel", "ranged_duel"), 23, 1, 8, false);
	public static final List<Site> SITES = List.of(SWORD, UHC, CRYSTAL, CART, MACE);

	/** The Arcane Colosseum, the grand free-for-all stadium: its centre, north of the cart field. */
	public static final int GRAND_X = 0;
	public static final int GRAND_Z = -420;
	/** Half the side of the square it fits in, floating islands included. */
	public static final int GRAND_REACH = 88;
	/** Its pad in the hub, just south of the plaza. */
	public static final int[] GRAND_PAD = {0, 18};
	/** Where the hub pad lands you: on the south bridge, looking at the gate. */
	public static final int GRAND_ARRIVAL = 65;
	/** Pads back to the hub (distances south of the centre): on the south bridge, and in the south tunnel by the field. */
	public static final int GRAND_RETURN_BRIDGE = 67;
	public static final int GRAND_RETURN_TUNNEL = 36;

	private PracticeLayout() {
	}

	/** The arena a game mode is fought in (custom modes go to the sword court). */
	public static Site siteFor(String modeId) {
		for (Site site : SITES) {
			if (site.modes().contains(modeId)) {
				return site;
			}
		}
		return SWORD;
	}

	/** The hub pad for {@code site}: x and z on the pad circle, the sites in order round it. */
	public static int[] pad(Site site) {
		double angle = 2 * Math.PI * SITES.indexOf(site) / SITES.size();
		return new int[] {(int) Math.round(Math.sin(angle) * PAD_RADIUS), (int) Math.round(-Math.cos(angle) * PAD_RADIUS)};
	}

	/**
	 * The UHC meadow's rolling ground: 0-3 blocks above the floor at {@code dx, dz} from its centre, flat
	 * round both spawns so nobody starts on a slope.
	 */
	public static int meadowHeight(int dx, int dz) {
		double h = 1.3 + 1.1 * Math.sin(dx / 6.5) + 0.9 * Math.cos(dz / 8.0) + 0.6 * Math.sin((dx + dz) / 4.5);
		int height = (int) Math.max(0, Math.min(3, Math.round(h)));
		int off = UHC.spawnOffset();
		if (Math.abs(dx) <= 2 && (Math.abs(dz - off) <= 2 || Math.abs(dz + off) <= 2)) {
			return spawnHeight();
		}
		return height;
	}

	/** The meadow's height at both spawns. */
	public static int spawnHeight() {
		return 1;
	}

	/** A deterministic 0-999 value per block, for scattering flowers, grass and moss. */
	public static int scatter(int x, int z, int salt) {
		long h = x * 73856093L ^ z * 19349663L ^ salt * 83492791L;
		h ^= h >>> 13;
		h *= 0x5bd1e995L;
		h ^= h >>> 15;
		return (int) Math.floorMod(h, 1000L);
	}
}

package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.beam;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.candles;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.face;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.hanging;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.mix;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.slab;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.st;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.stair;
import static io.github.flick256.sparbot.practice.colosseum.RoomStyles.stairTop;

import io.github.flick256.sparbot.practice.colosseum.RoomStyles.Style;
import io.github.flick256.sparbot.practice.colosseum.RoomStyles.X;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The kinds of hall, each with its own furnishings: a head (an altar, a hearth, a stage, a forge...) at the end
 * without a door, or in the middle of each side wall of a hall with a door at each end; things along the side
 * walls; a body of tables, rows, cages, beds or beds of flowers; and their own lights where the usual
 * chandeliers won't do. Every kind reads its hall's {@link Style} for materials, colours and switches, so no two
 * halls of a kind are alike.
 */
final class RoomKinds {
	private RoomKinds() {
	}

	static final int COUNT = 28;
	static final String[][] NOUNS = {{"HALL", "BOARD", "TABLE"}, {"ARMOURY", "ARSENAL", "RACKS"}, {"LIBRARY", "STACKS", "SHELVES"},
		{"SHRINE", "ALTAR", "SANCTUM"}, {"YARD", "DOJO", "RING"}, {"COFFERS", "VAULT", "HOARD"}, {"LAB", "STILL", "ALEMBIC"},
		{"SALON", "PARLOUR", "LOUNGE"}, {"STORES", "GRANARY", "DEPOT"}, {"BATHS", "THERMAE", "POOL"}, {"GARDEN", "ARBOUR", "GROVE"},
		{"CONSORT", "CHORUS", "STAGE"}, {"CRYPT", "OSSUARY", "TOMBS"}, {"BUNKS", "BILLET", "BARRACK"}, {"CHARTS", "ATLAS", "SURVEY"},
		{"KITCHEN", "LARDER", "PANTRY"}, {"ORRERY", "SKYDOME", "ZENITH"}, {"FORGE", "SMITHY", "FOUNDRY"}, {"PENS", "KENNELS", "CAGES"},
		{"LOOMS", "MERCERY", "TAILORS"}, {"LEDGERS", "SCRIBES", "QUILLS"}, {"WARD", "MENDERS", "HOSPICE"}, {"LAURELS", "HONOURS", "TROPHY"},
		{"APIARY", "HIVES", "MEADERY"}, {"WAGERS", "TALLY", "ODDS"}, {"ENGINES", "WORKS", "GEARS"}, {"CELLAR", "GROTTO", "WARREN"},
		{"COUNCIL", "SENATE", "MOOT"}};

	private static final String[][][] INSCRIPTIONS = {
		{{"{P} RAISED", "A CUP HERE", "AFTER EVERY", "VICTORY"}, {"{N} FEASTS", "WERE HELD", "HERE FOR", "{P}"}},
		{{"{P} KEPT", "EVERY BLADE", "HERE SHARP", "IN YEAR {Y}"}, {"TAKE ONE.", "RETURN TWO.", "- {P},", "QUARTERMASTER"}},
		{{"{P} READ", "EVERY BOOK", "ON THESE", "SHELVES TWICE"}, {"QUIET,", "PLEASE.", "- {P},", "KEEPER"}},
		{{"{P} PRAYED", "HERE BEFORE", "EVERY BOUT", "OF YEAR {Y}"}, {"LIGHT A", "CANDLE FOR", "{P}, WHO", "FOUGHT ON"}},
		{{"{P} TRAINED", "HERE EVERY", "DAWN OF", "YEAR {Y}"}, {"{N} BOUTS", "BEGAN WITH", "ONE STEP", "HERE - {P}"}},
		{{"COUNTED BY", "{P}:", "{N} CROWNS,", "NOT ONE LESS"}, {"{P} SAID", "THE GOLD WAS", "NEVER THE", "PRIZE"}},
		{{"{P} BREWED", "THE FIRST", "HEALING", "DRAUGHT HERE"}, {"DO NOT", "DRINK THE", "GREEN ONE.", "- {P}"}},
		{{"{P} TOLD", "STORIES HERE", "UNTIL THE", "FIRE WENT OUT"}, {"REST HERE,", "CHAMPION.", "SAYS {P}.", ""}},
		{{"STOCK TAKEN", "BY {P}:", "{N} BARRELS,", "ONE MISSING"}, {"{P} LOST", "A RING HERE", "IN YEAR {Y}.", "STILL LOST"}},
		{{"{P} SWAM", "{N} LAPS", "AFTER EVERY", "WIN"}, {"THE WATER", "IS WARM.", "{P} SAYS", "IT HEALS"}},
		{{"{P} SOWED", "THE FIRST", "SEED HERE,", "YEAR {Y}"}, {"{P} TALKED", "TO THESE", "FLOWERS", "EVERY DAY"}},
		{{"{P} SANG", "FOR THE", "CHAMPIONS", "OF YEAR {Y}"}, {"ENCORE!", "CRIED {N}", "VOICES FOR", "{P}"}},
		{{"HERE LIES", "{P},", "WHO FELL IN", "BOUT {N}"}, {"{P} WAS", "NEVER", "BEATEN. ONLY", "OUTLIVED"}},
		{{"{P} SLEPT", "HERE {N}", "NIGHTS AND", "SNORED ALL"}, {"LIGHTS OUT", "AT THE BELL.", "- SERGEANT", "{P}"}},
		{{"SURVEYED BY", "{P}", "IN YEAR {Y}.", "MOSTLY RIGHT"}, {"{P} DREW", "EVERY STAIR", "AND ONE", "THAT ISN'T"}},
		{{"{P} COOKED", "{N} SUPPERS", "AND BURNED", "ONLY ONE"}, {"NO BLADES", "AT THE", "TABLE.", "- {P}"}},
		{{"{P} COUNTED", "{N} STARS", "AND ONE THAT", "FELL"}, {"{P} SAW", "ASTER FALL", "FROM THIS", "VERY ROOM"}},
		{{"{P} STRUCK", "THE ANVIL", "{N} TIMES", "A DAY"}, {"EVERY BLADE", "IN THE BOWL", "WAS MADE BY", "{P}"}},
		{{"{P} FED THE", "BEASTS AT", "DUSK. MIND", "YOUR HANDS"}, {"ONE CAGE", "IS EMPTY.", "{P} WON'T", "SAY WHY"}},
		{{"{P} SEWED", "EVERY", "CHAMPION'S", "COLOURS"}, {"{N} BANNERS", "WOVEN HERE", "BY {P}", ""}},
		{{"{P} WROTE", "DOWN EVERY", "BOUT EVER", "FOUGHT"}, {"BOUT {N}:", "{P} WON", "ON POINTS.", "(DISPUTED)"}},
		{{"{P} MENDED", "{N} BROKEN", "CHAMPIONS", "AND ONE CAT"}, {"WALK IT", "OFF, SAID", "{P}.", "THEY DID"}},
		{{"WON BY {P}", "AT THE GAMES", "OF YEAR {Y}", ""}, {"{P} GAVE", "EVERY PRIZE", "BACK BUT", "THIS ONE"}},
		{{"{P} KEPT", "THE BEES.", "THE BEES", "KEPT {P}"}, {"MEAD BY", "{P},", "BREWED YEAR", "{Y}"}},
		{{"{P} BET ON", "THE BLUE", "CORNER AND", "LOST IT ALL"}, {"ODDS ON", "VAELOR:", "STRUCK OFF", "- {P}"}},
		{{"{P} OILED", "THE ENGINES", "THAT RAISE", "THE GATES"}, {"{N} GEARS.", "{P} NAMED", "EVERY ONE", ""}},
		{{"{P} GREW", "THESE IN", "THE DARK.", "DON'T EAT ONE"}, {"{P} WENT", "DOWN HERE", "FOR A", "MUSHROOM..."}},
		{{"{P} SPOKE", "HERE ONCE", "AND WAS", "OUTVOTED"}, {"THE SEVEN", "MET HERE", "IN YEAR {Y}", "- {P}"}}};

	/** The hall's inscription: one of its kind's, about the one it is named for. */
	static String[] inscription(Style s) {
		String[] t = INSCRIPTIONS[s.kind][s.pick(21, INSCRIPTIONS[s.kind].length)];
		String p = s.person.toUpperCase(Locale.ROOT);
		String n = Integer.toString(10 + mix(s.seed + 9) % 990);
		String[] out = new String[4];
		for (int i = 0; i < 4; i++) {
			out[i] = t[i].replace("{P}", p).replace("{N}", n).replace("{Y}", Integer.toString(s.year));
		}
		return out;
	}

	/** Furnishes one column; returns whether the kind lights itself (no chandeliers). */
	static boolean build(X x) {
		return switch (x.s.kind) {
			case 0 -> feast(x);
			case 1 -> armoury(x);
			case 2 -> library(x);
			case 3 -> shrine(x);
			case 4 -> training(x);
			case 5 -> coffers(x);
			case 6 -> alchemy(x);
			case 7 -> salon(x);
			case 8 -> stores(x);
			case 9 -> baths(x);
			case 10 -> garden(x);
			case 11 -> music(x);
			case 12 -> crypt(x);
			case 13 -> barracks(x);
			case 14 -> charts(x);
			case 15 -> kitchen(x);
			case 16 -> orrery(x);
			case 17 -> forge(x);
			case 18 -> pens(x);
			case 19 -> looms(x);
			case 20 -> ledgers(x);
			case 21 -> ward(x);
			case 22 -> laurels(x);
			case 23 -> apiary(x);
			case 24 -> wagers(x);
			case 25 -> engines(x);
			case 26 -> cellar(x);
			default -> council(x);
		};
	}

	// --- Shared pieces ---

	private static final Block[] POTS = {Blocks.POTTED_POPPY, Blocks.POTTED_ALLIUM, Blocks.POTTED_FERN, Blocks.POTTED_AZALEA, Blocks.POTTED_BLUE_ORCHID,
		Blocks.POTTED_CORNFLOWER, Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_RED_TULIP, Blocks.POTTED_TORCHFLOWER, Blocks.POTTED_FLOWERING_AZALEA,
		Blocks.POTTED_CHERRY_SAPLING, Blocks.POTTED_BAMBOO};

	/** Something small on a table: candles, a pot, a flower, a lantern or a cake. */
	private static BlockState trinket(X x, int salt) {
		int roll = x.roll(salt) % 10;
		return switch (roll) {
			case 0, 1, 2 -> candles(Blocks.DYED_CANDLE.pick(roll == 0 ? x.s.a : x.s.b), 1 + x.roll(salt + 1) % 4);
			case 3 -> st(Blocks.DECORATED_POT);
			case 4, 5 -> st(POTS[x.roll(salt + 2) % POTS.length]);
			case 6 -> x.standingLamp();
			case 7 -> st(Blocks.CAKE);
			default -> st(Blocks.AIR);
		};
	}

	private static BlockState table(X x) {
		return slab(x.s.wood.slab(), true);
	}

	private static BlockState chair(X x, Direction backTo) {
		return stair(x.s.wood.stairs(), backTo);
	}

	private static BlockState chiseledShelf(X x, Direction f, int salt) {
		int bits = x.roll(salt) % 64;
		BlockState s = face(Blocks.CHISELED_BOOKSHELF, f);
		for (int i = 0; i < 6; i++) {
			s = s.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), (bits >> i & 1) == 1);
		}
		return s;
	}

	private static BlockState lit(Block b) {
		BlockState s = st(b);
		return s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, true) : s;
	}

	private static BlockState bulb(X x) {
		return st(Blocks.COPPER_BULB.waxed().pick(RoomStyles.WEATHER[x.s.pick(13, 4)])).setValue(CopperBulbBlock.LIT, true);
	}

	private static BlockState copper(X x) {
		return st(Blocks.CUT_COPPER.waxed().pick(RoomStyles.WEATHER[x.s.pick(13, 4)]));
	}

	private static BlockState cauldron(int level) {
		return st(Blocks.WATER_CAULDRON).setValue(LayeredCauldronBlock.LEVEL, level);
	}

	private static BlockState campfire(boolean soul) {
		return st(soul ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
	}

	private static BlockState leaves(Block b) {
		return st(b).setValue(LeavesBlock.PERSISTENT, true);
	}

	/** Whether the column is in a rectangle of the body: {@code u0 <= u < u1} and {@code v0 <= |v| < v1}. */
	private static boolean in(X x, double u0, double u1, double v0, double v1) {
		return x.u >= u0 && x.u < u1 && x.av >= v0 && x.av < v1;
	}

	/** A long run down the body: clear of the doors and the head. */
	private static boolean run(X x) {
		return x.front >= 3 && x.back >= 4.5;
	}

	// --- The kinds ---

	/**
	 * A feast hall: a long table set with candles, pots, cakes and flowers and chairs all down it (two tables
	 * either side of the walk in a through hall), a dais at the head with a high seat and a table across, ale
	 * barrels, banners.
	 */
	private static boolean feast(X x) {
		Style s = x.s;
		if (x.keep) {
			return false;
		}
		if (x.head() && !x.through) {
			x.floor(st(s.stone.alt()));
			if (x.a < 1) {
				x.put(0, Math.abs(x.b) < 0.5 ? stair(Blocks.DARK_OAK_STAIRS, x.toHead) : Math.abs(x.b) < 2.5 ? chair(x, x.toHead) : st(Blocks.BARREL));
				if (Math.abs(x.b) < 0.5) {
					x.put(1, st(Blocks.GOLD_BLOCK));
					x.put(2, face(Blocks.WALL_BANNER.pick(s.a), x.face));
				} else if (Math.abs(x.b) >= 2.5) {
					x.put(1, candles(Blocks.DYED_CANDLE.pick(s.b), 2));
				} else {
					x.put(3, face(Blocks.WALL_BANNER.pick(s.b), x.face));
				}
			} else if (x.a < 2 && Math.abs(x.b) < Math.min(3.5, x.hw - 1)) {
				x.put(0, table(x));
				x.put(1, Math.abs(x.b) < 0.5 ? st(Blocks.CAKE) : trinket(x, 5));
			} else if (x.a >= 3 && Math.abs(x.b) < 1.5 && x.a < 3.5) {
				x.put(0, slab(s.stone.slab(), false));
			}
			return false;
		}
		if (x.r.side) {
			if (Math.floorMod(x.iu, 5) == 1) {
				x.put(0, face(Blocks.BARREL, Direction.UP));
				x.put(1, s.bit(1) ? st(Blocks.BARREL) : candles(Blocks.CANDLE, 3));
			} else if (Math.floorMod(x.iu, 5) == 3) {
				x.put(3, face(Blocks.WALL_BANNER.pick(Math.floorMod(x.iu, 10) == 3 ? s.a : s.b), x.r.awayFromSide));
				x.put(0, st(POTS[s.pick(2, POTS.length)]));
			} else {
				x.wall();
			}
			return false;
		}
		double tableAt = x.through ? Math.max(2.0, x.hw / 2) : 0;
		double off = Math.abs(x.av - tableAt);
		boolean wide = !x.through && x.hw > 6;
		if (!run(x)) {
			return false;
		}
		if (off < (wide ? 1.0 : 0.5)) {
			x.put(0, table(x));
			if (x.roll(7) % 3 != 0) {
				x.put(1, trinket(x, 8));
			}
			if (x.flat && Math.floorMod(x.iu, 7) == 3) {
				x.put(1, x.standingLamp());
			}
			x.floor(st(s.wood.planks()));
		} else if (off < (wide ? 2.0 : 1.5) && Math.floorMod(x.iu, 2) == 0) {
			x.put(0, chair(x, x.v > 0 == x.av > tableAt ? x.r.towardSide : x.r.awayFromSide));
		} else if (!x.aisle && off < (wide ? 2.0 : 1.5)) {
			x.put(0, st(Blocks.CARPET.pick(s.a)));
		}
		return false;
	}

	/** An armoury: racks of lances, swords and shields on the walls, rows of anvils and grindstones, and a forge breast at the head. */
	private static boolean armoury(X x) {
		Style s = x.s;
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			int ab = (int) Math.abs(Math.round(x.b));
			if (x.a < 1 && ab <= 1) {
				x.rise(1, st(Blocks.BRICKS));
				x.put(0, ab == 0 ? lit(Blocks.BLAST_FURNACE) : st(Blocks.BRICKS));
				if (ab == 0) {
					x.put(2, face(Blocks.WALL_BANNER.pick(DyeColor.BLACK), x.face));
				}
			} else if (x.a < 1 && ab <= 3) {
				x.put(0, ab == 2 ? st(Blocks.SMITHING_TABLE) : face(Blocks.GRINDSTONE, x.face));
				x.put(2, face(Blocks.TRIPWIRE_HOOK, x.face));
			} else if (x.a < 2 && ab == 0) {
				x.put(0, face(Blocks.ANVIL, x.r.towardSide));
			} else if (x.a < 2 && ab == 1) {
				x.put(0, cauldron(3));
			}
			return false;
		}
		if (x.r.side) {
			switch (Math.floorMod(x.iu, 4)) {
				case 0 -> {
					// A lance rack.
					x.put(0, st(s.stone.wall()));
					x.put(1, face(Blocks.LIGHTNING_ROD.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)]), Direction.UP));
					x.put(2, face(Blocks.END_ROD, Direction.UP));
				}
				case 1 -> {
					x.put(0, face(Blocks.BARREL, Direction.UP));
					x.put(2, face(Blocks.TRIPWIRE_HOOK, x.r.awayFromSide));
				}
				case 2 -> {
					// A shield on the wall over a chest.
					x.put(0, face(Blocks.CHEST, x.r.awayFromSide));
					x.put(2, face(Blocks.WALL_BANNER.pick(Math.floorMod(x.iu, 8) == 2 ? s.a : DyeColor.BLACK), x.r.awayFromSide));
				}
				default -> {
					// A suit of armour on its stand.
					x.put(0, st(Blocks.POLISHED_BLACKSTONE_WALL));
					x.put(1, st(Blocks.IRON_BLOCK));
					x.put(2, st(Blocks.SKELETON_SKULL));
				}
			}
			return false;
		}
		if (!run(x) || x.aisle) {
			return false;
		}
		double row = x.through ? Math.max(2.0, x.hw / 2) : Math.max(1.5, x.hw / 2);
		if (Math.abs(x.av - row) < 0.5) {
			int i = Math.floorMod(x.iu, 4);
			x.put(0, i == 0 ? face(Blocks.ANVIL, x.r.in) : i == 1 ? st(Blocks.CHIPPED_ANVIL) : i == 2 ? face(Blocks.GRINDSTONE, x.r.towardSide)
				: st(Blocks.SMITHING_TABLE));
		} else if (!x.through && x.av < 0.5 && Math.floorMod(x.iu, 6) == 3) {
			// A weapon stand: a post with a lance and a skull for a helm.
			x.put(0, st(s.wood.fence()));
			x.put(1, st(s.wood.fence()));
			x.put(2, st(Blocks.SKELETON_SKULL));
		}
		return false;
	}

	/**
	 * A library: shelves floor to ceiling down both walls (chiseled ones half full), ladders against them,
	 * reading tables with candles and lecterns, a globe, and at the head a great reading desk.
	 */
	private static boolean library(X x) {
		Style s = x.s;
		if (x.keep) {
			x.floor(st(s.wood.planks()));
			return false;
		}
		x.floor(Math.floorMod(x.iu / 2 + x.iv, 2) == 0 ? st(s.wood.planks()) : st(s.wood.stripped()));
		if (x.r.side || x.a < 1 && !x.through) {
			Direction f = x.r.side ? x.r.awayFromSide : x.face;
			for (int y = 0; y <= x.top - x.feet; y++) {
				x.put(y, (y + x.iu + x.iv) % 4 == 0 ? chiseledShelf(x, f, 11 + y) : y == 3 && Math.floorMod(x.iu, 6) == 0 ? lit(Blocks.REDSTONE_LAMP)
					: st(Blocks.BOOKSHELF));
			}
			return false;
		}
		if (x.fs < 2 && Math.floorMod(x.iu, 7) == 3 && !x.head()) {
			x.rise(0, face(Blocks.LADDER, x.r.awayFromSide).setValue(LadderBlock.FACING, x.r.awayFromSide));
			return false;
		}
		if (x.head() && !x.through) {
			if (x.a < 2 && Math.abs(x.b) < 0.5) {
				x.put(0, face(Blocks.LECTERN, x.face));
			} else if (x.a < 2 && Math.abs(x.b) < 2) {
				x.put(0, table(x));
				x.put(1, candles(Blocks.CANDLE, 4));
			} else if (x.a < 3 && Math.abs(x.b) < 0.5) {
				x.put(0, chair(x, x.face));
			}
			return false;
		}
		if (x.aisle || !run(x)) {
			x.put(0, x.aisle ? st(Blocks.CARPET.pick(s.a)) : st(Blocks.AIR));
			return false;
		}
		double row = x.through ? Math.max(2.0, x.hw / 2) : 0;
		double off = Math.abs(x.av - row);
		int i = Math.floorMod(x.iu, 6);
		if (off < 0.5 && i >= 1 && i <= 3) {
			x.put(0, table(x));
			x.put(1, i == 2 ? candles(Blocks.CANDLE, 2) : x.roll(4) % 2 == 0 ? st(Blocks.CARPET.pick(DyeColor.WHITE)) : st(Blocks.AIR));
		} else if (off < 1.5 && (i == 1 || i == 3)) {
			x.put(0, chair(x, x.av > row ? x.r.towardSide : x.r.awayFromSide));
		} else if (off < 0.5 && i == 5 && !x.through) {
			x.put(0, Math.floorMod(x.iu, 12) == 5 ? st(Blocks.ENCHANTING_TABLE) : st(s.wood.fence()));
			if (Math.floorMod(x.iu, 12) != 5) {
				x.put(1, st(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE)));
			}
		}
		return false;
	}

	/** A shrine: kneelers before an altar of the star, a reredos of carved stone behind it, coloured glass and candles down the walls. */
	private static boolean shrine(X x) {
		Style s = x.s;
		x.floor(x.av < 1.0 ? st(Blocks.AMETHYST_BLOCK) : Math.floorMod(x.iu, 3) == 0 ? st(s.stone.accent()) : st(Blocks.CALCITE));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 2.5) {
				x.rise(0, ab < 0.5 ? st(Blocks.QUARTZ_PILLAR) : st(Blocks.CHISELED_QUARTZ_BLOCK));
				if (ab < 0.5) {
					x.fill(1, 3, st(Blocks.STAINED_GLASS.pick(s.a)));
					x.put(4, st(Blocks.SEA_LANTERN));
				}
			} else if (x.a < 2 && ab < 1.5) {
				x.put(0, ab < 0.5 ? st(Blocks.CRYING_OBSIDIAN) : st(Blocks.AMETHYST_BLOCK));
				x.put(1, ab < 0.5 ? face(Blocks.END_ROD, Direction.UP) : candles(Blocks.DYED_CANDLE.pick(s.a), 4));
			} else if (x.a < 2 && ab < 2.5) {
				x.put(0, st(Blocks.AMETHYST_CLUSTER));
			}
			return false;
		}
		if (x.r.side) {
			if (Math.floorMod(x.iu, 3) == 0) {
				x.rise(0, st(Blocks.QUARTZ_PILLAR));
			} else {
				x.fill(1, Math.min(4, x.top - x.feet - 1), st(Blocks.STAINED_GLASS.pick(Math.floorMod(x.iu, 3) == 1 ? s.a : s.b)));
				x.put(0, candles(Blocks.DYED_CANDLE.pick(s.b), 1 + Math.floorMod(x.iu, 4)));
			}
			return false;
		}
		if (!x.through && run(x) && Math.floorMod(x.iu, 3) == 0 && x.av >= 1.2 && x.fs >= 1.5) {
			x.put(0, stair(Blocks.PURPUR_STAIRS, x.face));
		} else if (x.through && !x.aisle && Math.floorMod(x.iu, 4) == 2 && x.fs >= 1.5) {
			x.put(0, st(s.stone.wall()));
			x.put(1, candles(Blocks.DYED_CANDLE.pick(s.a), 3));
		}
		return false;
	}

	/**
	 * A training yard: a sparring ring roped with posts in the middle (or lanes down a through hall), straw
	 * dummies, targets at the head, weights and a rack of practice blades.
	 */
	private static boolean training(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.CUT_SANDSTONE));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			if (x.a < 1) {
				x.put(0, st(Blocks.HAY_BLOCK));
				x.put(1, Math.floorMod(x.ib, 2) == 0 ? st(Blocks.TARGET) : st(Blocks.HAY_BLOCK));
				x.put(2, Math.floorMod(x.ib, 2) == 0 ? st(Blocks.TARGET) : st(Blocks.AIR));
			} else if (x.a >= 2.5 && x.a < 3.5 && Math.abs(x.b) < x.hw - 1) {
				x.floor(st(Blocks.DYED_TERRACOTTA.pick(DyeColor.RED)));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 5);
			if (i == 0) {
				x.put(0, face(Blocks.BARREL, Direction.UP));
				x.put(1, face(Blocks.END_ROD, Direction.UP));
			} else if (i == 2) {
				x.put(0, st(Blocks.CHIPPED_ANVIL));
			} else if (i == 3) {
				x.sideSign(2, DyeColor.BLACK, false, "RULE " + (1 + Math.floorMod(x.iu / 5, 7)) + ":", RULES[Math.floorMod(x.iu / 5 + s.index, RULES.length)][0],
					RULES[Math.floorMod(x.iu / 5 + s.index, RULES.length)][1], "");
			} else {
				x.wall();
			}
			return false;
		}
		if (!x.through) {
			double dm = Math.hypot(x.mid(), x.v);
			double ring = Math.min(x.hw - 1.2, 4.5);
			if (Math.abs(dm - ring) < 0.5) {
				x.floor(st(Blocks.DYED_TERRACOTTA.pick(s.a)));
				if (Math.floorMod(x.iu + x.iv, 3) == 0) {
					x.put(0, st(s.wood.fence()));
				}
				return false;
			}
			if (dm < ring - 0.5) {
				x.floor(st(Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE)));
				return false;
			}
		}
		if (run(x) && !x.aisle && Math.floorMod(x.iu, 4) == 2 && Math.abs(x.fs - 2) < 0.6) {
			// A straw dummy (a skull for a head: a pumpkin would try to make a golem).
			x.put(0, st(s.wood.fence()));
			x.put(1, st(Blocks.HAY_BLOCK));
			x.put(2, st(Blocks.SKELETON_SKULL));
		}
		return false;
	}

	static final String[][] RULES = {{"no blow after", "the horn"}, {"no bout", "unfought"}, {"bow to the", "crowd"}, {"keep your", "guard up"},
		{"eat before", "you fight"}, {"never turn", "your back"}, {"the floor", "is honest"}};

	/** The coffers: gold heaped behind bars, counting tables with scales, chests, and at the head a vault door. */
	private static boolean coffers(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(Blocks.GILDED_BLACKSTONE) : st(Blocks.POLISHED_BLACKSTONE));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 2.5) {
				x.rise(0, st(Blocks.IRON_BLOCK));
				if (ab < 1.5) {
					x.fill(0, 2, st(Blocks.IRON_BARS));
				}
				if (ab < 0.5) {
					x.put(1, st(Blocks.GOLD_BLOCK));
				}
			} else if (x.a < 2 && ab >= 1.5 && ab < 3) {
				x.put(0, face(Blocks.CHEST, x.face));
			}
			return false;
		}
		if (x.fs < 2 && !x.aisle) {
			if (x.fs >= 1 && Math.floorMod(x.iu, 5) != 2) {
				x.fill(0, 2, st(Blocks.IRON_BARS));
			} else if (x.fs < 1) {
				int heap = x.roll(5) % 10;
				x.put(0, heap < 4 ? st(Blocks.GOLD_BLOCK) : heap < 6 ? st(Blocks.RAW_GOLD_BLOCK) : heap < 8 ? face(Blocks.CHEST, x.r.awayFromSide)
					: st(Blocks.BARREL));
				if (heap < 2) {
					x.put(1, st(Blocks.GOLD_BLOCK));
				}
			}
			return false;
		}
		if (run(x) && !x.aisle && Math.floorMod(x.iu, 5) == 2 && x.fs >= 2) {
			x.put(0, st(Blocks.POLISHED_DEEPSLATE));
			x.put(1, x.roll(9) % 2 == 0 ? candles(Blocks.DYED_CANDLE.pick(DyeColor.YELLOW), 2) : st(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE));
		} else if (run(x) && !x.aisle && Math.floorMod(x.iu, 5) == 3 && x.fs >= 2 && x.fs < 3) {
			x.put(0, chair(x, x.r.towardSide));
		}
		return false;
	}

	/**
	 * An alchemist's laboratory: benches of brewing stands, phials (candles) and potted fungus under shelves, a
	 * cauldron bubbling over a soul fire, and at the head a great copper still lit by bulbs.
	 */
	private static boolean alchemy(X x) {
		Style s = x.s;
		x.floor(x.roll(3) % 5 == 0 ? st(Blocks.MOSSY_STONE_BRICKS) : st(s.stone.block()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 0.5) {
				x.fill(0, 2, copper(x));
				x.put(3, face(Blocks.LIGHTNING_ROD.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)]), Direction.UP));
			} else if (x.a < 1 && ab < 2.5) {
				x.put(0, ab < 1.5 ? st(Blocks.COPPER_GRATE.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)])) : bulb(x));
				x.put(1, ab < 1.5 ? st(Blocks.TINTED_GLASS) : face(Blocks.BREWING_STAND, x.face));
				x.put(2, ab < 1.5 ? bulb(x) : st(Blocks.AIR));
			} else if (x.a < 2 && ab < 0.5) {
				x.put(0, campfire(true));
				x.put(1, cauldron(3));
			}
			return false;
		}
		if (x.r.side) {
			x.put(0, st(Blocks.POLISHED_DEEPSLATE));
			int i = Math.floorMod(x.iu, 4);
			x.put(1, i == 0 ? st(Blocks.BREWING_STAND) : i == 1 ? st(x.roll(2) % 2 == 0 ? Blocks.POTTED_CRIMSON_FUNGUS : Blocks.POTTED_WARPED_FUNGUS)
				: i == 2 ? candles(Blocks.DYED_CANDLE.pick(x.roll(6) % 2 == 0 ? s.a : DyeColor.LIME), 1 + x.roll(7) % 4) : st(Blocks.DECORATED_POT));
			x.put(3, face(s.wood.shelf(), x.r.awayFromSide));
			return false;
		}
		if (run(x) && !x.aisle && !x.through && x.av < 0.5 && Math.floorMod(x.iu, 8) == 4) {
			x.put(0, campfire(true));
			x.put(1, cauldron(2));
			x.r.fill(x.feet + 3, x.top, st(Blocks.IRON_CHAIN));
		} else if (run(x) && !x.aisle && Math.floorMod(x.iu, 8) == 0 && x.fs >= 2 && x.fs < 3) {
			x.put(0, table(x));
			x.put(1, st(Blocks.BREWING_STAND));
		}
		return false;
	}

	/**
	 * A salon: a fireplace with a mantel at the head and armchairs before it, a rug in the hall's colours,
	 * sofas along the walls with lamps and plants between, a little table and a piano.
	 */
	private static boolean salon(X x) {
		Style s = x.s;
		x.floor(st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 1.5) {
				x.rise(1, st(Blocks.BRICKS));
				x.put(0, ab < 0.5 ? campfire(false) : st(Blocks.BRICKS));
				x.put(1, ab < 0.5 ? st(Blocks.IRON_BARS) : st(Blocks.BRICKS));
				x.put(2, slab(s.stone.slab(), true));
				x.put(3, ab < 0.5 ? face(Blocks.WALL_BANNER.pick(s.a), x.face) : candles(Blocks.CANDLE, 2));
			} else if (x.a < 1 && ab < 2.5) {
				x.put(0, st(POTS[s.pick(5, POTS.length)]));
			} else if (x.a >= 2 && x.a < 3 && ab >= 0.5 && ab < 2.5 && !x.through) {
				x.put(0, chair(x, x.face));
			} else if (x.a >= 1 && x.a < 2 && ab < 1.5) {
				x.put(0, st(Blocks.CARPET.pick(s.b)));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 5);
			if (i == 0) {
				x.put(0, st(s.wood.fence()));
				x.put(1, x.standingLamp());
			} else if (i == 4) {
				x.put(0, st(POTS[x.roll(3) % POTS.length]));
				x.put(2, face(s.wood.shelf(), x.r.awayFromSide));
			} else {
				x.put(0, chair(x, x.r.towardSide));
			}
			return false;
		}
		if (x.aisle) {
			x.put(0, st(Blocks.CARPET.pick(s.b)));
			return false;
		}
		if (run(x) && x.fs >= 1.5) {
			boolean border = x.fs < 2.5 || x.front < 4 || x.back < 5.5;
			x.put(0, st(Blocks.CARPET.pick(border ? s.b : s.a)));
			if (!x.through && x.av < 0.5 && Math.floorMod(x.iu, 9) == 4) {
				x.put(0, table(x));
				x.put(1, trinket(x, 2));
			} else if (!x.through && s.bit(3) && x.av < 1.5 && Math.floorMod(x.iu, 9) == 7) {
				// A piano: note blocks with a bench.
				x.put(0, st(Blocks.NOTE_BLOCK));
				x.put(1, slab(Blocks.DARK_OAK_SLAB, false));
			}
		}
		return false;
	}

	/** Stores: shelves of barrels and crates up the walls, aisles of stacked crates, sacks and chests, ladders, and a pyramid of casks at the head. */
	private static boolean stores(X x) {
		Style s = x.s;
		x.floor(st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			int h = (int) Math.max(0, Math.min(3, x.headDepth() - x.a));
			for (int y = 0; y < h; y++) {
				x.put(y, (y + x.ib) % 3 == 0 ? st(Blocks.HAY_BLOCK) : face(Blocks.BARREL, x.face));
			}
			return false;
		}
		if (x.r.side) {
			for (int y = 0; y <= Math.min(3, x.top - x.feet); y++) {
				int roll = x.roll(10 + y) % 6;
				x.put(y, roll < 3 ? face(Blocks.BARREL, x.r.awayFromSide) : roll == 3 ? st(Blocks.HAY_BLOCK) : roll == 4 ? st(Blocks.DRIED_KELP_BLOCK)
					: st(s.wood.planks()));
			}
			return false;
		}
		if (x.aisle || !run(x) || x.av < 1.2) {
			return false;
		}
		int i = Math.floorMod(x.iu, 5);
		if ((i == 1 || i == 2) && x.fs >= 1.5) {
			int h = 1 + x.roll(12) % 3;
			for (int y = 0; y < h; y++) {
				int roll = x.roll(13 + y) % 5;
				x.put(y, roll < 2 ? st(Blocks.BARREL) : roll == 2 ? face(Blocks.CHEST, x.r.in) : roll == 3 ? st(Blocks.COMPOSTER) : st(Blocks.HAY_BLOCK));
			}
		} else if (i == 4 && x.fs < 2) {
			x.rise(0, face(Blocks.LADDER, x.r.awayFromSide));
		}
		return false;
	}

	/**
	 * Baths: a raised pool lit from beneath, its rim of smooth stone all round (the water never gets out),
	 * columns, benches, ferns and towels, and a fountain figure at the head. A through hall has two long tubs
	 * either side of the walk when it is wide enough, and steaming cauldrons when it is not.
	 */
	private static boolean baths(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(Blocks.PRISMARINE_BRICKS) : st(Blocks.SMOOTH_QUARTZ));
		if (x.keep) {
			return false;
		}
		if (pool(x, 0)) {
			x.floor(Math.floorMod(x.iu, 3) == 0 ? st(Blocks.SEA_LANTERN) : st(Blocks.PRISMARINE_BRICKS));
			x.put(0, st(Blocks.WATER));
			return false;
		}
		if (pool(x, 1.1)) {
			x.put(0, st(Blocks.SMOOTH_QUARTZ));
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 0.5) {
				x.put(0, st(Blocks.PRISMARINE_BRICKS));
				x.put(1, st(Blocks.PRISMARINE_WALL));
				x.put(2, st(Blocks.SEA_LANTERN));
				x.put(3, st(Blocks.PRISMARINE_WALL));
			} else if (x.a < 1 && ab < 2.5) {
				x.put(0, stair(Blocks.QUARTZ_STAIRS, x.toHead));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 4);
			if (i == 0) {
				x.rise(0, st(Blocks.QUARTZ_PILLAR));
			} else if (i == 2) {
				x.put(0, st(Blocks.POTTED_FERN));
			} else {
				x.put(0, stair(Blocks.QUARTZ_STAIRS, x.r.towardSide));
				x.put(1, s.bit(5) ? st(Blocks.CARPET.pick(DyeColor.WHITE)) : st(Blocks.AIR));
			}
			return false;
		}
		if (x.through && !x.aisle && run(x) && Math.floorMod(x.iu, 5) == 2 && x.fs >= 1.5) {
			x.put(0, campfire(false));
			x.put(1, cauldron(3));
		}
		return false;
	}

	/** Where the baths' water lies (grown by {@code grow} for its rim): never near a door, a wall or the walk. */
	private static boolean pool(X x, double grow) {
		if (x.through) {
			if (x.midHw < 6) {
				return false;
			}
			return x.av >= 2.2 - grow && x.av < x.hw - 1.6 + grow && x.u > 4 - grow && x.depth - x.u > 4 - grow;
		}
		return x.av < x.hw - 2.0 + grow && x.front > 4 - grow && x.back > 4.5 - grow;
	}

	/**
	 * A conservatory garden: grass and moss underfoot, flowerbeds, little trees, azaleas, a fountain in a
	 * mossy basin, spore blossoms and glowing vines hanging from the ceiling.
	 */
	private static boolean garden(X x) {
		Style s = x.s;
		x.floor(x.av < 1.0 ? st(Blocks.MOSSY_STONE_BRICKS) : x.roll(1) % 3 == 0 ? st(Blocks.MOSS_BLOCK) : st(Blocks.GRASS_BLOCK));
		if (x.flat && x.roll(2) % 9 == 0 && x.fs > 1) {
			if (x.roll(3) % 2 == 0) {
				x.r.put(x.top, st(Blocks.SPORE_BLOSSOM));
			} else {
				x.r.put(x.top, st(Blocks.CAVE_VINES_PLANT).setValue(CaveVines.BERRIES, true));
				x.r.put(x.top - 1, st(Blocks.CAVE_VINES).setValue(CaveVines.BERRIES, true));
			}
		}
		if (x.keep || x.av < 1.0 && x.through) {
			return false;
		}
		if (!x.through) {
			// The fountain, in the middle: a spout in a ring of water in a mossy rim.
			double dm = Math.hypot(x.mid(), x.v);
			if (dm < 0.6) {
				x.put(0, st(Blocks.MOSSY_STONE_BRICK_WALL));
				x.put(1, st(Blocks.SEA_LANTERN));
				return false;
			}
			if (dm < 1.6) {
				x.put(0, st(Blocks.WATER));
				x.floor(st(Blocks.MOSSY_STONE_BRICKS));
				return false;
			}
			if (dm < 2.8) {
				x.put(0, st(Blocks.MOSSY_STONE_BRICKS));
				return false;
			}
		}
		if (x.head()) {
			// A flowering tree at the head.
			double t = Math.hypot(x.a - 1.5, x.b);
			if (t < 0.6) {
				x.fill(0, 2, st(s.wood.log()));
				x.put(3, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			} else if (t < 2.2) {
				x.put(3, leaves(x.roll(4) % 2 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
				if (t < 1.6) {
					x.put(4, leaves(Blocks.AZALEA_LEAVES));
				}
				x.put(0, x.roll(5) % 3 == 0 ? st(Blocks.FERN) : st(Blocks.AIR));
			}
			return false;
		}
		if (x.r.side) {
			x.put(0, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			if (Math.floorMod(x.iu, 3) == 0) {
				x.put(1, leaves(Blocks.AZALEA_LEAVES));
			}
			return false;
		}
		if (x.av >= 1.0 && run(x)) {
			int roll = x.roll(6) % 12;
			Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
				Blocks.LILY_OF_THE_VALLEY, Blocks.FERN, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.BUSH};
			x.put(0, roll < 9 || x.fs > 2 ? st(flowers[roll]) : st(Blocks.AIR));
		}
		return false;
	}

	/**
	 * A music hall: a stage at the head with note blocks, a bell, a jukebox and a conductor's stand before
	 * curtains, rows of seats facing it, and hangings down the walls.
	 */
	private static boolean music(X x) {
		Style s = x.s;
		x.floor(st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1) {
				x.rise(0, st(Blocks.WOOL.pick(s.a)));
				return false;
			}
			if (x.through) {
				x.put(0, x.a < 2 ? st(Blocks.NOTE_BLOCK) : st(Blocks.AIR));
				return false;
			}
			x.put(0, st(s.wood.planks()));
			if (x.a < 2) {
				x.put(1, Math.floorMod(x.ib, 2) == 0 ? st(Blocks.NOTE_BLOCK) : ab < 0.5 ? st(Blocks.JUKEBOX) : st(Blocks.AIR));
			} else if (x.a < 3 && ab < 0.5) {
				x.put(1, st(Blocks.BELL));
			} else if (x.a >= 3 && ab < 0.5) {
				x.put(0, stair(s.wood.stairs(), x.toHead));
				x.put(1, face(Blocks.LECTERN, x.face));
			} else if (x.a >= 3) {
				x.put(0, slab(s.wood.slab(), false));
			}
			return false;
		}
		if (x.r.side) {
			x.fill(1, Math.min(4, x.top - x.feet - 1), st(Blocks.WOOL.pick(Math.floorMod(x.iu, 2) == 0 ? s.b : DyeColor.BLACK)));
			if (Math.floorMod(x.iu, 4) == 0) {
				x.put(0, x.standingLamp());
			}
			return false;
		}
		if (x.aisle || x.av < 1.0) {
			x.put(0, st(Blocks.CARPET.pick(s.a)));
			return false;
		}
		if (run(x) && Math.floorMod(x.iu, 2) == 0) {
			x.put(0, stair(s.wood.stairs(), x.through ? x.r.towardSide : x.face));
		}
		return false;
	}

	/** A crypt: burial niches in the walls with skulls and candles, tombs in rows, bone and cobweb, and a great sarcophagus at the head. */
	private static boolean crypt(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu, 4) == 0 ? st(Blocks.CHISELED_TUFF) : x.roll(1) % 4 == 0 ? st(Blocks.CRACKED_STONE_BRICKS) : st(Blocks.TUFF_BRICKS));
		if (x.flat && x.fs < 1.5 && Math.floorMod(x.iu, 5) == 2) {
			x.r.put(x.top, st(Blocks.COBWEB));
		}
		if (x.keep) {
			return true;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 2.5) {
				x.rise(0, st(Blocks.TUFF_BRICKS));
				x.put(2, ab < 0.5 ? face(Blocks.WALL_BANNER.pick(DyeColor.BLACK), x.face) : st(Blocks.TUFF_BRICKS));
				if (ab >= 1.5) {
					x.put(1, face(Blocks.SOUL_WALL_TORCH, x.face));
				}
			} else if (x.a < 3 && ab < 0.6) {
				x.put(0, st(Blocks.POLISHED_DEEPSLATE));
				x.put(1, x.a < 2 ? st(Blocks.SKELETON_SKULL) : face(Blocks.LIGHTNING_ROD.waxed().pick(RoomStyles.WEATHER[3]), x.toHead));
			} else if (x.a < 3 && ab < 1.6) {
				x.put(0, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, x.r.towardSide));
				x.put(1, candles(Blocks.DYED_CANDLE.pick(DyeColor.WHITE), 2));
			}
			return true;
		}
		if (x.r.side) {
			x.rise(0, st(Blocks.TUFF_BRICKS));
			if (Math.floorMod(x.iu, 2) == 0) {
				x.put(1, face(Blocks.SKELETON_WALL_SKULL, x.r.awayFromSide));
				x.put(3, Math.floorMod(x.iu, 4) == 0 ? candles(Blocks.CANDLE, 2) : face(Blocks.SOUL_WALL_TORCH, x.r.awayFromSide));
			}
			return true;
		}
		if (x.aisle || !run(x)) {
			if (Math.floorMod(x.iu, 6) == 3 && x.av < 0.5) {
				x.hang(hanging(Blocks.SOUL_LANTERN));
			}
			return true;
		}
		double row = x.through ? Math.max(2.0, x.hw / 2) : Math.max(1.5, x.hw / 2);
		if (Math.abs(x.av - row) < 1.0 && Math.floorMod(x.iu, 4) != 0) {
			x.put(0, Math.floorMod(x.iu, 4) == 2 ? st(Blocks.CHISELED_POLISHED_BLACKSTONE) : st(Blocks.POLISHED_BLACKSTONE_BRICKS));
			x.put(1, Math.floorMod(x.iu, 4) == 2 ? candles(Blocks.CANDLE, 1 + x.roll(4) % 3) : slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
		} else if (x.roll(8) % 13 == 0) {
			x.put(0, x.roll(9) % 2 == 0 ? st(Blocks.BONE_BLOCK) : st(Blocks.SKELETON_SKULL));
		} else if (Math.floorMod(x.iu, 6) == 3 && x.av < 0.5) {
			x.hang(hanging(Blocks.SOUL_LANTERN));
		}
		return true;
	}

	/** Barracks: bunks two high down both walls with blankets, foot lockers, a weapon rack, and a sergeant's desk at the head. */
	private static boolean barracks(X x) {
		Style s = x.s;
		x.floor(st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head() && !x.through) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 0.5) {
				x.put(0, st(Blocks.CARTOGRAPHY_TABLE));
				x.put(2, face(Blocks.WALL_BANNER.pick(s.a), x.face));
			} else if (x.a < 1 && ab < 2.5) {
				x.put(0, face(Blocks.CHEST, x.face));
				x.put(1, ab < 1.5 ? candles(Blocks.CANDLE, 1) : st(Blocks.AIR));
			} else if (x.a < 2 && ab < 0.5) {
				x.put(0, face(Blocks.LECTERN, x.face));
			}
			return false;
		}
		boolean bunk = x.fs < 2 && Math.floorMod(x.iu, 3) != 0 && run(x);
		if (bunk) {
			x.put(0, st(Blocks.WOOL.pick(s.a)));
			x.put(1, st(Blocks.CARPET.pick(DyeColor.WHITE)));
			x.put(2, slab(s.wood.slab(), true));
			x.put(3, st(Blocks.CARPET.pick(s.b)));
			return false;
		}
		if (x.fs < 2 && Math.floorMod(x.iu, 3) == 0 && run(x)) {
			x.fill(0, 3, st(s.wood.log()));
			if (x.r.side) {
				x.put(4, x.sconceOrAir());
			}
			return false;
		}
		if (x.fs < 3 && x.fs >= 2 && Math.floorMod(x.iu, 3) == 1 && run(x) && !x.aisle) {
			x.put(0, face(Blocks.BARREL, Direction.UP));
		} else if (x.r.side) {
			x.wall();
		}
		return false;
	}

	/** A map room: the colosseum laid out in the floor in rings of colour, cartography tables and looms, and a great globe at the head. */
	private static boolean charts(X x) {
		Style s = x.s;
		double rr = Math.hypot(x.mid(), x.v) * (x.through ? 1.0 : 1.4);
		BlockState mosaic = rr < 1 ? st(Blocks.GOLD_BLOCK) : rr < 2.5 ? st(Blocks.CONCRETE.pick(DyeColor.PURPLE)) : rr < 3.5 ? st(Blocks.CONCRETE.pick(DyeColor.BLACK))
			: rr < 6 ? st(Blocks.CONCRETE.pick(DyeColor.GRAY)) : rr < 7 ? st(Blocks.CONCRETE.pick(DyeColor.LIGHT_BLUE)) : rr < 8 ? st(Blocks.CONCRETE.pick(DyeColor.PINK))
			: st(s.wood.planks());
		x.floor(mosaic);
		if (x.keep) {
			return false;
		}
		if (x.head() && !x.through) {
			double g = Math.hypot(x.a - 1.6, x.b);
			if (g < 0.6) {
				x.put(0, st(s.wood.fence()));
				x.put(1, st(s.wood.fence()));
			}
			if (g < 1.6) {
				x.put(2, g < 0.6 ? st(Blocks.CONCRETE.pick(DyeColor.GREEN)) : st(x.roll(3) % 3 == 0 ? Blocks.CONCRETE.pick(DyeColor.LIME) : Blocks.CONCRETE.pick(DyeColor.BLUE)));
				x.put(3, g < 1.0 ? st(Blocks.CONCRETE.pick(DyeColor.BLUE)) : st(Blocks.AIR));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 4);
			if (i == 1) {
				x.put(0, st(Blocks.CARTOGRAPHY_TABLE));
			} else if (i == 2) {
				x.put(0, face(Blocks.LOOM, x.r.awayFromSide));
			} else if (i == 3) {
				x.put(3, face(Blocks.WALL_BANNER.pick(DyeColor.LIGHT_BLUE), x.r.awayFromSide));
				x.put(0, face(Blocks.CHEST, x.r.awayFromSide));
			} else {
				x.wall();
			}
		}
		return false;
	}

	/**
	 * A kitchen: counters of ovens, smokers and barrels along the walls with pots and candles, a prep island down
	 * the middle with melons and cakes, cauldrons, and a great hearth with a spit at the head.
	 */
	private static boolean kitchen(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(Blocks.BRICKS) : st(Blocks.MUD_BRICKS));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 1.5) {
				x.rise(2, st(Blocks.BRICKS));
				x.put(0, ab < 0.5 ? campfire(false) : st(Blocks.BRICKS));
				x.put(1, ab < 0.5 ? st(Blocks.IRON_CHAIN) : lit(Blocks.SMOKER));
			} else if (x.a < 1 && ab < 3) {
				x.put(0, lit(ab < 2 ? Blocks.FURNACE : Blocks.BLAST_FURNACE));
				x.put(1, st(Blocks.DECORATED_POT));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 4);
			x.put(0, i == 0 ? face(Blocks.SMOKER, x.r.awayFromSide) : i == 1 ? face(Blocks.BARREL, Direction.UP) : i == 2 ? st(Blocks.POLISHED_DEEPSLATE)
				: cauldron(2));
			x.put(1, i == 2 ? trinket(x, 4) : i == 1 ? st(Blocks.DECORATED_POT) : st(Blocks.AIR));
			x.put(3, face(s.wood.shelf(), x.r.awayFromSide));
			return false;
		}
		if (!x.through && run(x) && x.av < 0.5) {
			x.put(0, st(Blocks.POLISHED_DEEPSLATE));
			int roll = x.roll(5) % 5;
			x.put(1, roll == 0 ? st(Blocks.MELON) : roll == 1 ? st(Blocks.CAKE) : roll == 2 ? st(Blocks.HAY_BLOCK) : trinket(x, 6));
		} else if (x.through && !x.aisle && run(x) && Math.floorMod(x.iu, 6) == 3 && x.fs >= 1.5) {
			x.put(0, st(Blocks.BARREL));
			x.put(1, st(Blocks.CAKE));
		}
		return false;
	}

	/**
	 * An orrery: a floor like the night sky, a brass sun hung in the middle with the planets on chains round it,
	 * telescopes along the walls, star charts, and a great telescope at the head. Its own dim lights.
	 */
	private static boolean orrery(X x) {
		Style s = x.s;
		x.floor(x.roll(1) % 13 == 0 ? st(Blocks.SEA_LANTERN) : x.roll(2) % 17 == 0 ? st(Blocks.CONCRETE.pick(DyeColor.WHITE))
			: st(Blocks.CONCRETE.pick(DyeColor.BLACK)));
		if (x.flat && x.roll(3) % 23 == 0 && x.air(x.top - x.feet)) {
			x.r.put(x.top, face(Blocks.END_ROD, Direction.DOWN));
		}
		if (x.keep) {
			return true;
		}
		double dm = Math.hypot(x.mid(), x.v);
		int lowest = x.flat ? x.feet + 4 : Math.max(x.feet + 4, x.top - 4);
		if (!x.through && dm < 0.6) {
			x.r.fill(lowest + 1, x.top, st(Blocks.IRON_CHAIN));
			x.r.put(lowest, st(Blocks.OCHRE_FROGLIGHT));
			x.put(0, st(Blocks.POLISHED_DEEPSLATE));
			x.put(1, st(Blocks.IRON_CHAIN));
			return true;
		}
		if (!x.through && Math.abs(dm - 2.5) < 0.5 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(x.v, x.mid()))), 90) < 12) {
			Block[] planets = {Blocks.LAPIS_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.IRON_BLOCK, Blocks.AMETHYST_BLOCK};
			x.r.fill(lowest + 1, x.top, st(Blocks.IRON_CHAIN));
			x.r.put(lowest, st(planets[x.roll(4) % 4]));
			return true;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 2 && ab < 0.5) {
				x.put(0, st(s.wood.fence()));
				x.put(1, copper(x));
				x.put(2, face(Blocks.LIGHTNING_ROD.waxed().pick(RoomStyles.WEATHER[s.pick(5, 4)]), x.toHead));
			} else if (x.a < 1 && ab < 3) {
				x.put(3, face(Blocks.WALL_BANNER.pick(DyeColor.BLUE), x.face));
				x.put(0, candles(Blocks.DYED_CANDLE.pick(DyeColor.LIGHT_BLUE), 2));
			}
			return true;
		}
		if (x.r.side) {
			if (Math.floorMod(x.iu, 5) == 2) {
				x.put(0, st(s.wood.fence()));
				x.put(1, face(Blocks.LIGHTNING_ROD.waxed().pick(RoomStyles.WEATHER[s.pick(5, 4)]), x.r.towardSide));
				x.put(3, x.sconceOrAir());
			} else if (Math.floorMod(x.iu, 5) == 4) {
				x.put(2, face(Blocks.WALL_BANNER.pick(Math.floorMod(x.iu, 10) == 4 ? DyeColor.BLUE : DyeColor.BLACK), x.r.awayFromSide));
				x.put(0, candles(Blocks.DYED_CANDLE.pick(DyeColor.WHITE), 1 + x.roll(6) % 3));
			}
			return true;
		}
		if (Math.floorMod(x.iu, 7) == 3 && x.av < 0.5 && x.through) {
			x.hang(hanging(Blocks.SOUL_LANTERN));
		}
		return true;
	}

	/** A forge: a crucible of lava in a brick furnace at the head, anvils and quench troughs, coal, iron and tool hooks on the walls. */
	private static boolean forge(X x) {
		Style s = x.s;
		x.floor(x.roll(1) % 4 == 0 ? st(Blocks.COBBLED_DEEPSLATE) : st(s.stone.block()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 2.5) {
				x.rise(0, st(Blocks.BRICKS));
				if (ab < 0.5) {
					x.put(0, st(Blocks.LAVA_CAULDRON));
					x.put(1, st(Blocks.IRON_BARS));
				} else if (ab < 1.5) {
					x.put(0, lit(Blocks.BLAST_FURNACE));
				}
			} else if (x.a < 2 && ab < 0.5) {
				x.put(0, face(Blocks.ANVIL, x.r.towardSide));
			} else if (x.a < 2 && ab < 1.5) {
				x.put(0, cauldron(3));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 5);
			if (i == 0) {
				x.put(0, st(Blocks.COAL_BLOCK));
				x.put(1, st(Blocks.COAL_BLOCK));
			} else if (i == 1) {
				x.put(0, st(Blocks.IRON_BLOCK));
				x.put(2, face(Blocks.TRIPWIRE_HOOK, x.r.awayFromSide));
			} else if (i == 3) {
				x.put(0, st(Blocks.RAW_IRON_BLOCK));
				x.put(2, face(Blocks.TRIPWIRE_HOOK, x.r.awayFromSide));
				x.put(3, x.sconceOrAir());
			} else {
				x.wall();
			}
			return false;
		}
		if (!x.aisle && run(x) && Math.floorMod(x.iu, 5) == 2 && x.fs >= 1.5 && x.fs < 3) {
			x.put(0, x.roll(4) % 2 == 0 ? st(Blocks.DAMAGED_ANVIL) : face(Blocks.ANVIL, x.r.in));
		} else if (!x.aisle && run(x) && Math.floorMod(x.iu, 5) == 4 && x.fs >= 1.5 && x.fs < 3) {
			x.put(0, cauldron(3));
		} else if (Math.floorMod(x.iu, 5) == 0 && x.av < 0.5 && x.flat) {
			x.r.fill(x.feet + 4, x.top, st(Blocks.IRON_CHAIN));
		}
		return false;
	}

	/** The beast pens: barred cages along both walls with hay, bones and troughs (one broken open), feed sacks, and a great cage at the head. */
	private static boolean pens(X x) {
		Style s = x.s;
		x.floor(x.roll(1) % 3 == 0 ? st(Blocks.COARSE_DIRT) : st(Blocks.PACKED_MUD));
		if (x.keep) {
			return false;
		}
		boolean broken = Math.floorMod(x.iu / 4 + s.index, 5) == 0;
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a >= x.headDepth() - 1) {
				if (!(broken && ab < 1.0)) {
					x.fill(0, Math.min(3, x.top - x.feet), st(Blocks.IRON_BARS));
				}
			} else if (x.a < 1) {
				x.put(0, x.roll(2) % 3 == 0 ? st(Blocks.BONE_BLOCK) : st(Blocks.HAY_BLOCK));
				x.put(1, x.roll(3) % 4 == 0 ? st(Blocks.SKELETON_SKULL) : st(Blocks.AIR));
			}
			return false;
		}
		if (x.fs < 3 && run(x) && !x.aisle) {
			if (x.fs >= 2) {
				boolean gate = Math.floorMod(x.iu, 4) == 0;
				if (!gate && !(broken && Math.floorMod(x.iu, 4) == 2)) {
					x.fill(0, 2, st(Blocks.IRON_BARS));
				}
				if (gate) {
					x.put(2, st(Blocks.IRON_BARS));
				}
			} else if (x.r.side) {
				int roll = x.roll(4) % 6;
				x.put(0, roll < 2 ? st(Blocks.HAY_BLOCK) : roll == 2 ? cauldron(1) : roll == 3 ? st(Blocks.BONE_BLOCK) : st(Blocks.AIR));
				if (Math.floorMod(x.iu, 4) == 2 && x.flat) {
					x.r.fill(x.top - 2, x.top, st(Blocks.IRON_CHAIN));
				}
			} else if (x.roll(5) % 5 == 0) {
				x.put(0, st(Blocks.COBWEB));
			}
			return false;
		}
		if (!x.aisle && run(x) && x.roll(6) % 9 == 0) {
			x.put(0, st(Blocks.HAY_BLOCK));
		}
		return false;
	}

	/** The looms: looms and bolts of wool along the walls, dress forms in the hall's colours, cutting tables, dye vats and a wall of banners at the head. */
	private static boolean looms(X x) {
		Style s = x.s;
		x.floor(st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1) {
				x.put(3, face(Blocks.WALL_BANNER.pick(DyeColor.byId(Math.floorMod(x.ib * 5 + s.index, 16))), x.face));
				x.put(0, ab < 1.5 ? cauldron(3) : st(POTS[Math.floorMod(x.ib, POTS.length)]));
			} else if (x.a < 2 && ab < 0.5) {
				x.put(0, st(Blocks.CARPET.pick(s.b)));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 4);
			if (i == 0) {
				x.put(0, face(Blocks.LOOM, x.r.awayFromSide));
			} else {
				for (int y = 0; y < 3; y++) {
					x.put(y, st(Blocks.WOOL.pick(DyeColor.byId(Math.floorMod(x.iu * 3 + y + s.index, 16)))));
				}
			}
			return false;
		}
		if (x.aisle || !run(x)) {
			return false;
		}
		double row = x.through ? Math.max(2.0, x.hw / 2) : Math.max(1.5, x.hw / 2);
		if (Math.abs(x.av - row) < 0.5 && Math.floorMod(x.iu, 3) == 1) {
			// A dress form.
			x.put(0, st(s.wood.fence()));
			x.put(1, st(Blocks.WOOL.pick(Math.floorMod(x.iu, 2) == 0 ? s.a : s.b)));
			x.put(2, st(Blocks.CARPET.pick(s.b)));
		} else if (!x.through && x.av < 0.5 && Math.floorMod(x.iu, 5) != 0) {
			x.put(0, table(x));
			x.put(1, st(Blocks.CARPET.pick(Math.floorMod(x.iu, 2) == 0 ? s.a : DyeColor.WHITE)));
		}
		return false;
	}

	/** The ledgers: chiseled shelves to the ceiling, rows of writing desks with ink and paper and stools, and a great ledger on a dais at the head. */
	private static boolean ledgers(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(s.wood.planks()) : st(s.stone.alt()));
		if (x.keep) {
			return false;
		}
		if (x.r.side) {
			for (int y = 0; y <= x.top - x.feet; y++) {
				x.put(y, chiseledShelf(x, x.r.awayFromSide, 20 + y));
			}
			return false;
		}
		if (x.head() && !x.through) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 1.5) {
				x.fill(0, 2, st(Blocks.GLASS_PANE));
				x.put(3, candles(Blocks.CANDLE, 4));
				x.put(0, st(s.stone.accent()));
			} else if (x.a < 2.5) {
				x.put(0, slab(s.stone.slab(), false));
				if (ab < 0.5 && x.a >= 1.5) {
					x.put(0, face(Blocks.LECTERN, x.face));
				}
			}
			return false;
		}
		if (x.aisle || !run(x)) {
			return false;
		}
		int i = Math.floorMod(x.iu, 3);
		boolean desk = x.fs >= 1.5 && Math.floorMod((int) Math.floor(x.av), 3) == (x.through ? 2 : 0);
		if (desk && i == 0) {
			x.put(0, table(x));
			int roll = x.roll(7) % 3;
			x.put(1, roll == 0 ? candles(Blocks.DYED_CANDLE.pick(DyeColor.BLACK), 1) : roll == 1 ? st(Blocks.CARPET.pick(DyeColor.WHITE)) : st(Blocks.DECORATED_POT));
		} else if (desk && i == 1) {
			x.put(0, slab(s.wood.slab(), false));
		}
		return false;
	}

	/** The ward: cots with pillows behind white glass screens, physic on tables, flowers, honey, and at the head a red cross over the physician's desk. */
	private static boolean ward(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? st(Blocks.CONCRETE.pick(DyeColor.WHITE)) : st(Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY)));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 2) {
				// A red cross on white over the physician's chest.
				BlockState red = st(Blocks.CONCRETE.pick(DyeColor.RED));
				BlockState white = st(Blocks.CONCRETE.pick(DyeColor.WHITE));
				x.put(2, ab < 0.5 ? red : white);
				x.put(3, red);
				x.put(4, ab < 0.5 ? red : white);
				x.put(0, face(Blocks.CHEST, x.face));
			} else if (x.a < 2 && ab < 1.5 && !x.through) {
				x.put(0, table(x));
				x.put(1, ab < 0.5 ? st(Blocks.BREWING_STAND) : st(Blocks.HONEY_BLOCK));
			}
			return false;
		}
		if (x.fs < 2 && run(x) && !x.aisle) {
			int i = Math.floorMod(x.iu, 4);
			if (i == 0) {
				x.fill(0, 2, st(Blocks.STAINED_GLASS.pick(DyeColor.WHITE)));
			} else if (i == 1 || i == 2) {
				x.put(0, st(Blocks.WOOL.pick(DyeColor.WHITE)));
				x.put(1, st(Blocks.CARPET.pick(x.r.side ? DyeColor.WHITE : s.a)));
			} else if (x.r.side) {
				x.put(0, st(POTS[x.roll(3) % POTS.length]));
				x.put(2, x.sconceOrAir());
			}
			return false;
		}
		if (!x.aisle && run(x) && Math.floorMod(x.iu, 6) == 3 && x.fs >= 2 && x.fs < 3) {
			x.put(0, table(x));
			x.put(1, x.roll(8) % 2 == 0 ? st(Blocks.BREWING_STAND) : cauldron(1));
		}
		return false;
	}

	/** The laurels: plinths with trophies (heads) down both walls, glass display cases, banners, and a trophy cup on a pedestal at the head. */
	private static boolean laurels(X x) {
		Style s = x.s;
		x.floor(x.av < 1.0 ? st(Blocks.GILDED_BLACKSTONE) : st(s.stone.alt()));
		if (x.keep) {
			return false;
		}
		BlockState[] heads = {st(Blocks.SKELETON_SKULL), st(Blocks.ZOMBIE_HEAD), st(Blocks.CREEPER_HEAD), st(Blocks.PIGLIN_HEAD),
			st(Blocks.WITHER_SKELETON_SKULL), st(Blocks.DRAGON_HEAD)};
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 2 && ab < 0.5) {
				x.put(0, st(s.stone.accent()));
				x.put(1, st(Blocks.GOLD_BLOCK));
				x.put(2, st(Blocks.CAULDRON));
				x.put(3, x.standingLamp());
			} else if (x.a < 1 && ab < 2.5) {
				x.put(3, face(Blocks.WALL_BANNER.pick(ab < 1.5 ? DyeColor.YELLOW : s.a), x.face));
			}
			return false;
		}
		if (x.fs < 1.5 && Math.floorMod(x.iu, 3) == 1 && run(x)) {
			x.put(0, st(s.stone.accent()));
			x.put(1, heads[Math.floorMod(x.iu / 3 + s.index + (x.v > 0 ? 3 : 0), heads.length)]);
			if (s.bit(4)) {
				x.put(2, st(Blocks.GLASS));
			}
			return false;
		}
		if (x.r.side && Math.floorMod(x.iu, 3) == 2) {
			x.put(3, face(Blocks.WALL_BANNER.pick(Math.floorMod(x.iu, 6) == 2 ? s.a : DyeColor.YELLOW), x.r.awayFromSide));
		} else if (x.r.side) {
			x.wall();
		}
		return false;
	}

	/** The apiary: hives and nests along the walls, honey and honeycomb, flowers, yellow candles, mead barrels, and a honeycomb wall at the head. */
	private static boolean apiary(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 3) == 0 ? st(Blocks.HONEYCOMB_BLOCK) : st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			if (x.a < 1) {
				for (int y = 0; y <= Math.min(4, x.top - x.feet); y++) {
					x.put(y, (y + x.ib) % 3 == 0 ? face(Blocks.BEEHIVE, x.face) : st(Blocks.HONEYCOMB_BLOCK));
				}
			} else if (x.a < 2 && Math.abs(x.b) < 1.5) {
				x.put(0, face(Blocks.BARREL, Direction.UP));
				x.put(1, candles(Blocks.DYED_CANDLE.pick(DyeColor.YELLOW), 3));
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 3);
			x.put(0, i == 0 ? face(Blocks.BEEHIVE, x.r.awayFromSide) : i == 1 ? st(Blocks.HONEY_BLOCK) : st(Blocks.HONEYCOMB_BLOCK));
			x.put(1, i == 0 ? face(Blocks.BEE_NEST, x.r.awayFromSide) : st(POTS[x.roll(3) % POTS.length]));
			return false;
		}
		if (!x.aisle && run(x) && x.fs >= 1.5) {
			int roll = x.roll(4) % 10;
			if (roll < 3) {
				x.put(0, st(roll == 0 ? Blocks.TORCHFLOWER : roll == 1 ? Blocks.OXEYE_DAISY : Blocks.DANDELION));
				x.floor(st(Blocks.GRASS_BLOCK));
			} else if (roll == 3 && x.fs >= 2) {
				x.put(0, table(x));
				x.put(1, candles(Blocks.DYED_CANDLE.pick(DyeColor.YELLOW), 2));
			}
		}
		return false;
	}

	/** The wagers: a bookmaker's booth behind bars at the head, card tables with chips, and boards of odds on the walls. */
	private static boolean wagers(X x) {
		Style s = x.s;
		x.floor(x.fs >= 1.5 && run(x) ? st(Blocks.MOSS_BLOCK) : st(s.wood.planks()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1) {
				x.put(0, ab < 1 ? st(Blocks.GOLD_BLOCK) : face(Blocks.CHEST, x.face));
				x.put(2, face(Blocks.WALL_BANNER.pick(s.a), x.face));
			} else if (x.a < 2 && ab < x.hw - 0.5) {
				x.put(0, st(s.wood.planks()));
				x.put(1, ab < 0.5 ? face(Blocks.LECTERN, x.face) : st(Blocks.IRON_BARS));
				x.put(2, st(Blocks.IRON_BARS));
			}
			return false;
		}
		if (x.r.side) {
			if (Math.floorMod(x.iu, 4) == 1) {
				int n = Math.floorMod(x.iu / 4 + s.index, RoomStyles.PEOPLE.length);
				x.sideSign(2, DyeColor.WHITE, true, "TODAY'S ODDS", RoomStyles.PEOPLE[n].toUpperCase(Locale.ROOT) + " " + (1 + n % 7) + ":" + (1 + (n * 3) % 5),
					RoomStyles.PEOPLE[(n + 9) % RoomStyles.PEOPLE.length].toUpperCase(Locale.ROOT) + " " + (2 + n % 5) + ":1", "VAELOR -:-");
			} else {
				x.wall();
			}
			return false;
		}
		if (x.aisle || !run(x)) {
			return false;
		}
		double row = x.through ? Math.max(2.0, x.hw / 2) : Math.max(1.5, x.hw / 2);
		int i = Math.floorMod(x.iu, 5);
		if (Math.abs(x.av - row) < 0.5 && i == 2) {
			x.put(0, st(s.wood.fence()));
			x.put(1, st(Blocks.CARPET.pick(DyeColor.GREEN)));
		} else if (Math.abs(x.av - row) < 0.5 && (i == 1 || i == 3)) {
			x.put(0, chair(x, i == 1 ? x.r.out : x.r.in));
		} else if (Math.abs(x.av - row) < 1.5 && i == 2) {
			x.put(0, chair(x, x.av > row ? x.r.towardSide : x.r.awayFromSide));
		}
		return false;
	}

	/** The engines: pistons, observers and lit lamps along the walls, gears of grindstones, copper pipes, and a great clock on the head wall. */
	private static boolean engines(X x) {
		Style s = x.s;
		x.floor(Math.floorMod(x.iu + x.iv, 2) == 0 ? copper(x) : st(Blocks.COPPER_GRATE.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)])));
		if (x.keep) {
			return false;
		}
		if (x.head() && x.a < 1) {
			// The clock face: a ring of copper round a gold hub, with hands of rods.
			for (int y = 0; y <= Math.min(6, x.top - x.feet); y++) {
				double rr = Math.hypot(x.b, y - 3);
				if (Math.abs(rr - 2.4) < 0.5) {
					x.put(y, copper(x));
				} else if (rr < 0.5) {
					x.put(y, st(Blocks.GOLD_BLOCK));
				} else if (Math.abs(x.b) < 0.5 && y > 3 && rr < 2) {
					x.put(y, face(Blocks.END_ROD, Direction.UP));
				}
			}
			return false;
		}
		if (x.r.side) {
			int i = Math.floorMod(x.iu, 4);
			x.put(0, i == 0 ? face(Blocks.PISTON, Direction.UP) : i == 1 ? face(Blocks.OBSERVER, x.r.awayFromSide) : i == 2 ? lit(Blocks.REDSTONE_LAMP)
				: face(Blocks.DISPENSER, x.r.awayFromSide));
			x.put(1, i == 2 ? bulb(x) : copper(x));
			if (x.flat) {
				x.r.put(x.top - 1, st(Blocks.COPPER_CHAIN.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)])).setValue(RotatedPillarBlock.AXIS, x.r.in.getAxis()));
			}
			return false;
		}
		if (!x.aisle && run(x) && Math.floorMod(x.iu, 6) == 3 && x.fs >= 1.5 && x.fs < 2.5) {
			x.put(0, face(Blocks.GRINDSTONE, x.r.towardSide));
			x.put(1, face(Blocks.GRINDSTONE, x.r.in));
		} else if (!x.aisle && run(x) && Math.floorMod(x.iu, 6) == 0 && x.fs >= 1.5 && x.fs < 2.5) {
			x.put(0, copper(x));
			x.rise(1, st(Blocks.COPPER_CHAIN.waxed().pick(RoomStyles.WEATHER[s.pick(3, 4)])));
		}
		return false;
	}

	/** A mushroom cellar: mycelium and podzol, giant mushrooms with shroomlights, hanging roots, glow lichen, barrels in the damp. Dim. */
	private static boolean cellar(X x) {
		Style s = x.s;
		x.floor(x.roll(1) % 3 == 0 ? st(Blocks.PODZOL) : x.roll(1) % 3 == 1 ? st(Blocks.ROOTED_DIRT) : st(Blocks.MYCELIUM));
		if (x.flat && x.roll(2) % 7 == 0 && x.fs > 1) {
			x.r.put(x.top, st(Blocks.HANGING_ROOTS));
		}
		if (x.keep) {
			return true;
		}
		// Two giant mushrooms, a red and a brown, where the body is wide enough.
		for (int m = 0; m < 2; m++) {
			double cu = x.depth * (m == 0 ? 0.38 : 0.62);
			double cv = x.through ? (m == 0 ? 1 : -1) * Math.max(2.5, x.hw - 2) : (m == 0 ? 1 : -1) * Math.min(1.5, x.hw / 3);
			double t = Math.hypot(x.u - cu, x.v - cv);
			if (t < 0.6) {
				x.fill(0, 2, st(Blocks.MUSHROOM_STEM));
				x.put(3, st(Blocks.SHROOMLIGHT));
				return true;
			}
			if (t < 1.8) {
				x.put(3, st(m == 0 ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK));
				return true;
			}
		}
		if (x.r.side) {
			if (Math.floorMod(x.iu, 5) == 0) {
				x.put(0, face(Blocks.BARREL, Direction.UP));
			} else if (x.roll(4) % 3 == 0) {
				x.put(1 + x.roll(5) % 3, st(Blocks.GLOW_LICHEN).setValue(MultifaceBlock.getFaceProperty(x.r.towardSide), true));
			}
			return true;
		}
		if (!x.aisle && x.roll(6) % 7 == 0) {
			x.put(0, st(x.roll(7) % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM));
		} else if (Math.floorMod(x.iu, 7) == 3 && x.av < 0.5) {
			x.hang(hanging(Blocks.SOUL_LANTERN));
		}
		return true;
	}

	/** A council chamber: a round table with chairs about it under a seal, banners of the Seven, and a raised seat at the head. */
	private static boolean council(X x) {
		Style s = x.s;
		x.floor(x.av < 0.5 && Math.abs(x.mid()) < 0.5 ? st(Blocks.GLAZED_TERRACOTTA.pick(s.a)) : st(s.stone.alt()));
		if (x.keep) {
			return false;
		}
		if (x.head()) {
			double ab = Math.abs(x.b);
			if (x.a < 1 && ab < 0.5) {
				x.put(0, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, x.toHead));
				x.put(1, st(Blocks.GOLD_BLOCK));
				x.put(3, face(Blocks.WALL_BANNER.pick(s.a), x.face));
			} else if (x.a < 1 && ab < 3.5) {
				x.put(3, face(Blocks.WALL_BANNER.pick(DyeColor.byId(Math.floorMod(x.ib + s.index, 16))), x.face));
				x.put(0, slab(s.stone.slab(), false));
			} else if (x.a < 2 && ab < 1.5) {
				x.put(0, slab(s.stone.slab(), false));
			}
			return false;
		}
		if (x.r.side) {
			x.wall();
			return false;
		}
		if (!x.through) {
			double dm = Math.hypot(x.mid(), x.v);
			double table = Math.min(2.5, x.hw - 2.2);
			if (dm < table - 1) {
				x.put(0, st(Blocks.CARPET.pick(s.b)));
			} else if (dm < table) {
				x.put(0, table(x));
				x.put(1, x.roll(4) % 3 == 0 ? candles(Blocks.CANDLE, 2) : st(Blocks.AIR));
			} else if (dm < table + 1 && Math.floorMod(x.iu + x.iv, 2) == 0) {
				x.put(0, chair(x, Math.abs(x.mid()) > x.av ? (x.mid() > 0 ? x.r.out : x.r.in) : (x.v > 0 ? x.r.towardSide : x.r.awayFromSide)));
			}
		} else if (!x.aisle && run(x) && x.fs >= 1.5 && x.fs < 2.5 && Math.floorMod(x.iu, 2) == 0) {
			x.put(0, chair(x, x.r.towardSide));
		}
		return false;
	}

	@SuppressWarnings("unused")
	private static BlockState log(X x) {
		return beam(x.s.wood.log(), x.r.in);
	}

	@SuppressWarnings("unused")
	private static BlockState trapdoorTop(X x, Direction f) {
		return stairTop(x.s.wood.stairs(), f);
	}
}

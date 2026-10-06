package io.github.flick256.sparbot.practice.colosseum;

import io.github.flick256.sparbot.practice.colosseum.ColosseumInterior.Room;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Every ordinary hall in the stands, each one of its own. A hall's number (unique, from its Gallery, sector
 * and level) picks:
 * <ul>
 * <li>its kind, from {@link RoomKinds}: feast hall, armoury, observatory, beast pens and two dozen more.
 * Neighbours are never the same kind.</li>
 * <li>a palette: one of twelve woods, two of a dozen stones, two colours, a kind of light.</li>
 * <li>a floor pattern, a wall treatment, a ceiling and a few variant switches the kind uses for its own layout.</li>
 * <li>a name nobody else has ("The Amber Larder / of Brenna"), and an inscription about whoever it is named for.</li>
 * </ul>
 * A few in every hundred are long abandoned: cobwebs, cracked stone, the lights mostly out.
 */
final class RoomStyles {
	private RoomStyles() {
	}

	// --- Palettes ---

	/** A wood: planks, log, stripped log, stairs, slab, fence, trapdoor and shelf. */
	record Wood(Block planks, Block log, Block stripped, Block stairs, Block slab, Block fence, Block trapdoor, Block shelf) {
	}

	/** A stone: the main block, a second one to go with it, stairs, slab, wall, and a carved accent. */
	record Stone(Block block, Block alt, Block stairs, Block slab, Block wall, Block accent) {
	}

	static final Wood[] WOODS = {
		new Wood(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_TRAPDOOR,
			Blocks.OAK_SHELF),
		new Wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE,
			Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_SHELF),
		new Wood(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_FENCE,
			Blocks.BIRCH_TRAPDOOR, Blocks.BIRCH_SHELF),
		new Wood(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_FENCE,
			Blocks.JUNGLE_TRAPDOOR, Blocks.JUNGLE_SHELF),
		new Wood(Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE,
			Blocks.ACACIA_TRAPDOOR, Blocks.ACACIA_SHELF),
		new Wood(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
			Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_TRAPDOOR, Blocks.DARK_OAK_SHELF),
		new Wood(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB,
			Blocks.MANGROVE_FENCE, Blocks.MANGROVE_TRAPDOOR, Blocks.MANGROVE_SHELF),
		new Wood(Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE,
			Blocks.CHERRY_TRAPDOOR, Blocks.CHERRY_SHELF),
		new Wood(Blocks.PALE_OAK_PLANKS, Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG, Blocks.PALE_OAK_STAIRS, Blocks.PALE_OAK_SLAB,
			Blocks.PALE_OAK_FENCE, Blocks.PALE_OAK_TRAPDOOR, Blocks.PALE_OAK_SHELF),
		new Wood(Blocks.CRIMSON_PLANKS, Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM, Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_SLAB,
			Blocks.CRIMSON_FENCE, Blocks.CRIMSON_TRAPDOOR, Blocks.CRIMSON_SHELF),
		new Wood(Blocks.WARPED_PLANKS, Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM, Blocks.WARPED_STAIRS, Blocks.WARPED_SLAB, Blocks.WARPED_FENCE,
			Blocks.WARPED_TRAPDOOR, Blocks.WARPED_SHELF),
		new Wood(Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK, Blocks.BAMBOO_STAIRS, Blocks.BAMBOO_SLAB, Blocks.BAMBOO_FENCE,
			Blocks.BAMBOO_TRAPDOOR, Blocks.BAMBOO_SHELF)};

	static final Stone[] STONES = {
		new Stone(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL,
			Blocks.CHISELED_STONE_BRICKS),
		new Stone(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_WALL,
			Blocks.CHISELED_DEEPSLATE),
		new Stone(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
			Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.CHISELED_POLISHED_BLACKSTONE),
		new Stone(Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL,
			Blocks.CHISELED_TUFF_BRICKS),
		new Stone(Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.DIORITE_WALL, Blocks.CHISELED_QUARTZ_BLOCK),
		new Stone(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL,
			Blocks.CHISELED_SANDSTONE),
		new Stone(Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.CUT_RED_SANDSTONE_SLAB,
			Blocks.RED_SANDSTONE_WALL, Blocks.CHISELED_RED_SANDSTONE),
		new Stone(Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.CHISELED_TUFF),
		new Stone(Blocks.BRICKS, Blocks.TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICK_WALL, Blocks.CHISELED_STONE_BRICKS),
		new Stone(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_WALL,
			Blocks.SEA_LANTERN),
		new Stone(Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB,
			Blocks.RED_NETHER_BRICK_WALL, Blocks.CHISELED_NETHER_BRICKS),
		new Stone(Blocks.RESIN_BRICKS, Blocks.POLISHED_GRANITE, Blocks.RESIN_BRICK_STAIRS, Blocks.RESIN_BRICK_SLAB, Blocks.RESIN_BRICK_WALL,
			Blocks.CHISELED_RESIN_BRICKS),
		new Stone(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL,
			Blocks.PURPUR_PILLAR),
		new Stone(Blocks.POLISHED_DIORITE, Blocks.CALCITE, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL,
			Blocks.QUARTZ_PILLAR)};

	/** Light: lanterns, soul lanterns, copper lanterns, candle wheels, froglights, sea lanterns with end rods. */
	static final int LIGHTS = 6;

	// --- Names ---

	static final String[] ADJECTIVES = {"AMBER", "ASHEN", "AZURE", "BRASS", "CINDER", "COBALT", "COPPER", "CRIMSON", "DUSK", "EMBER", "GILDED", "GLASS",
		"GOLDEN", "GREY", "HOLLOW", "IRON", "IVORY", "JADE", "LONG", "LOW", "MOON", "OAKEN", "OLD", "OPAL", "PALE", "QUIET", "RED", "ROSE", "RUSTED",
		"SILVER", "STAR", "STONE", "SUN", "VELVET", "WHITE", "WINTER", "HIGH", "DEEP", "BRIGHT", "SMOKY"};
	static final String[] PEOPLE = {"Aurel", "Brenna", "Cyra", "Dorn", "Ilsa", "Kael", "Mira", "Marr", "Sable", "Tobin", "Ysolde", "Tamsin", "Idris",
		"Vesna", "Halvard", "Oriel", "Pell", "Quill", "Rowan", "Sorrel", "Thane", "Ulric", "Wren", "Yara", "Zeph", "Anselm", "Bryn", "Cassia", "Edda",
		"Fenn", "Garrick", "Hesper", "Ivo", "Jory", "Liesl", "Nim", "Odo", "Petra", "Rhea", "Silas", "Talia"};

	/** A hall's own number, unique across the stands. */
	static int index(int band, int sector, int k, boolean annex) {
		if (annex) {
			return 1056 + StairHall.quarter(sector) * 5 + k;
		}
		return (band == 0 ? 0 : band == 1 ? 192 : 624) + k * 48 + sector;
	}

	static int mix(int a) {
		a ^= a >>> 16;
		a *= 0x7feb352d;
		a ^= a >>> 15;
		a *= 0x846ca68b;
		a ^= a >>> 16;
		return a & 0x7fffffff;
	}

	/** The hall's kind: a step of 11 kinds round each ring (never the same as the next hall's), from a start of its own for each level. */
	static int kind(int band, int sector, int k, boolean annex) {
		int start = mix(band * 131 + k * 17 + (annex ? 7 : 0) + 1) % RoomKinds.COUNT;
		return Math.floorMod(sector * 11 + start, RoomKinds.COUNT);
	}

	/** A hall's name, as on the plaque over its door: "AMBER LARDER" / "of Brenna" (no two the same). */
	static String[] name(int band, int sector, int k, boolean annex) {
		Style s = style(band, sector, k, annex);
		return new String[] {s.adjective + " " + s.noun, "of " + s.person};
	}

	/** Everything a hall's number decides. */
	static final class Style {
		final int index;
		final int seed;
		final int kind;
		final Wood wood;
		final Stone stone;
		final Stone stone2;
		final DyeColor a;
		final DyeColor b;
		final int light;
		final int floor;
		final int walls;
		final int ceiling;
		final boolean abandoned;
		final String adjective;
		final String noun;
		final String person;
		final int year;

		Style(int band, int sector, int k, boolean annex) {
			index = index(band, sector, k, annex);
			seed = mix(index * 7919 + 17);
			kind = kind(band, sector, k, annex);
			wood = WOODS[seed % WOODS.length];
			int s1 = (seed >>> 4) % STONES.length;
			stone = STONES[s1];
			stone2 = STONES[(s1 + 1 + (seed >>> 8) % (STONES.length - 1)) % STONES.length];
			a = DyeColor.byId((seed >>> 12) % 16);
			DyeColor second = DyeColor.byId((seed >>> 16) % 16);
			b = second == a ? DyeColor.byId((a.getId() + 7) % 16) : second;
			light = (seed >>> 20) % LIGHTS;
			floor = (seed >>> 23) % 6;
			walls = (seed >>> 26) % 4;
			ceiling = (seed >>> 28) % 3;
			abandoned = mix(seed) % 100 < 8;
			adjective = ADJECTIVES[(index * 17) % ADJECTIVES.length];
			person = PEOPLE[(29 * (index / 40) + 13 * (index % 40)) % PEOPLE.length];
			noun = RoomKinds.NOUNS[kind][(seed >>> 3) % 3];
			year = 1 + mix(seed + 5) % 80;
		}

		/** A switch for the kind's own layout. */
		boolean bit(int n) {
			return (mix(seed + 101 * n) & 1) == 1;
		}

		/** A small number for the kind's own layout, 0 to {@code bound - 1}. */
		int pick(int n, int bound) {
			return mix(seed + 977 * n) % bound;
		}
	}

	static Style style(int band, int sector, int k, boolean annex) {
		return new Style(band, sector, k, annex);
	}

	/** Whether a hall has a door on its inner side, and on its outer side. */
	static boolean doorIn(int band, int sector, int k, boolean annex) {
		return !annex && ColosseumInterior.door(band, sector, k, true) && (band == 0 ? k == 0 : band == 1 ? k <= 4 : k <= 8);
	}

	static boolean doorOut(int band, int sector, int k, boolean annex) {
		return annex || band != 2 && ColosseumInterior.door(band, sector, k, false) && (band == 0 ? k <= 3 : k <= 8);
	}

	// --- One column of a hall ---

	/** Where a column is in its hall, and the helpers the kinds build with. */
	static final class X {
		final Room r;
		final Style s;
		final double u;
		final double v;
		final double av;
		final double depth;
		final double hw;
		/** From the nearer side wall. */
		final double fs;
		final int iu;
		final int iv;
		final int feet;
		/** The highest air block under the ceiling. */
		final int top;
		final boolean flat;
		final boolean doorIn;
		final boolean doorOut;
		/** A hall with a door at each end: the walk between them is kept clear. */
		final boolean through;
		/** For the hall's head (the end without a door, or the middle of each side wall in a through hall): from its wall, and along it. */
		final double a;
		final double b;
		final int ia;
		final int ib;
		/** Facing out of the head wall into the hall, and back toward it. */
		final Direction face;
		final Direction toHead;
		/** Near a door: kept clear. */
		final boolean keep;
		/** The walk between a through hall's doors. */
		final boolean aisle;
		/** From the far end (the head of an end hall, the outer end of a through hall), and from the near end. */
		final double back;
		final double front;
		/** The hall's half width at its middle: the same for every column of it (for anything that must not change size along it). */
		final double midHw;

		X(Room r, Style s, boolean doorIn, boolean doorOut, boolean annex) {
			this.r = r;
			this.s = s;
			u = r.u;
			v = r.v;
			av = r.av;
			depth = r.depth;
			hw = r.halfW;
			fs = hw - av;
			iu = r.iu;
			iv = r.iv;
			feet = r.feet;
			top = r.ceiling - 1;
			flat = r.flatCeiling();
			this.doorIn = doorIn;
			this.doorOut = doorOut;
			through = doorIn && doorOut;
			if (through) {
				a = fs;
				b = u - depth / 2;
				face = r.awayFromSide;
				toHead = r.towardSide;
				back = depth - u;
				front = u;
			} else if (doorOut) {
				a = u;
				b = -v;
				face = r.out;
				toHead = r.in;
				back = u;
				front = depth - u;
			} else {
				a = depth - u;
				b = v;
				face = r.in;
				toHead = r.out;
				back = depth - u;
				front = u;
			}
			ia = (int) Math.floor(a);
			ib = (int) Math.round(b);
			keep = doorIn && u < 2.5 && av < 2.1 || doorOut && depth - u < 2.5 && av < 2.1;
			double middle = ColosseumInterior.ROOMS[r.band][0] + (annex ? ColosseumInterior.ANNEX : 0) + depth / 2;
			midHw = middle * Math.toRadians(ColosseumInterior.SECTOR / 2) - 0.5;
			aisle = through && av < 1.1;
		}

		/** How deep the head's furnishings may go (a through hall's side walls are close to its walk). */
		double headDepth() {
			return through ? Math.max(1.0, Math.min(3.0, hw - 1.6)) : 3.5;
		}

		/** Whether the column is in the head: the far end of an end hall, the middle of each side of a through hall. */
		boolean head() {
			return a < headDepth() && (through ? Math.abs(b) < 3.6 : true);
		}

		/** The middle of the hall (lengthwise), for centrepieces. */
		double mid() {
			return u - depth / 2;
		}

		int roll(int salt) {
			return r.roll(salt + s.index * 7);
		}

		void put(int dy, BlockState state) {
			r.put(feet + dy, state);
		}

		void fill(int dy0, int dy1, BlockState state) {
			r.fill(feet + dy0, feet + dy1, state);
		}

		/** Up to the ceiling, from {@code dy}. */
		void rise(int dy, BlockState state) {
			r.fill(feet + dy, top, state);
		}

		void floor(BlockState state) {
			r.put(feet - 1, state);
		}

		boolean air(int dy) {
			return CelestialColosseum.at(r.c, feet + dy).isAir();
		}

		void sign(int dy, Direction facing, DyeColor color, boolean glow, String... lines) {
			r.sign(feet + dy, facing, color, glow, lines);
		}

		/** A wall sign on the side wall, facing into the hall. */
		void sideSign(int dy, DyeColor color, boolean glow, String... lines) {
			sign(dy, r.awayFromSide, color, glow, lines);
		}

		void hang(BlockState lantern) {
			ColosseumInterior.hang(r.c, feet, r.ceiling, lantern);
		}

		/** The style's hanging light. */
		BlockState lamp() {
			return switch (s.light) {
				case 1 -> hanging(Blocks.SOUL_LANTERN);
				case 2 -> hanging(Blocks.COPPER_LANTERN.waxed().pick(WEATHER[s.pick(9, 4)]));
				default -> hanging(Blocks.LANTERN);
			};
		}

		/** The style's standing light (on a table or post). */
		BlockState standingLamp() {
			return switch (s.light) {
				case 1 -> st(Blocks.SOUL_LANTERN);
				case 2 -> st(Blocks.COPPER_LANTERN.waxed().pick(WEATHER[s.pick(9, 4)]));
				case 3 -> candles(Blocks.DYED_CANDLE.pick(s.a), 3);
				default -> st(Blocks.LANTERN);
			};
		}

		/** A light on the wall (torch of the style's kind), facing into the hall. */
		BlockState sconce() {
			return switch (s.light) {
				case 1 -> face(Blocks.SOUL_WALL_TORCH, r.awayFromSide);
				case 2 -> face(Blocks.COPPER_WALL_TORCH, r.awayFromSide);
				default -> face(Blocks.WALL_TORCH, r.awayFromSide);
			};
		}

		/** A sconce, unless the hall is abandoned (its lights long out). */
		BlockState sconceOrAir() {
			return s.abandoned ? st(Blocks.AIR) : sconce();
		}

		/** The style's treatment of the side walls, for the side columns a kind leaves bare. */
		void wall() {
			if (!r.side) {
				return;
			}
			switch (s.walls) {
				case 0 -> {
					// Pilasters of log every four blocks, sconces between.
					if (Math.floorMod(iu, 4) == 0) {
						rise(0, pillar(s.wood.stripped()));
					} else if (Math.floorMod(iu, 4) == 2 && !s.abandoned) {
						put(3, sconce());
					}
				}
				case 1 -> {
					// Stone wainscot to the waist, with a carved course every few blocks.
					put(0, Math.floorMod(iu, 5) == 0 ? st(s.stone.accent()) : st(s.stone.alt()));
					put(1, slab(s.stone.slab(), true));
					if (Math.floorMod(iu, 5) == 2 && !s.abandoned) {
						put(3, sconce());
					}
				}
				case 2 -> {
					// Panelling: open trapdoors against the wall, shelves above.
					BlockState panel = st(s.wood.trapdoor()).setValue(TrapDoorBlock.FACING, r.awayFromSide).setValue(TrapDoorBlock.OPEN, true);
					fill(0, 1, panel);
					if (Math.floorMod(iu, 3) == 1) {
						put(2, face(s.wood.shelf(), r.awayFromSide));
					} else if (Math.floorMod(iu, 6) == 3 && !s.abandoned) {
						put(3, sconce());
					}
				}
				default -> {
					// Tapestries in the hall's colours, with lights between.
					if (Math.floorMod(iu, 5) == 1) {
						put(3, face(Blocks.WALL_BANNER.pick(Math.floorMod(iu, 10) == 1 ? s.a : s.b), r.awayFromSide));
					} else if (Math.floorMod(iu, 5) == 3 && !s.abandoned) {
						put(3, sconce());
					}
				}
			}
		}
	}

	static final WeatheringCopper.WeatherState[] WEATHER = {WeatheringCopper.WeatherState.UNAFFECTED, WeatheringCopper.WeatherState.EXPOSED,
		WeatheringCopper.WeatherState.WEATHERED, WeatheringCopper.WeatherState.OXIDIZED};

	// --- Block helpers ---

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	/** A block turned to face {@code f}, if it turns. */
	static BlockState face(Block b, Direction f) {
		BlockState s = b.defaultBlockState();
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return s.setValue(BlockStateProperties.HORIZONTAL_FACING, f);
		}
		if (s.hasProperty(BlockStateProperties.FACING)) {
			return s.setValue(BlockStateProperties.FACING, f);
		}
		return s;
	}

	static BlockState stair(Block b, Direction f) {
		return b.defaultBlockState().setValue(StairBlock.FACING, f);
	}

	static BlockState stairTop(Block b, Direction f) {
		return stair(b, f).setValue(StairBlock.HALF, Half.TOP);
	}

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState candles(Block candle, int n) {
		return candle.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, true);
	}

	static BlockState hanging(Block lantern) {
		return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	static BlockState pillar(Block log) {
		return log.defaultBlockState();
	}

	static BlockState beam(Block log, Direction along) {
		BlockState s = log.defaultBlockState();
		return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, along.getAxis()) : s;
	}

	// --- Building a hall ---

	/** One column of one hall: its floor, its kind's furnishings, then lights, the ceiling, the inscription and (if abandoned) its decay. */
	static void room(Room r, boolean annex) {
		Style s = style(r.band, r.sector, r.k, annex);
		X x = new X(r, s, doorIn(r.band, r.sector, r.k, annex), doorOut(r.band, r.sector, r.k, annex), annex);
		floor(x);
		boolean ownLights = RoomKinds.build(x);
		if (!ownLights) {
			chandeliers(x);
		}
		ceiling(x);
		inscription(x);
		if (s.abandoned) {
			decay(x);
		}
	}

	/** The style's floor pattern (kinds lay their own over it where they need to). */
	static void floor(X x) {
		Style s = x.s;
		BlockState f = switch (s.floor) {
			case 0 -> st(Math.floorMod(x.iu + x.iv, 2) == 0 ? s.stone.block() : s.stone2.block());
			case 1 -> x.fs < 1.2 || x.u < 1.2 || x.depth - x.u < 1.2 ? st(s.stone.block())
				: Math.floorMod(x.iu, 5) == 0 ? beam(s.wood.stripped(), x.r.towardSide) : st(s.wood.planks());
			case 2 -> Math.floorMod(x.iu / 2 + x.iv, 2) == 0 ? st(s.wood.planks()) : beam(s.wood.stripped(), Math.floorMod(x.iv, 2) == 0 ? x.r.in : x.r.towardSide);
			case 3 -> x.av < 1.0 ? st(Blocks.GLAZED_TERRACOTTA.pick(s.a)) : x.av < 2.0 ? st(Blocks.CONCRETE.pick(s.b)) : st(s.stone.alt());
			case 4 -> Math.floorMod(x.iu + x.iv, 4) < 2 ? st(Blocks.DYED_TERRACOTTA.pick(s.a)) : st(s.stone.block());
			default -> {
				int roll = x.roll(3) % 10;
				yield st(roll < 5 ? s.stone.block() : roll < 8 ? s.stone.alt() : s.stone2.alt());
			}
		};
		x.floor(f);
	}

	/**
	 * Chandeliers down the middle of the hall, every seven blocks: a hub on a chain with arms round it, each kind
	 * of light its own way.
	 */
	static void chandeliers(X x) {
		int ring = Math.floorMod(x.iu, 7);
		boolean row = ring == 3 && x.u > 2.5 && x.depth - x.u > 2.5;
		boolean hub = row && x.av < 0.5;
		boolean arm = !hub && (row && x.av < 1.5 || (ring == 2 || ring == 4) && x.av < 0.5 && x.depth - x.u > 2.5 && x.u > 2.5);
		if (!hub && !arm) {
			return;
		}
		if (x.s.abandoned && x.roll(5) % 3 != 0) {
			// Mostly gone: a bare chain.
			if (hub) {
				x.put(x.top - x.feet, st(Blocks.IRON_CHAIN));
			}
			return;
		}
		int low = x.flat ? x.feet + 5 : Math.max(x.feet + 4, x.top - 3);
		if (hub) {
			x.r.fill(low + 1, x.top, st(Blocks.IRON_CHAIN));
		}
		switch (x.s.light) {
			case 3 -> {
				// A wheel of candles on slabs round a lantern.
				if (hub) {
					x.r.put(low, hanging(Blocks.LANTERN));
				} else {
					x.r.put(low, slab(x.s.wood.slab(), false));
					x.r.put(low + 1, candles(Blocks.DYED_CANDLE.pick(x.s.a), 3));
				}
			}
			case 4 -> {
				// Froglights in the ceiling, bright as day.
				if (hub) {
					x.r.fill(low + 1, x.top, st(Blocks.IRON_CHAIN));
					x.r.put(low, st(x.s.pick(4, 3) == 0 ? Blocks.OCHRE_FROGLIGHT : x.s.pick(4, 3) == 1 ? Blocks.VERDANT_FROGLIGHT : Blocks.PEARLESCENT_FROGLIGHT));
				} else {
					x.r.put(x.top, st(Blocks.IRON_CHAIN));
					x.r.put(x.top - 1 >= x.feet + 3 ? x.top - 1 : x.top, x.lamp());
				}
			}
			case 5 -> {
				// A sea lantern hung on chains with rods of light round it.
				if (hub) {
					x.r.put(low, st(Blocks.SEA_LANTERN));
				} else {
					x.r.put(low, face(Blocks.END_ROD, Direction.DOWN));
				}
			}
			default -> {
				if (hub) {
					x.r.put(low, x.lamp());
				} else {
					x.r.put(x.top, st(Blocks.IRON_CHAIN));
					x.r.put(Math.max(low, x.top - 1), x.lamp());
				}
			}
		}
	}

	/** Beams across a flat ceiling, coffers, or a long ridge beam (never over anything a kind put up there). */
	static void ceiling(X x) {
		if (!x.flat || !x.air(x.top - x.feet)) {
			return;
		}
		Style s = x.s;
		BlockState across = beam(s.wood.stripped(), x.r.towardSide);
		BlockState along = beam(s.wood.stripped(), x.r.in);
		switch (s.ceiling) {
			case 0 -> {
				if (Math.floorMod(x.iu, 4) == 0) {
					x.r.put(x.top, across);
				} else if (x.r.side) {
					x.r.put(x.top, stairTop(s.wood.stairs(), x.r.towardSide));
				}
			}
			case 1 -> {
				if (Math.floorMod(x.iu, 4) == 0 || Math.floorMod(x.iv, 4) == 0) {
					x.r.put(x.top, st(s.stone.alt()));
				}
			}
			default -> {
				if (x.av < 0.5) {
					x.r.put(x.top, along);
				} else if (Math.floorMod(x.iu, 6) == 0) {
					x.r.put(x.top, across);
				}
			}
		}
	}

	/** Cobwebs in the corners, cracked stone underfoot, and dust (no lights left in the sconces). */
	static void decay(X x) {
		if (x.fs < 1.5 && x.flat && x.roll(31) % 4 == 0 && x.air(x.top - x.feet)) {
			x.r.put(x.top, st(Blocks.COBWEB));
		}
		if (x.roll(37) % 23 == 0 && x.fs < 2.5 && !x.keep && x.air(0)) {
			x.put(0, st(Blocks.COBWEB));
		}
		if (x.roll(41) % 5 == 0) {
			x.floor(st(x.roll(43) % 2 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.CRACKED_DEEPSLATE_TILES));
		}
	}

	/** One inscription per hall, on the side wall near its door: something about the one it is named for. */
	static void inscription(X x) {
		int at = x.doorIn ? 3 : (int) Math.floor(x.depth - 4);
		if (!x.r.side || x.v > 0 || x.iu != at) {
			return;
		}
		String[] lines = RoomKinds.inscription(x.s);
		x.sideSign(2, x.s.abandoned ? DyeColor.GRAY : DyeColor.BLACK, !x.s.abandoned, lines);
	}
}

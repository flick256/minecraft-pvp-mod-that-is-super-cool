package io.github.flick256.sparbot.practice.colosseum;

import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.AIR;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.F;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.S;
import static io.github.flick256.sparbot.practice.colosseum.CelestialColosseum.outward;

import io.github.flick256.sparbot.core.practice.PracticeLayout;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The halls under the stands: twenty-four of them, eight to each Gallery, every one a single great room with its own
 * story. The four gates and the four diagonal piers split each Gallery into eight; a hall is the whole wedge between a
 * gate and a pier, from its Gallery's inner ring wall to its outer one (forty to a hundred blocks long, twenty-two
 * to twenty-eight deep).
 *
 * <p>They are all built the same way, in layers, and each dressed in its own stone and furnished for its story:
 * <ul>
 * <li>the floor: a border round the walls, a field of two stones, a runner down the length and a medallion in the
 * middle;</li>
 * <li>a colonnade along the outer wall holding up a balcony (and, in the two outer Galleries, a second one along the
 * inner wall, higher), with a balustrade, lanterns and a straight stair up at each end;</li>
 * <li>the ceiling: the raked underside of the seats in the First Gallery, a coffered vault with ribs and chandeliers
 * in the others;</li>
 * <li>two great doors on each ring corridor, the hall's name over the middle of its wall;</li>
 * <li>and what makes it that hall: a centrepiece, furniture, and its story in words on its walls or a book on a
 * lectern.</li>
 * </ul>
 * Six halls are the landmarks of the story (the Archive, the Warden's Hall, the Chapel, the Cells, the Treasury and
 * the Hall of Champions), and four of the piers hide a vault (see {@link Vaults}).
 */
final class GrandHalls {
	static final Block WAXED_COPPER = Blocks.COPPER_BLOCK.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED);
	static final Block WAXED_CUT_COPPER = Blocks.CUT_COPPER.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED);
	static final Block ROD = Blocks.LIGHTNING_ROD.waxed().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED);
	/** Half the thickness of the piers at the diagonals. */
	static final double PIER = 3.5;
	/** The balconies' feet, above the hall's floor: along the outer wall, and (higher) along the inner wall. */
	static final int B1 = 9;
	static final int B2 = 18;
	/** How deep the balconies are. */
	static final double BALCONY = 4;
	/** The nave's ceiling in the two outer Galleries (above it the space under the seats is solid). */
	static final int CAP = 28;

	private GrandHalls() {
	}

	// --- Where a column is ---

	/** One column of a hall, and where it is in it. */
	static final class Spot {
		final BlockState[] c;
		final int dx;
		final int dz;
		final int hall;
		final int gallery;
		/** From the inner wall (0) out to the outer ({@link #depth}). */
		final double u;
		final double depth;
		/** Along the hall, from the gate end (0) to the pier end ({@link #len}). */
		final double w;
		final double len;
		/** From the hall's middle, across and along. */
		final double cu;
		final double cw;
		/** The top block of air (the ceiling is the block above). */
		final int top;
		final Direction in;
		final Direction out;
		final Direction toGate;
		final Direction toPier;
		final int iu;
		final int iw;

		Spot(BlockState[] c, int dx, int dz, int hall, int gallery, double u, double depth, double w, double len, int top, Direction toGate) {
			this.c = c;
			this.dx = dx;
			this.dz = dz;
			this.hall = hall;
			this.gallery = gallery;
			this.u = u;
			this.depth = depth;
			this.w = w;
			this.len = len;
			this.cu = u - depth / 2;
			this.cw = w - len / 2;
			this.top = top;
			this.in = outward(-dx, -dz);
			this.out = outward(dx, dz);
			this.toGate = toGate;
			this.toPier = toGate.getOpposite();
			this.iu = (int) Math.floor(u);
			this.iw = (int) Math.floor(w);
		}

		void put(int y, BlockState s) {
			CelestialColosseum.put(c, y, s);
		}

		void fill(int y0, int y1, BlockState s) {
			CelestialColosseum.fill(c, y0, y1, s);
		}

		void floor(BlockState s) {
			put(F - 1, s);
		}

		int roll(int salt) {
			return PracticeLayout.scatter(dx, dz, salt + hall * 7);
		}

		/** Distance from the hall's middle. */
		double r() {
			return Math.hypot(cu, cw);
		}

		boolean wallIn() {
			return u < 1;
		}

		boolean wallOut() {
			return depth - u < 1;
		}

		boolean endGate() {
			return w < 1;
		}

		boolean endPier() {
			return len - w < 1;
		}

		/** Next to one of the hall's walls (within {@code n}). */
		boolean byWall(double n) {
			return u < n || depth - u < n || w < n || len - w < n;
		}

		/** Where people walk in through a door (kept clear). */
		boolean doorway() {
			return (u < 3.5 || depth - u < 3.5) && door(w, len) <= 2.0;
		}

		/** The stairs to the balconies, and the balconies themselves, are kept clear of furniture. */
		boolean reserved() {
			return stairOut(this) || stairIn(this) || depth - u < BALCONY + 1 || gallery > 0 && u < BALCONY + 1 && w > 5 && len - w > 5;
		}

		/** Free floor for furniture: not a wall, door, column, stair or the space under a balcony's edge. */
		boolean free() {
			return !doorway() && !reserved() && !column(this) && !byWall(1.0);
		}

		void sign(int y, Direction facing, DyeColor color, String... lines) {
			ColosseumInterior.wallSign(c, dx, y, dz, facing, color, true, lines);
		}

		void lectern(Direction facing, ColosseumLore.Book book) {
			ColosseumLore.lectern(c, dx, F, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK,
				true), book);
		}
	}

	/** How far {@code w} is from the nearer of a hall's two doors on each side (a third and two thirds along). */
	static double door(double w, double len) {
		return Math.min(Math.abs(w - len / 3), Math.abs(w - 2 * len / 3));
	}

	/** How far (in blocks along the circle) bearing {@code deg} at distance {@code d} is from the nearer of hall {@code hall}'s doors. */
	static double doorOffset(int hall, double d, double deg) {
		int g = hall / 8;
		double a = ((deg % 360) + 360) % 360;
		double phi = a - (hall / 2 % 4) * 90;
		int h = hall % 2;
		double gw = gateHalf(g);
		double w = Math.toRadians(h == 0 ? phi : 90 - phi) * d - gw;
		double len = Math.toRadians(45) * d - gw - PIER;
		return door(w, len);
	}

	/** The Gallery (0-2) of distance {@code d}, or -1. */
	static int gallery(double d) {
		return ColosseumInterior.band(ColosseumInterior.ROOMS, d);
	}

	/** How wide the gate's passage (and its walls) is at this Gallery: the atrium in the Second Gallery. */
	static double gateHalf(int gallery) {
		return gallery == 1 ? 12.5 : 6.5;
	}

	/** Which hall (0-23) holds (d, deg), -2 for a pier, -1 for neither. */
	static int at(double d, double deg) {
		int g = gallery(d);
		if (g < 0) {
			return -1;
		}
		double a = ((deg % 360) + 360) % 360;
		int q = (int) Math.floor(a / 90) % 4;
		double phi = a - q * 90;
		int h = phi < 45 ? 0 : 1;
		double fromPier = Math.toRadians(Math.abs(phi - 45)) * d;
		if (fromPier <= PIER) {
			return -2;
		}
		double fromGate = Math.sin(Math.toRadians(h == 0 ? phi : 90 - phi)) * d;
		if (fromGate <= gateHalf(g)) {
			return -1;
		}
		return g * 8 + q * 2 + h;
	}

	/** The hall at a block of the world (blueprint dx, y, dz), or -1. */
	static int at(int dx, int y, int dz) {
		if (y < F || y > F + CAP + 8) {
			return -1;
		}
		int hall = at(Math.hypot(dx, dz), Math.toDegrees(Math.atan2(dz, dx)));
		return hall < 0 ? -1 : hall;
	}

	/** Builds one column of the Galleries (d is inside one). */
	static void build(BlockState[] c, int dx, int dz, double d, double deg, int u0) {
		int g = gallery(d);
		double a = ((deg % 360) + 360) % 360;
		int q = (int) Math.floor(a / 90) % 4;
		double phi = a - q * 90;
		int h = phi < 45 ? 0 : 1;
		int under = ColosseumInterior.underside(d);
		int top = g == 0 ? under - 1 : Math.min(under - 1, F + CAP);
		if (top < under - 1) {
			CelestialColosseum.fill(c, top + 1, under - 1, CelestialColosseum.TILES);
		}
		double fromPier = Math.toRadians(Math.abs(phi - 45)) * d;
		if (fromPier <= PIER) {
			pier(c, dx, dz, d, g, q, fromPier, top);
			return;
		}
		double[] band = ColosseumInterior.ROOMS[g];
		double depth = band[1] - band[0];
		double u = d - band[0];
		double gw = gateHalf(g);
		double arcGate = Math.toRadians(h == 0 ? phi : 90 - phi) * d;
		double w = arcGate - gw;
		double len = Math.toRadians(45) * d - gw - PIER;
		// Toward the gate: against the direction of increasing angle in the first half, with it in the second.
		double ang = Math.toRadians(a);
		Direction sunwise = outward(-Math.sin(ang), Math.cos(ang));
		Direction toGate = h == 0 ? sunwise.getOpposite() : sunwise;
		int hall = g * 8 + q * 2 + h;
		Spot s = new Spot(c, dx, dz, hall, g, u, depth, w, len, top, toGate);
		skeleton(s);
		if (!stairOut(s) && !stairIn(s) && !column(s)) {
			HALLS[hall].dress.accept(s);
			balconies(s);
		}
		story(s);
	}

	/** The piers at the diagonals: solid, faced, with a vault hidden in four of them. */
	private static void pier(BlockState[] c, int dx, int dz, double d, int g, int q, double fromPier, int top) {
		CelestialColosseum.fill(c, F, top, CelestialColosseum.BRICKS);
		if (fromPier > PIER - 1) {
			CelestialColosseum.put(c, F + 4, CelestialColosseum.CHISELED);
		}
		Vaults.build(c, dx, dz, d, g, q);
	}

	// --- The common shell ---

	/** Colonnades: along the outer wall under the first balcony, and (outer Galleries) along the inner under the second. */
	static boolean column(Spot s) {
		boolean onLine = Math.abs(s.u - (s.depth - BALCONY - 0.5)) < 0.75 || s.gallery > 0 && Math.abs(s.u - (BALCONY + 0.5)) < 0.75;
		double m = Math.floorMod((int) Math.floor(s.w), 7);
		return onLine && m >= 3 && m <= 4 && s.w > 5 && s.len - s.w > 5 && door(s.w, s.len) > 2.5;
	}

	/** The stair from the floor up to the outer balcony: at the gate end, climbing outward. */
	static boolean stairOut(Spot s) {
		return s.w > 1 && s.w < 4.5 && s.u >= s.depth - BALCONY - B1 && s.u < s.depth - BALCONY;
	}

	/** The stair from the floor up to the inner balcony (outer Galleries): at the pier end, climbing inward. */
	static boolean stairIn(Spot s) {
		return s.gallery > 0 && s.len - s.w > 1 && s.len - s.w < 4.5 && s.u >= BALCONY && s.u < BALCONY + B2 && s.u < s.depth - 1.5;
	}

	private static BlockState stair(Block b, Direction up) {
		return b.defaultBlockState().setValue(StairBlock.FACING, up);
	}

	private static BlockState topSlab(Block b) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
	}

	/** The floor, the colonnades, the balconies and their stairs, the ceiling, the lights. */
	private static void skeleton(Spot s) {
		Hall hall = HALLS[s.hall];
		Palette p = hall.palette;
		s.fill(F, s.top, AIR);
		// The floor.
		BlockState floor;
		if (s.byWall(1.5)) {
			floor = p.border;
		} else if (s.r() < 3.5) {
			floor = s.r() < 1.5 ? p.medallion : p.trim;
		} else if (Math.abs(s.cu) < 1.6 && !s.reserved()) {
			floor = p.runner;
		} else {
			floor = Math.floorMod(s.iu + s.iw, 2) == 0 ? p.floorA : p.floorB;
		}
		s.floor(floor);
		// The outer balcony, its stair, its colonnade.
		boolean outerBalcony = s.top - F > B1 + 4;
		if (outerBalcony && s.depth - s.u < BALCONY) {
			s.put(F + B1 - 1, p.balcony);
			s.put(F + B1 - 2, topSlab(p.slab));
			if (s.depth - s.u > BALCONY - 1 && !(s.w > 1 && s.w < 4.5)) {
				s.put(F + B1, p.rail);
				if (Math.floorMod(s.iw, 7) == 0) {
					s.put(F + B1 + 1, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		if (outerBalcony && stairOut(s)) {
			int rise = (int) Math.floor(s.u - (s.depth - BALCONY - B1)) + 1;
			s.fill(F, F + rise - 2, p.column);
			s.put(F + rise - 1, stair(p.stairs, s.out));
		}
		boolean innerBalcony = s.gallery > 0 && s.top - F > B2 + 4;
		if (innerBalcony && s.u < BALCONY && s.w > 1 && s.len - s.w > 1) {
			s.put(F + B2 - 1, p.balcony);
			s.put(F + B2 - 2, topSlab(p.slab));
			if (s.u > BALCONY - 1 && !(s.len - s.w > 1 && s.len - s.w < 4.5)) {
				s.put(F + B2, p.rail);
				if (Math.floorMod(s.iw, 7) == 0) {
					s.put(F + B2 + 1, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		if (innerBalcony && stairIn(s)) {
			int rise = (int) Math.floor(BALCONY + B2 - s.u);
			if (rise > 0) {
				s.fill(F, F + rise - 2, p.column);
				s.put(F + rise - 1, stair(p.stairs, s.in));
			}
		}
		if (column(s)) {
			int to = Math.abs(s.u - (BALCONY + 0.5)) < 0.75 ? (innerBalcony ? F + B2 - 3 : s.top) : outerBalcony ? F + B1 - 3 : s.top;
			s.fill(F, to, p.column);
			s.put(F, p.trim);
			s.put(to, p.capital);
			// And above the balcony, the column goes on to the ceiling as a pilaster on the balustrade's line.
			if (to < s.top) {
				s.fill(to + 3, s.top, p.column);
				s.put(s.top, p.capital);
			}
		}
		// The ceiling: ribs across, chandeliers down the middle, lights in the coffers.
		if (s.gallery > 0) {
			boolean rib = Math.floorMod(s.iw, 7) == 0;
			s.put(s.top + 1, rib ? p.trim : Math.abs(s.cu) < 0.6 ? p.light : p.ceiling);
			if (rib) {
				s.put(s.top, topSlab(p.slab));
			}
		} else {
			s.put(s.top + 1, Math.floorMod(s.iw, 7) == 0 ? p.trim : p.ceiling);
		}
		if (Math.abs(s.cu) < 0.6 && Math.floorMod(s.iw, 7) == 3 && !s.doorway()) {
			int chain = Math.max(F + 7, s.top - (s.gallery == 0 ? 6 : 12));
			s.fill(chain, s.top, CelestialColosseum.CHAIN);
			s.put(chain - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// Lights on the walls, banners between.
		if ((s.wallIn() || s.wallOut()) && !s.doorway() && s.w > 2 && s.len - s.w > 2) {
			Direction face = s.wallIn() ? s.out : s.in;
			int m = Math.floorMod(s.iw, 7);
			if (m == 0) {
				s.put(F + 3, p.light);
				if (s.top - F > B1 + 6) {
					s.put(F + B1 + 3, p.light);
				}
			} else if (m == 4 && !(s.wallOut() && outerBalcony) && hall.banner != null) {
				s.put(F + 4, hall.banner.defaultBlockState().setValue(WallBannerBlock.FACING, face));
			}
		}
	}

	/** Whatever a hall's furnishing put up there, its balconies stay whole and clear to walk on. */
	private static void balconies(Spot s) {
		Palette p = HALLS[s.hall].palette;
		if (s.top - F > B1 + 4 && s.depth - s.u < BALCONY) {
			s.put(F + B1 - 1, p.balcony);
			s.put(F + B1 - 2, topSlab(p.slab));
			if (s.depth - s.u >= 1) {
				s.fill(F + B1, F + B1 + 2, AIR);
			}
			if (s.depth - s.u > BALCONY - 1 && !(s.w > 1 && s.w < 4.5)) {
				s.put(F + B1, p.rail);
				if (Math.floorMod(s.iw, 7) == 0) {
					s.put(F + B1 + 1, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		if (s.gallery > 0 && s.top - F > B2 + 4 && s.u < BALCONY && s.w > 1 && s.len - s.w > 1) {
			s.put(F + B2 - 1, p.balcony);
			s.put(F + B2 - 2, topSlab(p.slab));
			if (s.u >= 1) {
				s.fill(F + B2, F + B2 + 2, AIR);
			}
			if (s.u > BALCONY - 1 && !(s.len - s.w > 1 && s.len - s.w < 4.5)) {
				s.put(F + B2, p.rail);
				if (Math.floorMod(s.iw, 7) == 0) {
					s.put(F + B2 + 1, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
	}

	/** The hall's name: on its inner and outer walls (over the door's side of the middle) and at each end. */
	private static void story(Spot s) {
		Hall hall = HALLS[s.hall];
		boolean middle = Math.abs(s.cw) < 0.5;
		if (middle && s.wallIn()) {
			s.sign(F + 3, s.out, DyeColor.YELLOW, hall.name[0], hall.name[1], hall.words[0], hall.words[1]);
		}
		if (Math.abs(s.cu - 1) < 0.5 && s.endGate() && !stairOut(s)) {
			s.sign(F + 2, s.toPier, DyeColor.WHITE, hall.words[2], hall.words[3], hall.words[4], hall.words[5]);
		}
	}

	// --- The halls ---

	/** The stones a hall is built in. */
	record Palette(BlockState floorA, BlockState floorB, BlockState border, BlockState runner, BlockState medallion, BlockState trim,
		BlockState column, BlockState capital, BlockState balcony, Block slab, Block stairs, BlockState rail, BlockState light, BlockState ceiling) {
	}

	/** A hall: its name (two lines), its words (six short lines: under its name, and at its gate end), its stone, its banner, and its furnishing. */
	record Hall(String[] name, String[] words, Palette palette, Block banner, java.util.function.Consumer<Spot> dress) {
	}

	static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static final Palette DEEPSLATE = new Palette(b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_BRICKS),
		b(Blocks.PURPUR_BLOCK), b(Blocks.CRYING_OBSIDIAN), b(Blocks.GILDED_BLACKSTONE), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.CHISELED_DEEPSLATE),
		b(Blocks.POLISHED_DEEPSLATE), Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_STAIRS, b(Blocks.DEEPSLATE_BRICK_WALL),
		b(Blocks.SEA_LANTERN), b(Blocks.DEEPSLATE_TILES));
	private static final Palette BLACKSTONE = new Palette(b(Blocks.POLISHED_BLACKSTONE), b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.BLACKSTONE),
		b(Blocks.GOLD_BLOCK), b(Blocks.GOLD_BLOCK), b(Blocks.GILDED_BLACKSTONE), b(Blocks.POLISHED_BLACKSTONE), b(Blocks.CHISELED_POLISHED_BLACKSTONE),
		b(Blocks.POLISHED_BLACKSTONE_BRICKS), Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
		b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL), b(Blocks.SHROOMLIGHT), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
	private static final Palette QUARTZ = new Palette(b(Blocks.QUARTZ_BRICKS), b(Blocks.SMOOTH_QUARTZ), b(Blocks.CHISELED_QUARTZ_BLOCK),
		b(Blocks.PURPUR_BLOCK), b(Blocks.AMETHYST_BLOCK), b(Blocks.GOLD_BLOCK), b(Blocks.QUARTZ_PILLAR), b(Blocks.CHISELED_QUARTZ_BLOCK),
		b(Blocks.SMOOTH_QUARTZ), Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_STAIRS, b(Blocks.DIORITE_WALL), b(Blocks.SEA_LANTERN),
		b(Blocks.QUARTZ_BRICKS));
	private static final Palette TUFF = new Palette(b(Blocks.POLISHED_TUFF), b(Blocks.TUFF_BRICKS), b(Blocks.CHISELED_TUFF),
		b(WAXED_CUT_COPPER), b(WAXED_COPPER), b(Blocks.CHISELED_TUFF_BRICKS), b(Blocks.TUFF_BRICKS), b(Blocks.CHISELED_TUFF_BRICKS),
		b(Blocks.POLISHED_TUFF), Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_STAIRS, b(Blocks.TUFF_BRICK_WALL), b(Blocks.OCHRE_FROGLIGHT),
		b(Blocks.TUFF_BRICKS));
	private static final Palette SANDSTONE = new Palette(b(Blocks.CUT_SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CHISELED_SANDSTONE),
		b(WAXED_CUT_COPPER), b(Blocks.GOLD_BLOCK), b(Blocks.CHISELED_SANDSTONE), b(Blocks.CUT_SANDSTONE), b(Blocks.CHISELED_SANDSTONE),
		b(Blocks.SMOOTH_SANDSTONE), Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS, b(Blocks.SANDSTONE_WALL), b(Blocks.OCHRE_FROGLIGHT),
		b(Blocks.CUT_SANDSTONE));
	private static final Palette BRICK = new Palette(b(Blocks.BRICKS), b(Blocks.POLISHED_GRANITE), b(Blocks.MUD_BRICKS), b(Blocks.TERRACOTTA),
		b(WAXED_COPPER), b(Blocks.CHISELED_RESIN_BRICKS), b(Blocks.BRICKS), b(Blocks.CHISELED_RESIN_BRICKS), b(Blocks.MUD_BRICKS),
		Blocks.MUD_BRICK_SLAB, Blocks.BRICK_STAIRS, b(Blocks.BRICK_WALL), b(Blocks.SHROOMLIGHT), b(Blocks.BRICKS));
	private static final Palette PRISMARINE = new Palette(b(Blocks.PRISMARINE_BRICKS), b(Blocks.DARK_PRISMARINE), b(Blocks.SMOOTH_QUARTZ),
		b(Blocks.QUARTZ_BRICKS), b(Blocks.SEA_LANTERN), b(Blocks.CHISELED_QUARTZ_BLOCK), b(Blocks.QUARTZ_PILLAR), b(Blocks.CHISELED_QUARTZ_BLOCK),
		b(Blocks.SMOOTH_QUARTZ), Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_STAIRS, b(Blocks.PRISMARINE_WALL), b(Blocks.SEA_LANTERN),
		b(Blocks.DARK_PRISMARINE));
	private static final Palette MOSS = new Palette(b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.CHISELED_STONE_BRICKS),
		b(Blocks.MOSS_BLOCK), b(Blocks.FLOWERING_AZALEA_LEAVES).setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
		b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.STRIPPED_CHERRY_LOG), b(Blocks.CHERRY_PLANKS), b(Blocks.STONE_BRICKS), Blocks.STONE_BRICK_SLAB,
		Blocks.MOSSY_STONE_BRICK_STAIRS, b(Blocks.MOSSY_STONE_BRICK_WALL), b(Blocks.VERDANT_FROGLIGHT), b(Blocks.STONE_BRICKS));
	private static final Palette DARK_OAK = new Palette(b(Blocks.DARK_OAK_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.STRIPPED_DARK_OAK_LOG),
		b(Blocks.WOOL.red()), b(Blocks.GOLD_BLOCK), b(Blocks.STRIPPED_SPRUCE_LOG), b(Blocks.STRIPPED_DARK_OAK_LOG), b(Blocks.CHISELED_BOOKSHELF),
		b(Blocks.DARK_OAK_PLANKS), Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, b(Blocks.DARK_OAK_FENCE), b(Blocks.SHROOMLIGHT),
		b(Blocks.DARK_OAK_PLANKS));
	private static final Palette CALCITE = new Palette(b(Blocks.CALCITE), b(Blocks.POLISHED_DIORITE), b(Blocks.DYED_TERRACOTTA.white()),
		b(Blocks.DYED_TERRACOTTA.lightBlue()), b(Blocks.SEA_LANTERN), b(Blocks.SMOOTH_QUARTZ), b(Blocks.QUARTZ_PILLAR), b(Blocks.CHISELED_QUARTZ_BLOCK),
		b(Blocks.CALCITE), Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_DIORITE_STAIRS, b(Blocks.DIORITE_WALL), b(Blocks.SEA_LANTERN),
		b(Blocks.CALCITE));

	private static String[] n(String a, String b) {
		return new String[] {a, b};
	}

	private static String[] w(String... lines) {
		return lines;
	}

	/** The twenty-four halls: First Gallery (0-7), Second (8-15), Third (16-23); each eight sunwise from the east gate. */
	static final Hall[] HALLS = {
		new Hall(n("THE ARMOURY", "OF THE SEVEN"), w("seven racks,", "one empty", "THE SEVEN", "ARMED HERE", "BEFORE EVERY", "GREAT BOUT."),
			BLACKSTONE, Blocks.WALL_BANNER.red(), HallFurniture::armoury),
		new Hall(n("THE ARCHIVE OF", "THE FOUNDERS"), w("Tell's Chronicle", "is in the middle", "READ IT.", "IT TELLS HOW", "VAELOR WENT", "DOWN."),
			DARK_OAK, Blocks.WALL_BANNER.brown(), HallFurniture::archive),
		new Hall(n("THE HALL OF", "CHAMPIONS"), w("one pedestal", "is empty", "WHOEVER BEATS", "VAELOR STANDS", "HERE FOR", "EVER."),
			QUARTZ, Blocks.WALL_BANNER.yellow(), HallFurniture::champions),
		new Hall(n("THE CHAPEL OF", "THE FALLEN STAR"), w("Sister Imre's", "blessing", "SIT. BREATHE.", "THE STAR FELL", "SO WE COULD", "RISE."),
			CALCITE, Blocks.WALL_BANNER.purple(), HallFurniture::chapel),
		new Hall(n("THE WARDEN'S", "HALL"), w("Corvin's log", "is on the desk", "HE COUNTED", "EVERY ONE", "WHO WENT", "DOWN."),
			TUFF, Blocks.WALL_BANNER.gray(), HallFurniture::warden),
		new Hall(n("THE CELLS", ""), w("the Lower Door", "is in the floor", "THE WAY", "DOWN TO", "VAELOR WAS", "NEVER LOCKED."),
			DEEPSLATE, Blocks.WALL_BANNER.black(), HallFurniture::cells),
		new Hall(n("THE HALL", "OF OATHS"), w("swear on", "the stone", "WIN WELL.", "LOSE WELL.", "COME BACK", "BETTER."),
			DEEPSLATE, Blocks.WALL_BANNER.white(), HallFurniture::oaths),
		new Hall(n("THE TREASURY", "OF THE CROWN"), w("the Champion's", "Purse waits", "SET ASIDE", "IN YEAR 13", "FOR WHOEVER", "BEATS HIM."),
			SANDSTONE, Blocks.WALL_BANNER.yellow(), HallFurniture::treasury),
		new Hall(n("THE FORGE OF", "STAR-IRON"), w("where his", "blade was made", "STAR-IRON", "NEEDS A STAR'S", "HEAT. WE HAD", "ONE."),
			BRICK, Blocks.WALL_BANNER.orange(), HallFurniture::forge),
		new Hall(n("THE FEAST", "HALL"), w("the champions'", "table", "EVERY", "VICTORY ENDED", "AT THIS", "TABLE."),
			DARK_OAK, Blocks.WALL_BANNER.red(), HallFurniture::feast),
		new Hall(n("THE HANGING", "GARDENS"), w("planted by", "the Six", "SOMETHING", "GREEN, UNDER", "ALL THAT", "STONE."),
			MOSS, Blocks.WALL_BANNER.green(), HallFurniture::gardens),
		new Hall(n("THE BATHS", "OF THE CROWN"), w("rest your", "wounds", "THE WATER", "IS WARM. THE", "STAR STILL", "HEATS IT."),
			PRISMARINE, Blocks.WALL_BANNER.cyan(), HallFurniture::baths),
		new Hall(n("THE OBSERVATORY", "OF THE SEVEN"), w("where they", "watched Aster", "IT FELL IN", "YEAR 1. WE", "WATCHED IT", "FOR TWELVE."),
			BLACKSTONE, Blocks.WALL_BANNER.blue(), HallFurniture::observatory),
		new Hall(n("THE LIBRARY", "OF BOUTS"), w("every fight", "ever fought", "A THOUSAND", "BOUTS. HE", "LOST NONE", "OF THEM."),
			DARK_OAK, Blocks.WALL_BANNER.lightBlue(), HallFurniture::bouts),
		new Hall(n("THE THEATRE", "OF GLORY"), w("the great bouts,", "played again", "TONIGHT:", "THE FALL OF", "VAELOR (ONE", "DAY, MAYBE)"),
			QUARTZ, Blocks.WALL_BANNER.red(), HallFurniture::theatre),
		new Hall(n("THE", "MENAGERIE"), w("beasts of", "the old games", "THEY FOUGHT", "BEASTS HERE,", "BEFORE THEY", "FOUGHT HIM."),
			MOSS, Blocks.WALL_BANNER.lime(), HallFurniture::menagerie),
		new Hall(n("THE", "BARRACKS"), w("the gladiators'", "bunks", "LIGHTS OUT", "AT THE", "THIRD", "BELL."),
			TUFF, Blocks.WALL_BANNER.gray(), HallFurniture::barracks),
		new Hall(n("THE TRAINING", "GROUNDS"), w("train here", "before you go", "STRIKE.", "BLOCK.", "STRIKE", "AGAIN."),
			SANDSTONE, Blocks.WALL_BANNER.orange(), HallFurniture::training),
		new Hall(n("THE MAP", "ROOM"), w("the Crown,", "laid out", "YOU ARE", "HERE. HE", "IS UNDER", "THE FIELD."),
			CALCITE, Blocks.WALL_BANNER.blue(), HallFurniture::maps),
		new Hall(n("THE", "INFIRMARY"), w("for those", "who came back", "MOST OF", "THEM CAME", "BACK.", "NOT ALL."),
			CALCITE, Blocks.WALL_BANNER.white(), HallFurniture::infirmary),
		new Hall(n("THE", "KITCHENS"), w("five hundred", "fed a day", "FEED THE", "CROWD AND", "THE CROWD", "STAYS."),
			BRICK, Blocks.WALL_BANNER.yellow(), HallFurniture::kitchens),
		new Hall(n("THE CRYPT", "OF THE SIX"), w("six tombs,", "and a seventh", "THE SIX", "LIE HERE.", "THE SEVENTH", "DOES NOT."),
			DEEPSLATE, Blocks.WALL_BANNER.black(), HallFurniture::crypt),
		new Hall(n("THE COUNCIL", "OF THE SEVEN"), w("seven seats,", "one cracked", "THEY RULED", "THE CROWN", "FROM THIS", "TABLE."),
			BLACKSTONE, Blocks.WALL_BANNER.purple(), HallFurniture::council),
		new Hall(n("THE WAGERING", "HALL"), w("place your", "bets", "VAELOR TO", "WIN: 1 TO", "1000. NO", "TAKERS."),
			SANDSTONE, Blocks.WALL_BANNER.green(), HallFurniture::wagering),
	};

	/** The halls that are landmarks of the story, in the order of {@link HollowCrown}'s landmark discoveries. */
	static final int ARCHIVE = 1;
	static final int WARDEN = 4;
	static final int CHAPEL = 3;
	static final int CELLS = 5;
	static final int TREASURY = 7;
	static final int CHAMPIONS = 2;
	static final int[] LANDMARKS = {ARCHIVE, WARDEN, CHAPEL, CELLS, TREASURY, CHAMPIONS};

	/** A hall's name on two lines (for the plaques over its doors). */
	static String[] name(int hall) {
		return HALLS[hall].name;
	}

	/** Where a hall's middle is (blueprint dx, dz), at the ground floor. */
	static double[] centre(int hall) {
		int g = hall / 8;
		int q = hall / 2 % 4;
		int h = hall % 2;
		double[] band = ColosseumInterior.ROOMS[g];
		double d = (band[0] + band[1]) / 2;
		double gw = gateHalf(g);
		double len = Math.toRadians(45) * d - gw - PIER;
		double arc = gw + len / 2;
		double phi = Math.toDegrees(arc / d);
		double a = Math.toRadians(q * 90 + (h == 0 ? phi : 90 - phi));
		return new double[] {Math.cos(a) * d, Math.sin(a) * d};
	}
}

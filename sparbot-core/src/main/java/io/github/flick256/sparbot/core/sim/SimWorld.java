package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import java.util.Arrays;
import java.util.List;

/**
 * The blocks of a simulated arena: solid blocks, cobwebs, and water and lava that flow by vanilla's
 * rules. Re-done from the decompiled 26.2 code without Minecraft, for the UHC simulator:
 * <ul>
 *   <li>FlowingFluid#tick, #spread, #spreadToSides, #getNewLiquid, #getSpread, #getSlopeDistance and
 *       #isWaterHole: how far and where water (drop-off 1 a block, a step every 5 ticks, slope search 4)
 *       and overworld lava (drop-off 2, every 30 ticks, slope search 2) flow, and when two water sources
 *       make a third</li>
 *   <li>LiquidBlock#shouldSpreadLiquid: lava with water above or beside it turns to obsidian (a source)
 *       or cobblestone (flowing); LavaFluid#spreadTo: lava flowing down into water makes stone</li>
 *   <li>Level#setBlock: a change schedules a fluid tick for every fluid next to it (neighborChanged);
 *       a tick is not scheduled twice for the same block and fluid (LevelTicks)</li>
 *   <li>A cobweb has no collision box, so water and lava flow into it and wash it away
 *       (FlowingFluid#canHoldAnyFluid); its outline still stops a crosshair</li>
 *   <li>BlockGetter#clip: crosshair and bucket raycasts, with outlines (blocks, webs), colliders (solid
 *       blocks) or source fluids (an empty bucket)</li>
 * </ul>
 * Coordinates are block coordinates; outside the stored volume it is stone below and around, air above.
 */
public final class SimWorld {
	public static final byte AIR = 0;
	/** Arena floor and walls. */
	public static final byte STONE = 1;
	/** A placed building block (cobblestone, planks). */
	public static final byte COBBLE = 2;
	public static final byte OBSIDIAN = 3;
	public static final byte WEB = 4;
	public static final byte WATER = 5;
	public static final byte LAVA = 6;

	private static final int AMOUNT = 0x0F;
	private static final int SOURCE = 0x10;
	private static final int FALLING = 0x20;
	private static final int SOURCE_META = SOURCE | 8;
	private static final double EPS = 1.0E-7;
	/** Lava's height limit for being replaced by water (LavaFluid#canBeReplacedWith). */
	private static final float LAVA_REPLACE_HEIGHT = 0.44444445F;
	private static final int RING = 256;
	/** Directions: index 0-3 are Direction.Plane.HORIZONTAL's order (north, east, south, west), 4 down, 5 up. */
	private static final int[] DX = {0, 1, 0, -1, 0, 0};
	private static final int[] DY = {0, 0, 0, 0, -1, 1};
	private static final int[] DZ = {-1, 0, 1, 0, 0, 0};
	private static final int[] OPPOSITE = {2, 3, 0, 1, 5, 4};
	static final int NORTH = 0;
	static final int EAST = 1;
	static final int SOUTH = 2;
	static final int WEST = 3;
	static final int DOWN = 4;
	static final int UP = 5;

	public final int minX;
	public final int minY;
	public final int minZ;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	private final byte[] type;
	private final byte[] meta;
	/** Per block, which fluid ticks are pending (bit = fluid kind, see {@link #kind}). */
	private final byte[] pending;
	private final int[][] ring = new int[RING][];
	private final int[] ringSize = new int[RING];
	private final Rng rng;
	private long time;
	/** Set whenever a block changes; the observers rebuild what they cache. */
	private int version;

	public SimWorld(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, Rng rng) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		int n = sizeX * sizeY * sizeZ;
		this.type = new byte[n];
		this.meta = new byte[n];
		this.pending = new byte[n];
		this.rng = rng;
	}

	/**
	 * A square arena: a stone floor whose top is at y = 0 (three blocks thick), the inside {@code -half}
	 * to {@code half - 1} in x and z, and stone walls {@code wallHeight} high around it.
	 */
	public static SimWorld arena(int half, int wallHeight, Rng rng) {
		SimWorld w = new SimWorld(-half - 1, -4, -half - 1, 2 * half + 2, 4 + wallHeight + 8, 2 * half + 2, rng);
		for (int x = w.minX; x < w.minX + w.sizeX; x++) {
			for (int z = w.minZ; z < w.minZ + w.sizeZ; z++) {
				boolean wall = x < -half || x >= half || z < -half || z >= half;
				for (int y = w.minY; y < (wall ? wallHeight : 0); y++) {
					w.init(x, y, z, STONE);
				}
			}
		}
		return w;
	}

	/** Sets a block while building the arena (no fluid updates). */
	public void init(int x, int y, int z, byte t) {
		int i = index(x, y, z);
		if (i >= 0) {
			type[i] = t;
			meta[i] = (byte) (t == WATER || t == LAVA ? SOURCE_META : 0);
		}
	}

	public long time() {
		return time;
	}

	public int version() {
		return version;
	}

	int index(int x, int y, int z) {
		int ix = x - minX;
		int iy = y - minY;
		int iz = z - minZ;
		if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) {
			return -1;
		}
		return (iy * sizeZ + iz) * sizeX + ix;
	}

	private int xOf(int i) {
		return i % sizeX + minX;
	}

	private int zOf(int i) {
		return i / sizeX % sizeZ + minZ;
	}

	private int yOf(int i) {
		return i / (sizeX * sizeZ) + minY;
	}

	public byte type(int x, int y, int z) {
		int i = index(x, y, z);
		if (i < 0) {
			return y >= minY + sizeY ? AIR : STONE;
		}
		return type[i];
	}

	/** A full solid cube: collides, blocks motion and sight, holds no fluid. */
	public static boolean solid(byte t) {
		return t == STONE || t == COBBLE || t == OBSIDIAN;
	}

	public boolean solid(int x, int y, int z) {
		return solid(type(x, y, z));
	}

	/** Has an outline a crosshair stops at (a block or a web). */
	static boolean outline(byte t) {
		return solid(t) || t == WEB;
	}

	/** BlockState#isSolid (a web counts: Blocks.COBWEB forceSolidOn). */
	private static boolean solidState(byte t) {
		return solid(t) || t == WEB;
	}

	static boolean fluid(byte t) {
		return t == WATER || t == LAVA;
	}

	/** A space a block can be placed into (BlockPlaceContext#canPlace: air and fluids are replaceable). */
	public boolean replaceable(int x, int y, int z) {
		byte t = type(x, y, z);
		return t == AIR || fluid(t);
	}

	public boolean isSource(int x, int y, int z) {
		int i = index(x, y, z);
		return i >= 0 && fluid(type[i]) && (meta[i] & SOURCE) != 0;
	}

	/** Fluid amount 1-8 (a source or falling fluid is 8), 0 for no fluid. */
	private int amount(int i) {
		if (i < 0 || !fluid(type[i])) {
			return 0;
		}
		int m = meta[i];
		return (m & SOURCE) != 0 ? 8 : m & AMOUNT;
	}

	/** FlowingFluid#getHeight: a full block with the same fluid above, else amount / 9. */
	public float fluidHeight(int x, int y, int z) {
		int i = index(x, y, z);
		if (i < 0 || !fluid(type[i])) {
			return 0;
		}
		return type(x, y + 1, z) == type[i] ? 1.0F : amount(i) / 9.0F;
	}

	// ---------------------------------------------------------------- changing blocks

	/**
	 * Level#setBlock with neighbour updates: the block (and fluids next to it) get their fluid ticks.
	 * Returns false if nothing changed.
	 */
	public boolean set(int x, int y, int z, byte t) {
		return set(x, y, z, t, fluid(t) ? SOURCE_META : 0);
	}

	private boolean set(int x, int y, int z, byte t, int m) {
		int i = index(x, y, z);
		if (i < 0 || type[i] == t && meta[i] == (byte) m) {
			return false;
		}
		type[i] = t;
		meta[i] = (byte) m;
		version++;
		if (fluid(t)) {
			// LiquidBlock#onPlace.
			if (!convertLava(i)) {
				schedule(i);
			}
		}
		for (int d = 0; d < 6; d++) {
			int n = index(x + DX[d], y + DY[d], z + DZ[d]);
			if (n >= 0 && fluid(type[n])) {
				// LiquidBlock#neighborChanged.
				if (!convertLava(n)) {
					schedule(n);
				}
			}
		}
		return true;
	}

	/** LiquidBlock#shouldSpreadLiquid for lava: water above or beside it turns it to obsidian or cobblestone. */
	private boolean convertLava(int i) {
		if (type[i] != LAVA) {
			return false;
		}
		int x = xOf(i);
		int y = yOf(i);
		int z = zOf(i);
		for (int d : new int[] {UP, NORTH, SOUTH, WEST, EAST}) {
			if (type(x + DX[d], y + DY[d], z + DZ[d]) == WATER) {
				set(x, y, z, (meta[i] & SOURCE) != 0 ? OBSIDIAN : COBBLE);
				return true;
			}
		}
		return false;
	}

	/** Fluid kind for the tick queue: 0 water source, 1 flowing water, 2 lava source, 3 flowing lava. */
	private int kind(int i) {
		return (type[i] == LAVA ? 2 : 0) + ((meta[i] & SOURCE) != 0 ? 0 : 1);
	}

	private void schedule(int i) {
		int bit = 1 << kind(i);
		if ((pending[i] & bit) != 0) {
			return;
		}
		pending[i] |= (byte) bit;
		int delay = type[i] == LAVA ? 30 : 5;
		int slot = (int) ((time + delay) % RING);
		if (ring[slot] == null) {
			ring[slot] = new int[16];
		} else if (ringSize[slot] == ring[slot].length) {
			ring[slot] = Arrays.copyOf(ring[slot], ring[slot].length * 2);
		}
		ring[slot][ringSize[slot]++] = i * 4 + kind(i);
	}

	/** One game tick of the world: due fluid ticks in the order they were scheduled. */
	public void tick() {
		time++;
		int slot = (int) (time % RING);
		int count = ringSize[slot];
		if (count == 0) {
			return;
		}
		int[] due = Arrays.copyOf(ring[slot], count);
		ringSize[slot] = 0;
		for (int k = 0; k < count; k++) {
			int i = due[k] >> 2;
			int kind = due[k] & 3;
			pending[i] &= (byte) ~(1 << kind);
			// ServerLevel#tickFluid: only if the block still holds that fluid.
			if (fluid(type[i]) && kind(i) == kind) {
				tickFluid(i);
			}
		}
	}

	private static int dropOff(byte t) {
		return t == LAVA ? 2 : 1;
	}

	private static int slopeFindDistance(byte t) {
		return t == LAVA ? 2 : 4;
	}

	/** FlowingFluid#tick. */
	private void tickFluid(int i) {
		byte t = type[i];
		int x = xOf(i);
		int y = yOf(i);
		int z = zOf(i);
		if ((meta[i] & SOURCE) == 0) {
			int newMeta = newLiquid(t, x, y, z);
			if (newMeta == 0) {
				set(x, y, z, AIR, 0);
				return;
			} else if (newMeta != (meta[i] & 0xFF)) {
				// LavaFluid#getSpreadDelay would sometimes wait 4x as long, but LiquidBlock#onPlace has already
				// scheduled the tick with the plain delay by then, and a tick isn't scheduled twice.
				set(x, y, z, t, newMeta);
			}
		}
		spread(t, x, y, z);
	}

	/** canPassThroughWall for full cubes: neither side may be a solid block. */
	private boolean passWall(int x, int y, int z, int tx, int ty, int tz) {
		return !solid(type(x, y, z)) && !solid(type(tx, ty, tz));
	}

	/** canHoldAnyFluid: anything that doesn't block motion (air, webs, fluids). */
	private boolean holdsFluid(int x, int y, int z) {
		return !solid(type(x, y, z));
	}

	private boolean sourceOf(byte t, int x, int y, int z) {
		return type(x, y, z) == t && isSource(x, y, z);
	}

	private boolean maybePass(byte t, int x, int y, int z, int tx, int ty, int tz) {
		return !sourceOf(t, tx, ty, tz) && holdsFluid(tx, ty, tz) && passWall(x, y, z, tx, ty, tz);
	}

	/** FlowingFluid#getNewLiquid: the fluid meta this block would hold, 0 for none. */
	private int newLiquid(byte t, int x, int y, int z) {
		int highest = 0;
		int sources = 0;
		for (int d = 0; d < 4; d++) {
			int nx = x + DX[d];
			int nz = z + DZ[d];
			int n = index(nx, y, nz);
			if (n >= 0 && type[n] == t && passWall(x, y, z, nx, y, nz)) {
				if ((meta[n] & SOURCE) != 0) {
					sources++;
				}
				highest = Math.max(highest, amount(n));
			}
		}
		// Two sources make a third (water only: the lava source conversion game rule is off by default).
		if (sources >= 2 && t == WATER) {
			byte below = type(x, y - 1, z);
			if (solidState(below) || sourceOf(t, x, y - 1, z)) {
				return SOURCE_META;
			}
		}
		if (type(x, y + 1, z) == t && passWall(x, y, z, x, y + 1, z)) {
			return 8 | FALLING;
		}
		int amount = highest - dropOff(t);
		return amount <= 0 ? 0 : amount;
	}

	/** FluidState#canBeReplacedWith for the fluid now at the block. */
	private boolean canBeReplacedWith(int x, int y, int z, byte other, int direction) {
		byte t = type(x, y, z);
		if (t == WATER) {
			return direction == DOWN && other != WATER;
		}
		if (t == LAVA) {
			return fluidHeight(x, y, z) >= LAVA_REPLACE_HEIGHT && other == WATER;
		}
		return true;
	}

	/** FlowingFluid#spread. */
	private void spread(byte t, int x, int y, int z) {
		int i = index(x, y, z);
		boolean source = (meta[i] & SOURCE) != 0;
		if (maybePass(t, x, y, z, x, y - 1, z)) {
			int below = newLiquid(t, x, y - 1, z);
			if (below != 0 && canBeReplacedWith(x, y - 1, z, t, DOWN)) {
				spreadTo(t, x, y - 1, z, DOWN, below);
				if (sourceNeighbours(t, x, y, z) >= 3) {
					spreadToSides(t, x, y, z);
				}
				return;
			}
		}
		if (source || !waterHole(t, x, y, z, x, y - 1, z)) {
			spreadToSides(t, x, y, z);
		}
	}

	private int sourceNeighbours(byte t, int x, int y, int z) {
		int count = 0;
		for (int d = 0; d < 4; d++) {
			if (sourceOf(t, x + DX[d], y, z + DZ[d])) {
				count++;
			}
		}
		return count;
	}

	/** FlowingFluid#isWaterHole. */
	private boolean waterHole(byte t, int x, int y, int z, int bx, int by, int bz) {
		if (!passWall(x, y, z, bx, by, bz)) {
			return false;
		}
		return type(bx, by, bz) == t || holdsFluid(bx, by, bz);
	}

	/** FlowingFluid#spreadToSides with #getSpread. */
	private void spreadToSides(byte t, int x, int y, int z) {
		int i = index(x, y, z);
		int neighbour = amount(i) - dropOff(t);
		if ((meta[i] & FALLING) != 0) {
			neighbour = 7;
		}
		if (neighbour <= 0) {
			return;
		}
		int lowest = 1000;
		int[] metas = new int[4];
		boolean[] chosen = new boolean[4];
		for (int d = 0; d < 4; d++) {
			int tx = x + DX[d];
			int tz = z + DZ[d];
			if (!maybePass(t, x, y, z, tx, y, tz)) {
				continue;
			}
			int newMeta = newLiquid(t, tx, y, tz);
			int distance = waterHole(t, tx, y, tz, tx, y - 1, tz) ? 0 : slopeDistance(t, tx, y, tz, 1, OPPOSITE[d]);
			if (distance < lowest) {
				Arrays.fill(chosen, false);
			}
			if (distance <= lowest) {
				if (canBeReplacedWith(tx, y, tz, t, d)) {
					chosen[d] = true;
					metas[d] = newMeta;
				}
				lowest = distance;
			}
		}
		for (int d = 0; d < 4; d++) {
			if (chosen[d]) {
				spreadTo(t, x + DX[d], y, z + DZ[d], d, metas[d]);
			}
		}
	}

	/** FlowingFluid#getSlopeDistance: how many blocks to the nearest drop, searching a few blocks out. */
	private int slopeDistance(byte t, int x, int y, int z, int pass, int from) {
		int lowest = 1000;
		for (int d = 0; d < 4; d++) {
			if (d == from) {
				continue;
			}
			int tx = x + DX[d];
			int tz = z + DZ[d];
			if (maybePass(t, x, y, z, tx, y, tz)) {
				if (waterHole(t, tx, y, tz, tx, y - 1, tz)) {
					return pass;
				}
				if (pass < slopeFindDistance(t)) {
					lowest = Math.min(lowest, slopeDistance(t, tx, y, tz, pass + 1, OPPOSITE[d]));
				}
			}
		}
		return lowest;
	}

	/** FlowingFluid#spreadTo (and LavaFluid's: lava flowing down into water makes stone). */
	private void spreadTo(byte t, int x, int y, int z, int direction, int newMeta) {
		if (direction == DOWN && t == LAVA && type(x, y, z) == WATER) {
			set(x, y, z, STONE);
			return;
		}
		if (newMeta == 0) {
			set(x, y, z, AIR, 0);
		} else {
			set(x, y, z, t, newMeta);
		}
	}

	// ---------------------------------------------------------------- what bodies touch

	/** BlockState#isSolid... for collisions: how far a box may move along one axis before a solid block stops it. */
	double collide(int axis, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double delta) {
		if (Math.abs(delta) < EPS) {
			return 0;
		}
		double[] lo = {minX, minY, minZ};
		double[] hi = {maxX, maxY, maxZ};
		int a = axis;
		int b = (axis + 1) % 3;
		int c = (axis + 2) % 3;
		int b0 = (int) Math.floor(lo[b] + EPS);
		int b1 = (int) Math.floor(hi[b] - EPS);
		int c0 = (int) Math.floor(lo[c] + EPS);
		int c1 = (int) Math.floor(hi[c] - EPS);
		double allowed = delta;
		// Only blocks wholly beyond the face the box moves out of count (VoxelShape#collide): one the box
		// is already inside doesn't stop it.
		if (delta > 0) {
			int from = (int) Math.ceil(hi[a] - EPS);
			int to = (int) Math.floor(hi[a] + delta);
			for (int k = from; k <= to; k++) {
				if (anySolid(a, k, b, b0, b1, c, c0, c1)) {
					allowed = Math.min(allowed, k - hi[a]);
					break;
				}
			}
		} else {
			int from = (int) Math.floor(lo[a] + EPS) - 1;
			int to = (int) Math.floor(lo[a] + delta);
			for (int k = from; k >= to; k--) {
				if (anySolid(a, k, b, b0, b1, c, c0, c1)) {
					allowed = Math.max(allowed, k + 1 - lo[a]);
					break;
				}
			}
		}
		return Math.abs(allowed) < EPS ? 0 : allowed;
	}

	private boolean anySolid(int a, int k, int b, int b0, int b1, int c, int c0, int c1) {
		int[] p = new int[3];
		p[a] = k;
		for (int u = b0; u <= b1; u++) {
			p[b] = u;
			for (int v = c0; v <= c1; v++) {
				p[c] = v;
				if (solid(p[0], p[1], p[2])) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether a box overlaps any solid block (Level#noCollision, negated). */
	boolean collides(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		for (int x = (int) Math.floor(minX + EPS); x <= (int) Math.floor(maxX - EPS); x++) {
			for (int y = (int) Math.floor(minY + EPS); y <= (int) Math.floor(maxY - EPS); y++) {
				for (int z = (int) Math.floor(minZ + EPS); z <= (int) Math.floor(maxZ - EPS); z++) {
					if (solid(x, y, z)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** LevelReader#containsAnyLiquid: any water or lava in the blocks the box reaches into. */
	boolean anyFluid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		for (int x = (int) Math.floor(minX); x < (int) Math.ceil(maxX); x++) {
			for (int y = (int) Math.floor(minY); y < (int) Math.ceil(maxY); y++) {
				for (int z = (int) Math.floor(minZ); z < (int) Math.ceil(maxZ); z++) {
					if (fluid(type(x, y, z))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Whether a box overlaps a block of type {@code t}. */
	boolean touches(byte t, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
			for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
				for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
					if (type(x, y, z) == t) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** EntityFluidInteraction#update for one fluid: how deep a box is in it, eyes inside, and the current. */
	static final class FluidContact {
		double height;
		boolean eyes;
		double flowX;
		double flowZ;
		int flows;
	}

	FluidContact fluidContact(byte t, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double eyeX, double eyeY, double eyeZ) {
		FluidContact contact = new FluidContact();
		int eyeBlockX = (int) Math.floor(eyeX);
		int eyeBlockZ = (int) Math.floor(eyeZ);
		for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX) - 1; x++) {
			for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY) - 1; y++) {
				for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ) - 1; z++) {
					if (type(x, y, z) != t) {
						continue;
					}
					double top = y + fluidHeight(x, y, z);
					if (top < minY) {
						continue;
					}
					if (x == eyeBlockX && z == eyeBlockZ && eyeY >= y && eyeY <= top) {
						contact.eyes = true;
					}
					contact.height = Math.max(contact.height, top - minY);
					double[] flow = flow(x, y, z);
					double scale = contact.height < 0.4 ? contact.height : 1.0;
					contact.flowX += flow[0] * scale;
					contact.flowZ += flow[1] * scale;
					contact.flows++;
				}
			}
		}
		return contact;
	}

	/** FlowingFluid#getFlow, horizontal part, normalised (falling fluid's downward pull is left out). */
	private double[] flow(int x, int y, int z) {
		byte t = type(x, y, z);
		float own = amount(index(x, y, z)) / 9.0F;
		double fx = 0;
		double fz = 0;
		for (int d = 0; d < 4; d++) {
			int nx = x + DX[d];
			int nz = z + DZ[d];
			byte n = type(nx, y, nz);
			if (fluid(n) && n != t) {
				continue; // another fluid doesn't count (affectsFlow)
			}
			float neighbour = n == t ? amount(index(nx, y, nz)) / 9.0F : 0;
			float distance = 0;
			if (neighbour == 0) {
				if (!solid(n) && type(nx, y - 1, nz) == t) {
					float belowHeight = amount(index(nx, y - 1, nz)) / 9.0F;
					if (belowHeight > 0) {
						distance = own - (belowHeight - 0.8888889F);
					}
				}
			} else {
				distance = own - neighbour;
			}
			fx += DX[d] * distance;
			fz += DZ[d] * distance;
		}
		double len = Math.sqrt(fx * fx + fz * fz);
		return len < 1.0E-5 ? new double[] {0, 0} : new double[] {fx / len, fz / len};
	}

	// ---------------------------------------------------------------- raycasts

	/** What a raycast stops at. */
	public enum Clip {
		/** Block outlines (blocks and webs), fluids ignored: the crosshair, a filled bucket. */
		OUTLINE,
		/** Solid blocks only: line of sight, arrows. */
		COLLIDER,
		/** Outlines plus source fluids: an empty bucket. */
		SOURCE
	}

	/**
	 * A raycast hit.
	 *
	 * @param face the side of the block hit: one of {@link #NORTH}... {@link #UP}
	 */
	public record Hit(int x, int y, int z, int face, double distance, Vec3 location) {
		public BlockSpot spot() {
			return new BlockSpot(x, y, z);
		}

		/** The block next to the hit face (where a block or fluid goes). */
		public BlockSpot adjacent() {
			return new BlockSpot(x + DX[face], y + DY[face], z + DZ[face]);
		}
	}

	/** BlockGetter#clip from {@code from} along {@code dir} (a unit vector) for up to {@code max} blocks; null if nothing. */
	public Hit clip(Vec3 from, Vec3 dir, double max, Clip mode) {
		double ox = from.x();
		double oy = from.y();
		double oz = from.z();
		double dx = dir.x();
		double dy = dir.y();
		double dz = dir.z();
		int x = (int) Math.floor(ox);
		int y = (int) Math.floor(oy);
		int z = (int) Math.floor(oz);
		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
		double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
		double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
		double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : (stepX > 0 ? x + 1 - ox : ox - x) * tDeltaX;
		double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : (stepY > 0 ? y + 1 - oy : oy - y) * tDeltaY;
		double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (stepZ > 0 ? z + 1 - oz : oz - z) * tDeltaZ;
		// Starting inside a shape: VoxelShape#clip reports a hit at the start, on the side facing back.
		int startFace = nearestFace(-dx, -dy, -dz);
		Hit inside = hitInCell(x, y, z, ox, oy, oz, dx, dy, dz, 0, startFace, mode, true);
		if (inside != null) {
			return inside;
		}
		double t = 0;
		int face;
		while (true) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				t = tMaxX;
				x += stepX;
				tMaxX += tDeltaX;
				face = stepX > 0 ? WEST : EAST;
			} else if (tMaxY < tMaxZ) {
				t = tMaxY;
				y += stepY;
				tMaxY += tDeltaY;
				face = stepY > 0 ? DOWN : UP;
			} else {
				t = tMaxZ;
				z += stepZ;
				tMaxZ += tDeltaZ;
				face = stepZ > 0 ? NORTH : SOUTH;
			}
			if (t > max) {
				return null;
			}
			if (y < minY - 1 || y > minY + sizeY + 1) {
				if (stepY == 0 || y < minY - 1 && stepY < 0 || y > minY + sizeY + 1 && stepY > 0) {
					return null;
				}
			}
			Hit hit = hitInCell(x, y, z, ox, oy, oz, dx, dy, dz, t, face, mode, false);
			if (hit != null) {
				return hit.distance() <= max ? hit : null;
			}
		}
	}

	private Hit hitInCell(int x, int y, int z, double ox, double oy, double oz, double dx, double dy, double dz, double t, int face, Clip mode,
		boolean start) {
		byte b = type(x, y, z);
		boolean full = mode == Clip.COLLIDER ? solid(b) : outline(b);
		if (full) {
			if (start) {
				return new Hit(x, y, z, face, 0, new Vec3(ox, oy, oz));
			}
			return new Hit(x, y, z, face, t, new Vec3(ox + dx * t, oy + dy * t, oz + dz * t));
		}
		if (mode == Clip.SOURCE && fluid(b) && isSource(x, y, z)) {
			double h = fluidHeight(x, y, z);
			if (start) {
				return oy - y <= h ? new Hit(x, y, z, face, 0, new Vec3(ox, oy, oz)) : null;
			}
			// The fluid box is the bottom h of the block: entered through its side or top, or not at all.
			double entryY = oy + dy * t;
			if (entryY - y <= h + EPS) {
				return new Hit(x, y, z, face, t, new Vec3(ox + dx * t, entryY, oz + dz * t));
			}
			if (dy < 0) {
				double tTop = (y + h - oy) / dy;
				double ex = ox + dx * tTop;
				double ez = oz + dz * tTop;
				if (ex >= x - EPS && ex <= x + 1 + EPS && ez >= z - EPS && ez <= z + 1 + EPS) {
					return new Hit(x, y, z, UP, tTop, new Vec3(ex, y + h, ez));
				}
			}
		}
		return null;
	}

	private static int nearestFace(double x, double y, double z) {
		double ax = Math.abs(x);
		double ay = Math.abs(y);
		double az = Math.abs(z);
		if (ay >= ax && ay >= az) {
			return y > 0 ? UP : DOWN;
		}
		if (ax >= az) {
			return x > 0 ? EAST : WEST;
		}
		return z > 0 ? SOUTH : NORTH;
	}

	/** LivingEntity#hasLineOfSight: nothing solid between the two points. */
	public boolean lineOfSight(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.0E-6) {
			return true;
		}
		return clip(from, d.scale(1.0 / len), len, Clip.COLLIDER) == null;
	}

	/** Every source block of a fluid in a box (for the observers). */
	void sources(byte t, int x0, int y0, int z0, int x1, int y1, int z1, List<BlockSpot> out) {
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = index(x, y, z);
					if (i >= 0 && type[i] == t && (meta[i] & SOURCE) != 0) {
						out.add(new BlockSpot(x, y, z));
					}
				}
			}
		}
	}

	/** Every block of a type in a box (for the observers). */
	void blocks(byte t, int x0, int y0, int z0, int x1, int y1, int z1, List<BlockSpot> out) {
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = index(x, y, z);
					if (i >= 0 && type[i] == t) {
						out.add(new BlockSpot(x, y, z));
					}
				}
			}
		}
	}

	/** Counts blocks of a type (tests and statistics). */
	public int count(byte t) {
		int n = 0;
		for (byte b : type) {
			if (b == t) {
				n++;
			}
		}
		return n;
	}

	Rng rng() {
		return rng;
	}
}

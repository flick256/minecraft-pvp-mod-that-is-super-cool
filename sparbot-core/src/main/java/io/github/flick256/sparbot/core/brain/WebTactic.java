package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * UHC cobwebs: place a cobweb in the space the opponent stands in (a cobweb has no collision box, so it
 * can be placed into a player), which slows them to a crawl and stops them jumping; the melee and lava
 * tactics then take over. The best moment is right after a hit has knocked them into the air: the web
 * goes where they will land, so they fall into it. One web, then a pause before the next.
 */
public final class WebTactic implements Tactic {
	private static final double MIN_RANGE = 1.2;
	private static final double MAX_RANGE = 4.0;
	private static final int PLACE_TIMEOUT = 30;
	private static final int WEB_COOLDOWN = 80;

	private final Decision willWeb = new Decision();
	private int ticks;
	private int websBefore;
	private long lastWeb = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "web";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		InventoryState inv = c.self.inventory();
		if (c.target == null || t == null || !t.visible() || t.inWeb() || inv.hotbarSlot(ItemKind.COBWEB) < 0
			|| c.observation.tick() - lastWeb < WEB_COOLDOWN) {
			return 0;
		}
		double d = c.targetDistance();
		if (d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		if (knockedUp(c, t)) {
			// The follow-up to a hit (or a shield stun): skilled players web the landing spot.
			return c.rng.chance(c.profile.items().uhcSkill()) ? Scores.SPECIALIST + 0.06 : 0;
		}
		if (!t.onGround()) {
			return 0;
		}
		return willWeb.get(c.rng, c.profile.items().uhcSkill(), 60) ? Scores.SPECIALIST - 0.03 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		ticks = 0;
		websBefore = c.self.inventory().count(ItemKind.COBWEB);
	}

	@Override
	public void reset() {
		willWeb.reset();
		lastWeb = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (inv.count(ItemKind.COBWEB) < websBefore || ++ticks > PLACE_TIMEOUT || t == null) {
			lastWeb = c.observation.tick();
			willWeb.reset();
			return BlockPlay.position(c);
		}
		return BlockPlay.clickTop(c, target(c, t), 1.0, inv.hotbarSlot(ItemKind.COBWEB));
	}

	/** In the air shortly after our hit landed. */
	private static boolean knockedUp(BrainContext c, TargetState t) {
		return !t.onGround() && t.position().y() > c.self.position().y() + 0.3 && c.memory.ticksSinceOwnClick < 15;
	}

	/** The ground block to put the web on: under them, or under where they will land. */
	static BlockSpot target(BrainContext c, TargetState t) {
		if (t.onGround()) {
			return BlockPlay.groundUnder(t.position());
		}
		// Airborne: assume they land at the bot's own height (the arenas are flat).
		double groundTop = Math.floor(c.self.position().y() + 1.0E-3);
		Vec3 land = BlockPlay.landing(t.position(), t.velocity(), groundTop);
		return new BlockSpot((int) Math.floor(land.x()), (int) groundTop - 1, (int) Math.floor(land.z()));
	}
}

package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
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
	/** A web that won't go down this quickly (the spot is hidden behind a block, say) is given up on. */
	private static final int PLACE_TIMEOUT = 12;
	private static final int WEB_COOLDOWN = 80;

	private final Decision willWeb = new Decision();
	/** Once the cobweb is reached for, the bot sees the web through (a half-done switch costs the sword's charge for nothing). */
	private boolean placing;
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
		if (placing) {
			if (t != null && t.visible() && !t.inWeb() && inv.hotbarSlot(ItemKind.COBWEB) >= 0 && c.targetDistance() <= MAX_RANGE + 1.0) {
				return coversHeal(c) ? Scores.SURVIVAL + 0.08 : Scores.SPECIALIST + 0.08;
			}
			placing = false;
		}
		if (c.target == null || t == null || !t.visible() || t.inWeb() || inv.hotbarSlot(ItemKind.COBWEB) < 0
			|| c.observation.tick() - lastWeb < WEB_COOLDOWN) {
			return 0;
		}
		double d = c.targetDistance();
		if (d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		if (coversHeal(c)) {
			// Low with an apple to eat and them coming: web them, then eat while they cut out.
			return willWeb.get(c.rng, c.profile.items().uhcSkill(), 10) ? Scores.SURVIVAL + 0.08 : 0;
		}
		// A skilled player webs in a window that costs no hit (see BrainContext#utilityWindow).
		if (!c.utilityWindow() && c.profile.items().uhcSkill() >= 0.6) {
			return 0;
		}
		if (knockedUp(c, t)) {
			// The follow-up to a hit (or a shield stun): skilled players web the landing spot.
			return willWeb.get(c.rng, c.profile.items().uhcSkill(), 10) ? Scores.SPECIALIST + 0.06 : 0;
		}
		if (!t.onGround()) {
			return 0;
		}
		return willWeb.get(c.rng, c.profile.items().uhcSkill(), 60) ? Scores.SPECIALIST - 0.03 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		placing = true;
		ticks = 0;
		websBefore = c.self.inventory().count(ItemKind.COBWEB);
	}

	@Override
	public void onExit(BrainContext c) {
		// Something more urgent took over: the web is given up on (not picked up again later by itself).
		placing = false;
	}

	@Override
	public void reset() {
		placing = false;
		willWeb.reset();
		lastWeb = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (inv.count(ItemKind.COBWEB) < websBefore || ++ticks > PLACE_TIMEOUT || t == null) {
			placing = false;
			lastWeb = c.observation.tick();
			willWeb.reset();
			return BlockPlay.position(c);
		}
		return BlockPlay.clickTop(c, target(c, t), 1.0, inv.hotbarSlot(ItemKind.COBWEB));
	}

	/** Low enough to want an apple, with one in the hotbar, and the opponent not yet in reach. */
	static boolean coversHeal(BrainContext c) {
		return c.self.healthFraction() < c.profile.items().gappleHealthFraction() + 0.1 && HealTactic.gappleSlot(c.self.inventory()) >= 0
			&& c.targetDistance() > 2.5 && !c.self.hasEffect("minecraft:regeneration");
	}

	/** In the air shortly after our hit landed. */
	private static boolean knockedUp(BrainContext c, TargetState t) {
		return !t.onGround() && t.position().y() > c.self.position().y() + 0.3 && c.memory.ticksSinceOwnClick < 15;
	}

	/** The ground block to put the web on: under them, or under where they will land. */
	static BlockSpot target(BrainContext c, TargetState t) {
		return BlockPlay.groundBelow(c, t);
	}
}

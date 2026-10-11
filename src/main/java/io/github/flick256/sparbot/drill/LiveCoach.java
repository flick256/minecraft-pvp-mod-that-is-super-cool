package io.github.flick256.sparbot.drill;

import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.drill.DrillTally;
import io.github.flick256.sparbot.core.drill.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The coach in your ear during a fight against a bot (the {@code liveCoach} setting): a short line on the
 * action bar the moment something is worth learning from, judged the same way the drills judge a rep. It
 * points out a half-charged swing, a hit taken without a jump reset, swinging from too close, and calls the
 * windows: the bot just missed, so punish it now; it is recharging, so go in. At most one line every two
 * and a half seconds, so it teaches without drowning the fight.
 */
final class LiveCoach {
	/** Ticks between two lines. */
	static final int TIP_GAP = 50;

	/** Whether a line may be said now (rate limit per fight). */
	interface Gate {
		boolean allow(Object session, long now);
	}

	/** Every tick of a watched fight: the window calls that need no event. */
	void tick(DrillTally tally, ServerPlayer player, BotPlayer bot, ServerLevel level, Gate gate, Object session) {
		long now = level.getGameTime();
		// Low and still pressing in: a nudge to reset rather than trade.
		if (player.getHealth() <= 6 && bot.getHealth() > player.getHealth() + 6 && Cues.reach(player, bot) < 3 && gate.allow(session, now)) {
			say(player, "Low: back off and reset instead of trading hits");
		}
	}

	/** Your hit landed: was it full charge, from a good distance? */
	void hitLanded(double charge, double reach, ServerPlayer player, Object session, Gate gate) {
		Feedback.Verdict c = Feedback.charge(charge);
		if (!c.good()) {
			if (gate.allow(session, player.level().getGameTime())) {
				say(player, c.text());
			}
			return;
		}
		if (reach < 2.0 && gate.allow(session, player.level().getGameTime())) {
			say(player, Feedback.reach(reach, 2.6).text());
		}
	}

	/** You took a hit on the ground: the jump reset, or its lack ({@code offset} as in {@link Feedback#jumpReset}). */
	void jumpResetJudged(Integer offset, ServerPlayer player, Object session, Gate gate) {
		Feedback.Verdict v = Feedback.jumpReset(offset);
		if (!v.good() && gate.allow(session, player.level().getGameTime())) {
			say(player, v.text());
		}
	}

	/** The bot swung and missed: the punish window is open right now. */
	void whiffed(ServerPlayer player, Object session, Gate gate) {
		if (gate.allow(session, player.level().getGameTime())) {
			say(player, "It missed: go in and hit before it recharges!");
		}
	}

	/** Anything else worth a line. */
	void say(ServerPlayer player, Object session, Gate gate, String text) {
		if (gate.allow(session, player.level().getGameTime())) {
			say(player, text);
		}
	}

	private static void say(ServerPlayer player, String text) {
		player.sendOverlayMessage(Component.literal("Coach: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal(text).withStyle(ChatFormatting.AQUA)));
	}
}

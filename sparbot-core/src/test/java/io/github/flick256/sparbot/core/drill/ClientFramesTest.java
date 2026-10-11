package io.github.flick256.sparbot.core.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The jump reset and S-tap readings against client ticks played out with vanilla 26.2's numbers: knockback
 * from an attacker at -z (pushing +z) of 0.4 that lifts 0.4 on the ground, a jump of 0.42 that (sprinting,
 * facing the attacker) pushes 0.2 back towards it, gravity 0.08 and drag 0.98.
 */
class ClientFramesTest {
	/** Plays a player out tick by tick: who presses what, and when the knockback arrives. */
	private static final class Player {
		final List<ClientFrame> frames = new ArrayList<>();
		double y;
		double z;
		double vy;
		double vz;
		boolean onGround = true;
		int tick;

		/** One client tick: knockback arriving at its start (or not), jump held, W held, S held. */
		void tick(boolean knockback, boolean jump, boolean forward, boolean back, boolean sprinting) {
			if (knockback) {
				vz = vz / 2 + 0.4;
				if (onGround) {
					vy = Math.min(0.4, vy / 2 + 0.4);
				}
			}
			if (jump && onGround) {
				vy = Math.max(0.42, vy);
				if (sprinting) {
					vz -= 0.2;
				}
			}
			// Moving: forward is -z (towards the attacker).
			if (onGround && !knockback && !(jump)) {
				vz = vz * 0.546 + (forward ? -0.1 : 0) + (back ? 0.1 : 0);
			}
			boolean wasGround = onGround;
			y += vy;
			z += vz;
			if (y <= 0 && !(wasGround && vy > 0)) {
				y = 0;
				vy = 0;
				onGround = true;
			} else {
				onGround = false;
				vy = (vy - 0.08) * 0.98;
				vz *= 0.91;
			}
			frames.add(new ClientFrame(tick++, forward, back, false, false, jump, sprinting, sprinting, 0, y, z, onGround));
		}

		void idle(int ticks, boolean forward, boolean sprinting) {
			for (int i = 0; i < ticks; i++) {
				tick(false, false, forward, false, sprinting);
			}
		}
	}

	@Test
	void aJumpOnTheKnockbacksTickIsAPerfectReset() {
		Player p = new Player();
		p.idle(10, true, true);
		int hit = p.tick;
		p.idle(2, true, true); // the knockback is on its way (ping)
		p.tick(true, true, true, false, true);
		p.idle(15, true, true);
		ClientFrames.JumpReading r = ClientFrames.jumpReset(p.frames, hit, 0, 1);
		assertEquals(0, r.offset());
		assertTrue(r.forward() && r.sprinting());
		assertTrue(Feedback.jumpReset(r).good());
	}

	@Test
	void theKnockbackAloneLiftingYouIsNoJump() {
		Player p = new Player();
		p.idle(10, true, true);
		int hit = p.tick;
		p.idle(2, true, true);
		p.tick(true, false, true, false, true);
		p.idle(15, true, true);
		ClientFrames.JumpReading r = ClientFrames.jumpReset(p.frames, hit, 0, 1);
		assertNull(r.offset(), "a knockback lift (0.4) is not a jump (0.42)");
		assertEquals("none", Feedback.jumpReset(r).reason());
	}

	@Test
	void jumpingTwoTicksEarlyLeavesYouInTheAirForThePush() {
		Player p = new Player();
		p.idle(10, true, true);
		int hit = p.tick;
		p.tick(false, true, true, false, true);
		p.idle(1, true, true);
		p.tick(true, false, true, false, true);
		p.idle(15, true, true);
		ClientFrames.JumpReading r = ClientFrames.jumpReset(p.frames, hit, 0, 1);
		assertEquals(-2, r.offset());
		assertFalse(Feedback.jumpReset(r).good());
		assertEquals("early", Feedback.jumpReset(r).reason());
	}

	@Test
	void aJumpBeforeTheHitWasSentStillReadsEarly() {
		Player p = new Player();
		p.idle(10, true, true);
		p.tick(false, true, true, false, true);
		int hit = p.tick;
		p.idle(2, true, true);
		p.tick(true, false, true, false, true);
		p.idle(15, true, true);
		assertEquals(-3, ClientFrames.jumpReset(p.frames, hit, 0, 1).offset());
	}

	@Test
	void pressingJumpAfterTheLiftIsLate() {
		Player p = new Player();
		p.idle(10, true, true);
		int hit = p.tick;
		p.tick(true, false, true, false, true);
		p.idle(1, true, true);
		p.tick(false, true, true, false, true);
		p.idle(15, true, true);
		ClientFrames.JumpReading r = ClientFrames.jumpReset(p.frames, hit, 0, 1);
		assertEquals(2, r.offset());
		assertEquals("late", Feedback.jumpReset(r).reason());
	}

	@Test
	void onTimeWithoutSprintingDoesNotCutKnockback() {
		Player p = new Player();
		p.idle(10, false, false);
		int hit = p.tick;
		p.tick(true, true, false, false, false);
		p.idle(15, false, false);
		ClientFrames.JumpReading r = ClientFrames.jumpReset(p.frames, hit, 0, 1);
		assertEquals(0, r.offset());
		assertEquals("nowasd", Feedback.jumpReset(r).reason());
	}

	@Test
	void waitsUntilTheKnockbackHasPlayedOut() {
		Player p = new Player();
		p.idle(10, true, true);
		int hit = p.tick;
		p.idle(2, true, true);
		assertTrue(ClientFrames.jumpReset(p.frames, hit, 0, 1).pending());
		p.tick(true, true, true, false, true);
		p.idle(3, true, true);
		assertTrue(ClientFrames.jumpReset(p.frames, hit, 0, 1).pending(), "a push could still come while in the air");
	}

	@Test
	void sTapsAreTapsWithTheClick() {
		Player p = new Player();
		p.idle(10, true, true);
		int click = p.tick;
		p.tick(false, false, false, true, false);
		p.tick(false, false, false, true, false);
		p.tick(false, false, false, true, false);
		p.idle(20, true, true);
		ClientFrames.TapReading r = ClientFrames.sTap(p.frames, click);
		assertEquals(0, r.offset());
		assertEquals(3, r.held());
		assertTrue(Feedback.sTap(r).good());

		Player late = new Player();
		late.idle(10, true, true);
		int c2 = late.tick;
		late.idle(5, true, true);
		late.tick(false, false, false, true, false);
		late.idle(20, true, true);
		assertEquals("late", Feedback.sTap(ClientFrames.sTap(late.frames, c2)).reason());

		Player held = new Player();
		held.idle(10, true, true);
		int c3 = held.tick;
		for (int i = 0; i < 12; i++) {
			held.tick(false, false, false, true, false);
		}
		held.idle(10, true, true);
		assertEquals("held", Feedback.sTap(ClientFrames.sTap(held.frames, c3)).reason());

		Player none = new Player();
		none.idle(10, true, true);
		int c4 = none.tick;
		none.idle(20, true, true);
		assertEquals("none", Feedback.sTap(ClientFrames.sTap(none.frames, c4)).reason());
	}

	@Test
	void aWTapLetsGoOfW() {
		Player p = new Player();
		p.idle(10, true, true);
		p.idle(2, false, false);
		p.idle(5, true, true);
		assertTrue(ClientFrames.sprintReset(p.frames, 5, 16));
		Player h = new Player();
		h.idle(20, true, true);
		assertFalse(ClientFrames.sprintReset(h.frames, 5, 16));
	}
}

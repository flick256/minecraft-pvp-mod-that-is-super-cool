package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.record.FighterInfo;
import io.github.flick256.sparbot.core.record.Frame;
import io.github.flick256.sparbot.core.record.Recording;
import io.github.flick256.sparbot.core.record.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** Milestone 7: recording fights and replaying them. */
public class RecordGameTests {
	@GameTest(maxTicks = 200)
	public void recordsABotsInputsAndAHumansKeysAndClicks(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Rec", 2.5, 1, 3.5, -90, "pro", "basic_sword"));
		Bot target = TestSupport.dummy(helper, "RecTarget", 4.5, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 5.5, 1, 6.5);
		String label = TestSupport.uniqueName("rectest").toLowerCase();
		SparBot.recorder().start(label, "", List.of(TestSupport.body(bot), human));
		helper.startSequence()
			.thenIdle(10)
			// What a client sends when its player holds W and left-clicks.
			.thenExecute(() -> human.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(true, false, false, false, false, false, false))))
			.thenIdle(5)
			.thenExecute(() -> human.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND)))
			.thenIdle(45)
			.thenExecute(() -> {
				try {
					SparBot.recorder().stop(label);
					Recording r = SparBot.recorder().load(label).orElseThrow();
					helper.assertTrue(r.durationTicks() >= 60, "recorded every tick, got " + r.durationTicks());
					helper.assertValueEqual(r.fighters().get(0), new FighterInfo(bot.name(), true, "pro", "balanced"), "bot fighter");
					helper.assertFalse(r.fighters().get(1).bot(), "the human is marked human");
					List<Sample> bots = r.frames().stream().map(f -> f.samples()[0]).filter(Objects::nonNull).toList();
					List<Sample> humans = r.frames().stream().map(f -> f.samples()[1]).filter(Objects::nonNull).toList();
					helper.assertTrue(bots.stream().anyMatch(s -> s.inputs().attack()), "the bot's clicks were recorded");
					helper.assertTrue(bots.stream().anyMatch(s -> s.mainHand() == ItemKind.SWORD), "with its sword");
					helper.assertTrue(humans.stream().anyMatch(s -> s.inputs().forward() == 1), "the human's W key was read back");
					helper.assertTrue(humans.stream().filter(s -> s.inputs().attack()).count() == 1, "exactly one left click from the human");
				} catch (java.io.IOException e) {
					throw new IllegalStateException(e);
				}
				TestSupport.removeRealPlayer(human);
				TestSupport.remove(bot, target);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void replayMovesMannequinsAlongTheRecording(GameTestHelper helper) {
		TestSupport.arena(helper);
		List<Frame> frames = new ArrayList<>();
		for (int t = 0; t < 40; t++) {
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + t * 0.1, 1, 3.5));
			Inputs inputs = new Inputs(0, 0, 1, 0, false, false, false, t == 20, false, -1);
			Sample sample = new Sample(new io.github.flick256.sparbot.core.math.Vec3(pos.x, pos.y, pos.z), io.github.flick256.sparbot.core.math.Vec3.ZERO,
				-90, 0, 20, 0, 1, true, 0, 0, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, "minecraft:iron_sword", inputs);
			frames.add(new Frame(t, new Sample[] {sample}));
		}
		String label = TestSupport.uniqueName("replaytest").toLowerCase();
		Recording recording = new Recording(label, "", helper.getLevel().dimension().identifier().toString(), 0,
			List.of(new FighterInfo("Steve", false, "", "")), frames);
		SparBot.replays().start(helper.getLevel().getServer(), recording);
		Mannequin actor = SparBot.replays().actors(label).get(0);
		helper.startSequence()
			.thenIdle(20)
			.thenExecute(() -> {
				Vec3 expected = helper.absoluteVec(new Vec3(1.5 + 20 * 0.1, 1, 3.5));
				helper.assertTrue(actor.position().distanceTo(expected) < 0.15, "the mannequin follows the recording, at " + actor.position()
					+ " expected " + expected);
				helper.assertTrue(actor.getMainHandItem().getItem() == net.minecraft.world.item.Items.IRON_SWORD, "holding the recorded item");
			})
			.thenWaitUntil(() -> helper.assertTrue(SparBot.replays().status().stream().anyMatch(s -> s.name().equals(label) && s.paused()), "replay pauses on its final frame"))
			.thenExecute(() -> {
				helper.assertFalse(actor.isRemoved(), "the final frame remains available for review");
				SparBot.replays().stop(label);
				helper.assertTrue(actor.isRemoved(), "the mannequin is removed when the replay is stopped");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void replayPauseSeekStepAndSpeedUseDisplayedFrame(GameTestHelper helper) {
		TestSupport.arena(helper);
		Recording recording = replayFixture(helper, 8);
		String label = recording.label();
		SparBot.replays().start(helper.getLevel().getServer(), recording);
		Mannequin actor = SparBot.replays().actors(label).get(0);
		SparBot.replays().pause(label);
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertValueEqual(0, frame(label), "starts on and remains at displayed frame zero while paused");
				helper.assertTrue(SparBot.replays().step(label, 1), "step forward succeeds");
				helper.assertValueEqual(1, frame(label), "forward step selects the next displayed frame");
				helper.assertTrue(SparBot.replays().step(label, 1), "second forward step succeeds");
				helper.assertValueEqual(2, frame(label), "consecutive steps advance consecutively");
				helper.assertTrue(SparBot.replays().step(label, -1), "step backward succeeds");
				helper.assertValueEqual(1, frame(label), "back step selects the preceding displayed frame");
				helper.assertTrue(SparBot.replays().seek(label, 4), "seek succeeds");
				helper.assertValueEqual(4, frame(label), "seek selects the requested displayed frame");
				helper.assertTrue(actor.position().distanceTo(helper.absoluteVec(new Vec3(1.5 + .1 * 4, 1, 3.5))) < .15, "mannequin matches seek frame");
				helper.assertFalse(SparBot.replays().speed(label, Double.NaN), "non-finite playback speeds are rejected");
				SparBot.replays().speed(label, .5);
				SparBot.replays().resume(label);
			})
			.thenIdle(2)
			.thenExecute(() -> {
				helper.assertValueEqual(5, frame(label), "half speed advances one frame over two server ticks");
				SparBot.replays().pause(label);
				SparBot.replays().stop(label);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void recordedEventsOnlyReachSessionsContainingTheirParticipants(GameTestHelper helper) {
		TestSupport.arena(helper);
		ServerPlayer a = TestSupport.spawnRealPlayer(helper, 2.5, 1, 2.5);
		ServerPlayer b = TestSupport.spawnRealPlayer(helper, 3.5, 1, 2.5);
		ServerPlayer c = TestSupport.spawnRealPlayer(helper, 4.5, 1, 2.5);
		String together = TestSupport.uniqueName("eventsab").toLowerCase();
		String separate = TestSupport.uniqueName("eventsc").toLowerCase();
		SparBot.recorder().start(together, "", List.of(a, b));
		SparBot.recorder().start(separate, "", List.of(c));
		SparBot.recorder().event("damage", "A hit B", a.getUUID(), b.getUUID());
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			try {
				SparBot.recorder().stop(together);
				SparBot.recorder().stop(separate);
				Recording first = SparBot.recorder().load(together).orElseThrow();
				Recording second = SparBot.recorder().load(separate).orElseThrow();
				helper.assertValueEqual(1, first.events().size(), "the session recording both hit participants gets the event");
				helper.assertTrue(second.events().isEmpty(), "an unrelated simultaneous recording gets no event");
			} catch (java.io.IOException e) { throw new IllegalStateException(e); }
			TestSupport.removeRealPlayer(a); TestSupport.removeRealPlayer(b); TestSupport.removeRealPlayer(c);
		}).thenSucceed();
	}

	private static int frame(String label) {
		return SparBot.replays().status().stream().filter(s -> s.name().equals(label)).findFirst().orElseThrow().frame();
	}

	@GameTest(maxTicks = 2)
	public void eventVisibilityCommandsUpdateReviewStatus(GameTestHelper helper) {
		Recording base = replayFixture(helper, 8);
		Recording recording = new Recording(base.label(), base.mode(), base.dimension(), base.startedAtMillis(), base.fighters(), base.frames(),
			List.of(new io.github.flick256.sparbot.core.record.ReplayEvent(2, "shield_check", "observed shield calculation")));
		var server = helper.getLevel().getServer();
		SparBot.replays().start(server, recording);
		try {
			var dispatcher = server.getCommands().getDispatcher();
			var source = server.createCommandSourceStack();
			dispatcher.execute("sparbot replay event " + base.label() + " shield_check off", source);
			helper.assertTrue(SparBot.replays().status().stream().filter(s -> s.name().equals(base.label())).findFirst().orElseThrow().events().isEmpty(),
				"off command hides events in menu review status");
			dispatcher.execute("sparbot replay event " + base.label() + " shield_check on", source);
			helper.assertValueEqual(1, SparBot.replays().events(base.label()).size(), "on command restores the event");
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError("documented replay event commands must parse", e);
		} finally { SparBot.replays().stop(base.label()); }
		helper.succeed();
	}

	private static Recording replayFixture(GameTestHelper helper, int count) {
		List<Frame> frames = new ArrayList<>();
		for (int t = 0; t < count; t++) {
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + t * 0.1, 1, 3.5));
			Sample sample = new Sample(new io.github.flick256.sparbot.core.math.Vec3(pos.x, pos.y, pos.z), io.github.flick256.sparbot.core.math.Vec3.ZERO,
				-90, 0, 20, 0, 1, true, 0, 0, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, "minecraft:iron_sword", Inputs.IDLE);
			frames.add(new Frame(t, new Sample[] {sample}));
		}
		return new Recording(TestSupport.uniqueName("replaycontrols").toLowerCase(), "", helper.getLevel().dimension().identifier().toString(), 0,
			List.of(new FighterInfo("Steve", false, "", "")), frames);
	}
}

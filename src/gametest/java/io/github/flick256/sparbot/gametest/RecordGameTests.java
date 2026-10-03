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
				Vec3 expected = helper.absoluteVec(new Vec3(1.5 + 19 * 0.1, 1, 3.5));
				helper.assertTrue(actor.position().distanceTo(expected) < 0.15, "the mannequin follows the recording, at " + actor.position()
					+ " expected " + expected);
				helper.assertTrue(actor.getMainHandItem().getItem() == net.minecraft.world.item.Items.IRON_SWORD, "holding the recorded item");
			})
			.thenWaitUntil(() -> helper.assertTrue(actor.isRemoved(), "the mannequin is removed when the replay ends"))
			.thenExecute(() -> helper.assertTrue(SparBot.replays().active().isEmpty() || !SparBot.replays().active().contains(label), "replay finished"))
			.thenSucceed();
	}
}

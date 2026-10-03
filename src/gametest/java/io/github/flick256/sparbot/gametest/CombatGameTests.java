package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.BotTestAccess;
import io.github.flick256.sparbot.core.act.Inputs;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Milestone 3: shields, axes, bows, crossbows, pearls, rods, totems and block-hitting, with real items. */
public class CombatGameTests {
	private static void clearInventory(BotPlayer body) {
		Inventory inv = body.getInventory();
		for (int i = 0; i < 36; i++) {
			inv.setItem(i, ItemStack.EMPTY);
		}
		body.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}

	/** Holds right click every tick (shield up, bow drawn...) for a paused dummy. */
	private static void holdUse(GameTestHelper helper, Bot dummy) {
		helper.onEachTick(() -> {
			if (dummy.body() != null && dummy.body().isAlive() && SparBot.bots().get(dummy.name()).isPresent()) {
				BotTestAccess.press(dummy, Inputs.IDLE.withUse(true));
			}
		});
	}

	@GameTest(maxTicks = 60)
	public void raisedShieldBlocksAMeleeHitButAnAxeDisablesIt(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot attacker = TestSupport.dummy(helper, "Swinger", 2.5, 1, 3.5, -90, "sparbot_combat");
		Bot blocker = TestSupport.dummy(helper, "Wall", 4.5, 1, 3.5, 90, "sparbot_combat");
		BotPlayer a = TestSupport.body(attacker);
		BotPlayer b = TestSupport.body(blocker);
		holdUse(helper, blocker);
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> {
				helper.assertTrue(b.isBlocking(), "shield raised after the 5-tick block delay");
				a.attack(b);
				helper.assertValueEqual(b.getHealth(), b.getMaxHealth(), "sword hit fully blocked");
				helper.assertTrue(b.getOffhandItem().getDamageValue() > 0, "shield took durability damage");
				a.getInventory().setSelectedSlot(1); // diamond axe
			})
			.thenIdle(25) // full axe charge so the item swap is settled
			.thenExecute(() -> {
				b.invulnerableTime = 0;
				a.attack(b);
				helper.assertTrue(b.getCooldowns().isOnCooldown(b.getOffhandItem()), "axe hit disabled the shield");
			})
			.thenIdle(2)
			.thenExecute(() -> {
				helper.assertFalse(b.isBlocking(), "a disabled shield cannot block");
				TestSupport.remove(attacker, blocker);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void botSwitchesToItsAxeToBreakARaisedShield(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Breaker", 2.5, 1, 3.5, -90, "pro", "sparbot_combat"));
		Bot wall = TestSupport.dummy(helper, "Turtle", 5.0, 1, 3.5, 90, "sparbot_combat");
		BotPlayer w = TestSupport.body(wall);
		holdUse(helper, wall);
		bot.setAssignedTarget(w.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(w.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD)), "the bot disabled the shield with its axe");
			TestSupport.remove(bot, wall);
		});
	}

	@GameTest(maxTicks = 600, padding = 32)
	public void botShootsADistantTargetWithItsBow(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 24);
		Bot archer = TestSupport.certain(TestSupport.spawnBot(helper, "Archer", 3.5, 1, 2.5, 0, "pro", "sparbot_ranged"));
		BotPlayer body = TestSupport.body(archer);
		body.getInventory().setItem(2, ItemStack.EMPTY); // bow only (no crossbow)
		Bot target = TestSupport.dummy(helper, "Target", 3.5, 1, 20.5, 180, "basic_sword");
		archer.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(archer.stats().rangedHits() > 0, "an arrow hit the target 18 blocks away");
			helper.assertTrue(body.getInventory().countItem(Items.ARROW) < 64, "arrows were really used up");
			TestSupport.remove(archer, target);
		});
	}

	@GameTest(maxTicks = 600, padding = 32)
	public void botLoadsAndFiresACrossbow(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 24);
		Bot archer = TestSupport.certain(TestSupport.spawnBot(helper, "Xbow", 3.5, 1, 2.5, 0, "pro", "sparbot_ranged"));
		BotPlayer body = TestSupport.body(archer);
		body.getInventory().setItem(1, ItemStack.EMPTY); // crossbow only (no bow)
		Bot target = TestSupport.dummy(helper, "Target", 3.5, 1, 18.5, 180, "basic_sword");
		archer.setAssignedTarget(TestSupport.body(target).getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(archer.stats().rangedHits() > 0, "a crossbow bolt hit the target");
			TestSupport.remove(archer, target);
		});
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void botPearlsAcrossALongGap(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 36);
		Bot jumper = TestSupport.certain(TestSupport.spawnBot(helper, "Pearler", 3.5, 1, 2.5, 0, "pro", "sparbot_combat"));
		BotPlayer body = TestSupport.body(jumper);
		// Pearls and a sword only, so the test isolates the pearl decision from the bow.
		clearInventory(body);
		body.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		body.getInventory().setItem(5, new ItemStack(Items.ENDER_PEARL, 4));
		Bot target = TestSupport.dummy(helper, "Far", 3.5, 1, 32.5, 180, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		jumper.setAssignedTarget(t.getUUID());
		double startDistance = body.distanceTo(t);
		helper.succeedWhen(() -> {
			helper.assertTrue(jumper.stats().pearlsThrown() > 0, "a pearl was thrown");
			helper.assertTrue(body.distanceTo(t) < startDistance - 12, "the pearl carried the bot towards its opponent");
			TestSupport.remove(jumper, target);
		});
	}

	@GameTest(maxTicks = 400, padding = 24)
	public void botHooksAFleeingOpponentWithItsRod(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 20);
		Bot angler = TestSupport.certain(TestSupport.spawnBot(helper, "Angler", 3.5, 1, 2.5, 0, "pro", "sparbot_combat"));
		BotPlayer body = TestSupport.body(angler);
		// Rod only, so the test isolates the rod decision.
		clearInventory(body);
		body.getInventory().setItem(0, new ItemStack(Items.FISHING_ROD));
		Bot runner = TestSupport.dummy(helper, "Runner", 3.5, 1, 6.5, 0, "basic_sword");
		BotPlayer r = TestSupport.body(runner);
		angler.setAssignedTarget(r.getUUID());
		AtomicBoolean hooked = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (SparBot.bots().get(runner.name()).isPresent()) {
				BotTestAccess.press(runner, new Inputs(0, 0, 1, 0, false, false, false, false, false, -1)); // walk away (facing +z)
			}
			if (body.fishing != null && body.fishing.getHookedIn() == r) {
				hooked.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hooked.get(), "the bot's hook caught the fleeing opponent");
			TestSupport.remove(angler, runner);
		});
	}

	@GameTest(maxTicks = 200)
	public void botPutsANewTotemInItsOffhandAfterAPop(GameTestHelper helper) {
		TestSupport.arena(helper);
		ServerLevel level = helper.getLevel();
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Popper", 3.5, 1, 3.5, 0, "pro", "basic_sword"));
		BotPlayer body = TestSupport.body(bot);
		body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		body.getInventory().setItem(22, new ItemStack(Items.TOTEM_OF_UNDYING));
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				body.hurtServer(level, level.damageSources().generic(), 1000);
				helper.assertTrue(body.isAlive() && body.getOffhandItem().isEmpty(), "totem popped");
			})
			.thenWaitUntil(() -> helper.assertTrue(body.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "re-totemed"))
			.thenExecute(() -> {
				helper.assertTrue(body.getInventory().getItem(22).isEmpty(), "the spare came from the inventory");
				helper.assertValueEqual(bot.stats().totemPops(), 1, "totem pops");
				TestSupport.remove(bot);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 300, padding = 24)
	public void botRaisesItsShieldAgainstADrawnBow(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 20);
		Bot guard = TestSupport.certain(TestSupport.spawnBot(helper, "Guard", 3.5, 1, 2.5, 0, "pro", "sparbot_combat"));
		BotPlayer g = TestSupport.body(guard);
		Bot archer = TestSupport.dummy(helper, "Bowman", 3.5, 1, 15.5, 180, "sparbot_ranged");
		BotPlayer a = TestSupport.body(archer);
		a.getInventory().setSelectedSlot(1); // bow
		holdUse(helper, archer);
		guard.setAssignedTarget(a.getUUID());
		// The guard must not just shoot back: take its bow and crossbow away.
		g.getInventory().setItem(2, ItemStack.EMPTY);
		g.getInventory().setItem(3, ItemStack.EMPTY);
		helper.succeedWhen(() -> {
			helper.assertTrue(g.isBlocking(), "shield raised against the archer");
			TestSupport.remove(guard, archer);
		});
	}

	@GameTest(maxTicks = 600)
	public void botBlockHitsBetweenSwings(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot fighter = TestSupport.certain(TestSupport.spawnBot(helper, "BlockHit", 2.5, 1, 3.5, -90, "pro", "sparbot_combat"));
		Bot dummy = TestSupport.dummy(helper, "Bag", 4.8, 1, 3.5, 90, "basic_sword");
		fighter.setAssignedTarget(TestSupport.body(dummy).getUUID());
		AtomicBoolean blocked = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (fighter.body() != null && fighter.body().isBlocking()) {
				blocked.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(blocked.get(), "raised the shield between swings");
			helper.assertTrue(fighter.stats().hits() >= 2, "and still landed hits");
			TestSupport.remove(fighter, dummy);
		});
	}

	@GameTest(maxTicks = 6000)
	public void fullKitDuelPlaysOutWithRealConsumables(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot pro = TestSupport.spawnBot(helper, "KitPro", 2.5, 1, 3.5, -90, "pro", "sparbot_combat");
		Bot mid = TestSupport.spawnBot(helper, "KitMid", 5.5, 1, 4.5, 90, "intermediate", "sparbot_combat");
		pro.setAssignedTarget(TestSupport.body(mid).getUUID());
		mid.setAssignedTarget(TestSupport.body(pro).getUUID());
		helper.succeedWhen(() -> {
			boolean someoneDied = TestSupport.body(pro).isDeadOrDying() || TestSupport.body(mid).isDeadOrDying();
			helper.assertTrue(someoneDied, "duel still running");
			SparBot.LOGGER.info("Full-kit duel: {} [{}] vs {} [{}]", pro.name(), pro.stats().summary(), mid.name(), mid.stats().summary());
			TestSupport.remove(pro, mid);
		});
	}
}

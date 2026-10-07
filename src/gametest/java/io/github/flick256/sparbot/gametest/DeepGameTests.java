package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.content.SparBotContent;
import io.github.flick256.sparbot.content.StarfallItem;
import io.github.flick256.sparbot.content.VaelorBoss;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.practice.colosseum.DeepSurvey;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Deep under the Celestial Colosseum, and Vaelor:
 * <ul>
 * <li>its blueprint walks (see {@link DeepSurvey}): down to the Brink, the Leap, the arena shut in, the way back up;</li>
 * <li>his rewards hit and hold harder than anything in vanilla;</li>
 * <li>he winds up and his blows land on a challenger who stands there in the kit;</li>
 * <li>he goes through his three phases (he can't be hurt while he changes), then kneels and is gone.</li>
 * </ul>
 */
public class DeepGameTests {
	private static final int SIZE = 40;

	@GameTest(maxTicks = 40)
	public void theDeepCanBeWalked(GameTestHelper helper) {
		long started = System.currentTimeMillis();
		List<String> problems = DeepSurvey.run();
		io.github.flick256.sparbot.SparBot.LOGGER.info("Deep survey: {} problems in {} ms", problems.size(), System.currentTimeMillis() - started);
		helper.assertTrue(problems.isEmpty(), "the Deep survey found:\n" + String.join("\n", problems));
		helper.succeed();
	}

	private static double total(Item item, Holder<Attribute> attribute, EquipmentSlot slot, double base) {
		ItemAttributeModifiers mods = new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return mods.compute(attribute, base, slot);
	}

	@GameTest(maxTicks = 20)
	public void rewardsHitHarderThanAnythingInVanilla(GameTestHelper helper) {
		double oath = total(SparBotContent.OATHKEEPER, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1);
		double sword = total(Items.NETHERITE_SWORD, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1);
		double axe = total(Items.NETHERITE_AXE, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1);
		double mace = total(Items.MACE, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND, 1);
		helper.assertTrue(oath >= axe + 3 && oath >= sword + 5 && oath > mace, "Oathkeeper does " + oath + " (netherite sword " + sword + ", axe " + axe
			+ ", mace " + mace + ")");
		double armour = 0;
		double tough = 0;
		double netherite = 0;
		double netheriteTough = 0;
		Item[] ours = {SparBotContent.UNBROKEN_HELM, SparBotContent.UNBROKEN_PLATE, SparBotContent.UNBROKEN_GREAVES, SparBotContent.UNBROKEN_SABATONS};
		Item[] theirs = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			armour += total(ours[i], Attributes.ARMOR, slots[i], 0);
			tough += total(ours[i], Attributes.ARMOR_TOUGHNESS, slots[i], 0);
			netherite += total(theirs[i], Attributes.ARMOR, slots[i], 0);
			netheriteTough += total(theirs[i], Attributes.ARMOR_TOUGHNESS, slots[i], 0);
		}
		helper.assertTrue(armour > netherite && tough > netheriteTough, "the Unbroken Plate gives " + armour + " armour and " + tough
			+ " toughness (netherite " + netherite + " and " + netheriteTough + ")");
		helper.assertTrue(StarfallItem.BASE_DAMAGE >= 3.5 && StarfallItem.SPEED > 1, "Starfall's arrows do " + StarfallItem.BASE_DAMAGE + " base at "
			+ StarfallItem.SPEED + "x speed (a bow: 2 at 1x)");
		helper.assertTrue(new ItemStack(SparBotContent.HEART_OF_ASTER).getMaxStackSize() == 1, "the Heart of Aster stacks");
		helper.succeed();
	}

	/** A 40x40 floor, its chunks kept ticking (he isn't a player: nothing else would tick them), and Vaelor in the middle. */
	private static VaelorBoss boss(GameTestHelper helper) {
		TestSupport.platform(helper, SIZE, SIZE);
		ServerLevel level = helper.getLevel();
		net.minecraft.core.BlockPos a = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
		net.minecraft.core.BlockPos b2 = helper.absolutePos(new net.minecraft.core.BlockPos(SIZE, 0, SIZE));
		for (int cx = Math.min(a.getX(), b2.getX()) >> 4; cx <= Math.max(a.getX(), b2.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), b2.getZ()) >> 4; cz <= Math.max(a.getZ(), b2.getZ()) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		VaelorBoss b = SparBotContent.VAELOR.create(level, EntitySpawnReason.EVENT);
		Vec3 at = helper.absoluteVec(new Vec3(20.5, 1, 20.5));
		b.snapTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(b);
		b.bind(at, 0, at, 15);
		return b;
	}

	@GameTest(maxTicks = 420, padding = 48)
	public void vaelorWindsUpAndHisBlowsLand(GameTestHelper helper) {
		TestSupport.isolate();
		VaelorBoss b = boss(helper);
		ServerPlayer p = TestSupport.spawnRealPlayer(helper, 20.5, 1, 26.5);
		KitApplier.apply(p, TestSupport.kit("sparbot_vaelor"));
		b.challenge(p);
		Set<VaelorBoss.Action> seen = EnumSet.noneOf(VaelorBoss.Action.class);
		helper.onEachTick(() -> seen.add(b.action()));
		helper.runAfterDelay(400, () -> {
			int totems = p.getInventory().countItem(Items.TOTEM_OF_UNDYING) + (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? 1 : 0);
			boolean hurt = !p.isAlive() || p.getHealth() < p.getMaxHealth() || totems < 2;
			b.discard();
			TestSupport.removeRealPlayer(p);
			helper.assertTrue(seen.contains(VaelorBoss.Action.RISE), "he never rose from his throne: " + seen);
			helper.assertTrue(seen.stream().anyMatch(a -> a == VaelorBoss.Action.CLEAVE || a == VaelorBoss.Action.OVERHEAD || a == VaelorBoss.Action.LUNGE),
				"he never wound up a blow: " + seen);
			helper.assertTrue(hurt, "none of his blows landed on a challenger standing still (seen " + seen + ")");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 1200, padding = 48)
	public void vaelorHasThreePhasesThenKneelsAndIsGone(GameTestHelper helper) {
		TestSupport.isolate();
		VaelorBoss b = boss(helper);
		ServerPlayer p = TestSupport.spawnRealPlayer(helper, 20.5, 1, 27.5);
		p.setGameMode(GameType.CREATIVE);
		b.challenge(p);
		ServerLevel level = helper.getLevel();
		int[] state = {0};
		int[] wait = {0};
		boolean[] roarChecked = {false};
		helper.onEachTick(() -> {
			VaelorBoss.Action a = b.action();
			if (a == VaelorBoss.Action.ROAR && !roarChecked[0]) {
				roarChecked[0] = true;
				helper.assertFalse(b.hurtServer(level, p.damageSources().playerAttack(p), 50), "he could be hurt while changing phase");
			}
			if (wait[0] > 0) {
				wait[0]--;
				return;
			}
			switch (state[0]) {
				case 0 -> {
					if (a != VaelorBoss.Action.SEATED && a != VaelorBoss.Action.RISE) {
						b.hurtServer(level, p.damageSources().playerAttack(p), 250);
						state[0] = 1;
					}
				}
				case 1 -> {
					if (b.phase() == 2 && a != VaelorBoss.Action.ROAR) {
						b.hurtServer(level, p.damageSources().playerAttack(p), 250);
						state[0] = 2;
						wait[0] = 20;
					}
				}
				case 2 -> {
					if (b.phase() == 3 && a != VaelorBoss.Action.ROAR) {
						b.hurtServer(level, p.damageSources().playerAttack(p), 2000);
						state[0] = 3;
					}
				}
				case 3 -> {
					helper.assertTrue(a == VaelorBoss.Action.KNEEL && !b.defeated() && !b.isRemoved(), "he didn't kneel when he fell (" + a + ")");
					state[0] = 4;
				}
				case 4 -> {
					if (b.isRemoved()) {
						TestSupport.removeRealPlayer(p);
						helper.assertTrue(b.defeated() && p.getUUID().equals(b.killer()), "he was gone but not beaten by the challenger");
						helper.assertTrue(roarChecked[0], "he never roared between phases");
						helper.succeed();
					}
				}
				default -> {
				}
			}
		});
	}
}

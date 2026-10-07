package io.github.flick256.sparbot.content;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Everything SparBot adds to the game itself (1.2 on): Vaelor, the boss under the Celestial Colosseum, and what he
 * leaves to whoever beats him. These are real registered items and an entity, so the client needs SparBot too.
 *
 * <p>The rewards hit harder and hold better than anything in vanilla:
 * <ul>
 * <li><b>Oathkeeper</b>, his greatsword: 14 attack damage (a netherite sword does 8, an axe 10), and Starbreak on
 * use: a shockwave in front of you.</li>
 * <li><b>Starfall</b>, a bow whose arrows fly faster and hit about twice as hard as a bow's.</li>
 * <li><b>The Unbroken Plate</b>: 24 armour and 16 toughness for the set (netherite: 20 and 12), and more knockback
 * resistance.</li>
 * <li><b>The Heart of Aster</b>, the star in his chest: on use it heals, shields and hardens you, then needs a rest.</li>
 * </ul>
 */
public final class SparBotContent {
	public static final String NS = "sparbot";

	/** Vaelor's star-iron: what Oathkeeper is made of. */
	public static final ToolMaterial STAR_IRON = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4062, 9.0F, 5.0F, 18,
		ItemTags.NETHERITE_TOOL_MATERIALS);
	public static final ResourceKey<EquipmentAsset> UNBROKEN_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, id("unbroken"));
	public static final ArmorMaterial UNBROKEN = new ArmorMaterial(45, defense(4, 7, 9, 4, 20), 18, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F,
		0.15F, ItemTags.REPAIRS_NETHERITE_ARMOR, UNBROKEN_ASSET);

	public static final Item OATHKEEPER = item("oathkeeper", OathkeeperItem::new, new Item.Properties().sword(STAR_IRON, 8.0F, -2.6F)
		.fireResistant().rarity(Rarity.EPIC).component(DataComponents.LORE, lore("Vaelor's greatsword, star-iron from the first forge.",
			"Use: Starbreak, a shockwave before you.")));
	public static final Item STARFALL = item("starfall", StarfallItem::new, new Item.Properties().durability(1440).enchantable(18)
		.fireResistant().rarity(Rarity.EPIC).component(DataComponents.LORE, lore("Strung with a thread of the falling star.",
			"Its arrows fly faster and strike far harder.")));
	public static final Item UNBROKEN_HELM = armour("unbroken_helm", ArmorType.HELMET);
	public static final Item UNBROKEN_PLATE = armour("unbroken_plate", ArmorType.CHESTPLATE);
	public static final Item UNBROKEN_GREAVES = armour("unbroken_greaves", ArmorType.LEGGINGS);
	public static final Item UNBROKEN_SABATONS = armour("unbroken_sabatons", ArmorType.BOOTS);
	public static final Item HEART_OF_ASTER = item("heart_of_aster", HeartOfAsterItem::new, new Item.Properties().stacksTo(1).fireResistant()
		.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).component(DataComponents.LORE,
			lore("The star that beat in Vaelor's chest.", "Use: mends you, shields you, hardens you.", "Rests for a minute and a half after.")));

	/** Every reward, in the order they stand in the Hall of Triumph. */
	public static final List<Item> REWARDS = List.of(OATHKEEPER, STARFALL, UNBROKEN_HELM, UNBROKEN_PLATE, UNBROKEN_GREAVES, UNBROKEN_SABATONS,
		HEART_OF_ASTER);

	public static final ResourceKey<EntityType<?>> VAELOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("vaelor"));
	public static final EntityType<VaelorBoss> VAELOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, VAELOR_KEY,
		EntityType.Builder.of(VaelorBoss::new, MobCategory.MONSTER).sized(1.5F, 4.1F).eyeHeight(3.6F).fireImmune().clientTrackingRange(16)
			.build(VAELOR_KEY));

	/** His blade (a shield blocks it, and his heavy blows knock the shield aside) and his star (nothing blocks that). */
	public static final ResourceKey<DamageType> BLADE = ResourceKey.create(Registries.DAMAGE_TYPE, id("vaelor_blade"));
	public static final ResourceKey<DamageType> STAR = ResourceKey.create(Registries.DAMAGE_TYPE, id("vaelor_star"));

	public static final Holder<SoundEvent> MUSIC_VAELOR = sound("music.vaelor");
	public static final Holder<SoundEvent> MUSIC_TRIUMPH = sound("music.triumph");
	public static final Holder<SoundEvent> CROWD = sound("crowd.roar");

	private SparBotContent() {
	}

	/** Called once from the mod's initialiser, so the static registrations above run in time. */
	public static void register() {
		FabricDefaultAttributeRegistry.register(VAELOR, VaelorBoss.createAttributes());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item armour(String name, ArmorType type) {
		return item(name, Item::new, new Item.Properties().humanoidArmor(UNBROKEN, type).fireResistant().rarity(Rarity.EPIC)
			.component(DataComponents.LORE, lore("Vaelor's own plate, dented by two hundred challengers.", "None of them broke it.")));
	}

	private static Holder<SoundEvent> sound(String name) {
		Identifier id = id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static EnumMap<ArmorType, Integer> defense(int boots, int legs, int chest, int helm, int body) {
		EnumMap<ArmorType, Integer> m = new EnumMap<>(ArmorType.class);
		m.put(ArmorType.BOOTS, boots);
		m.put(ArmorType.LEGGINGS, legs);
		m.put(ArmorType.CHESTPLATE, chest);
		m.put(ArmorType.HELMET, helm);
		m.put(ArmorType.BODY, body);
		return m;
	}

	private static ItemLore lore(String... lines) {
		return new ItemLore(java.util.Arrays.stream(lines)
			.map(l -> (Component) Component.literal(l).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)).toList());
	}
}

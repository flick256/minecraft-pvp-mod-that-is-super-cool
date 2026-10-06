package io.github.flick256.sparbot.practice.colosseum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.BotPlayer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceKey;

/**
 * The Hollow Crown: the colosseum's secrets as discoveries. Each one is remembered for the player (in
 * {@code config/sparbot/hollow-crown.json}), shown as an advancement in its own tab, and rewarded on the spot
 * with a relic of the story, experience and a fanfare:
 * <ul>
 * <li>stepping under the stands for the first time (the tab's root);</li>
 * <li>each of the six landmark halls, the Lower Door and the Hall of the Fallen;</li>
 * <li>each of the four secret vaults (crouch at the cracked wall to open one);</li>
 * <li>25 and 100 different halls walked through;</li>
 * <li>beating Vaelor (see {@link VaelorFight}), and finding every secret.</li>
 * </ul>
 * Champions who beat Vaelor go up on the plaques in the Hall of Champions and the Hall of the Fallen.
 */
public final class HollowCrown {
	/** A discovery: its advancement, what it is called, what it says, and its relic (or none). */
	enum Find {
		ROOT("root", "The Hollow Crown", "Under the stands of the Celestial Colosseum", null),
		ARCHIVE("archive", "The Archive of the Founders", "Tell's Chronicle tells how Vaelor went down", Items.FEATHER),
		WARDEN("warden", "The Warden's Hall", "Corvin kept count of everyone who went down", Items.LANTERN),
		CHAPEL("chapel", "The Chapel of the Fallen Star", "Sister Imre's blessing for the road", Items.CANDLE),
		CELLS("cells", "The Cells", "The Lower Door was never locked", Items.TRIPWIRE_HOOK),
		TREASURY("treasury", "The Treasury of the Crown", "The Champion's Purse, still unclaimed", Items.GOLD_NUGGET),
		CHAMPIONS("champions", "The Hall of Champions", "One pedestal is empty", Items.PAPER),
		LOWER_DOOR("lower_door", "The Lower Door", "The stair under the field", null),
		FALLEN("fallen", "The Hall of the Fallen", "Two hundred went this way", null),
		STUDY("vault_study", "Tell's Hidden Study", "The page left out of the Chronicle", Items.AMETHYST_SHARD),
		MASONS("vault_masons", "The Masons' Vault", "Every stone carries their mark", Items.AMETHYST_SHARD),
		SEAT("vault_seat", "The Seventh Seat", "Vaelor was the seventh of the Seven", Items.AMETHYST_SHARD),
		ARMOURY("vault_armoury", "Vaelor's Armoury", "He will not go easy", Items.AMETHYST_SHARD),
		WANDERER("wanderer", "Wanderer of the Galleries", "Walk through 25 different halls", Items.MAP),
		CARTOGRAPHER("cartographer", "Cartographer of the Crown", "Walk through 100 different halls", Items.SPYGLASS),
		CHAMPION("champion", "Unbroken No More", "Beat Vaelor the Unbroken in the Heartwell", null),
		SEEKER("seeker", "Seeker of the Hollow Crown", "Find every landmark and every vault", Items.COMPASS);

		final String id;
		final String title;
		final String text;
		final Item relic;

		Find(String id, String title, String text, Item relic) {
			this.id = id;
			this.title = title;
			this.text = text;
			this.relic = relic;
		}
	}

	private static final Find[] LANDMARKS = {Find.ARCHIVE, Find.WARDEN, Find.CHAPEL, Find.CELLS, Find.TREASURY, Find.CHAMPIONS};
	private static final Find[] VAULTS = {Find.STUDY, Find.MASONS, Find.SEAT, Find.ARMOURY};

	/** What is kept: each player's discoveries and halls walked, and the champions (newest first). */
	private static final class Record {
		Map<String, Set<String>> found = new HashMap<>();
		Map<String, Set<Integer>> halls = new HashMap<>();
		List<String> champions = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path file = Path.of("config", "sparbot", "hollow-crown.json");
	private static Record record = new Record();
	private static boolean loaded;

	private final VaelorFight fight = new VaelorFight(this);
	/** How long each player has been crouched at a vault's cracked wall. */
	private final Map<UUID, Integer> kneeling = new HashMap<>();

	public HollowCrown() {
	}

	public VaelorFight fight() {
		return fight;
	}

	/** Uses {@code dir} for the record (the server's config folder), reading it if it is there. */
	public static synchronized void load(Path dir) {
		file = dir.resolve("hollow-crown.json");
		record = new Record();
		if (Files.exists(file)) {
			try {
				Record read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Record.class);
				if (read != null) {
					record = read;
				}
			} catch (IOException | RuntimeException e) {
				SparBot.LOGGER.error("Could not read {}: {}; starting the Hollow Crown afresh", file, e.getMessage());
			}
		}
		loaded = true;
	}

	private static synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(record), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not write {}: {}", file, e.getMessage());
		}
	}

	private static synchronized Set<String> found(UUID player) {
		if (!loaded) {
			load(Path.of("config", "sparbot"));
		}
		return record.found.computeIfAbsent(player.toString(), k -> new LinkedHashSet<>());
	}

	/** Whether the player has made that discovery. */
	static boolean has(ServerPlayer player, Find find) {
		return found(player.getUUID()).contains(find.id);
	}

	/** The champions' plaque: who has beaten Vaelor (the newest first). */
	static synchronized String[] plaque() {
		List<String> c = record.champions;
		if (c.isEmpty()) {
			return new String[] {"CHAMPIONS OF", "THE CROWN", "", "(none yet)"};
		}
		return new String[] {"CHAMPIONS OF", "THE CROWN", c.get(0), c.size() > 1 ? c.size() > 2 ? c.get(1) + " +" + (c.size() - 2) : c.get(1) : ""};
	}

	/** Puts a new champion's name up (once). */
	static synchronized void crown(ServerLevel level, String name) {
		record.champions.remove(name);
		record.champions.add(0, name);
		save();
		ColosseumLore.findChampionPlaques();
		ColosseumLore.rewriteChampions(level);
	}

	// --- Each second: where everyone is ---

	public void tick(MinecraftServer server, ServerLevel level) {
		fight.tick(server, level);
		long now = server.getTickCount();
		if (now % 5 == 0) {
			vaults(level);
		}
		if (now % 20 != 3) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p instanceof BotPlayer || p.isSpectator() || !ColosseumWorks.inside(p.getX(), p.getZ(), 0)) {
				continue;
			}
			where(p, level);
		}
	}

	/** What a player has walked into: under the stands, a landmark, a hall, the way down, a vault. */
	private void where(ServerPlayer p, ServerLevel level) {
		int dx = p.getBlockX() - CelestialColosseum.CX;
		int dz = p.getBlockZ() - CelestialColosseum.CZ;
		int y = p.getBlockY();
		double d = Math.sqrt((double) dx * dx + (double) dz * dz);
		double deg = Math.toDegrees(Math.atan2(dz, dx));
		boolean under = d > CelestialColosseum.PODIUM && d <= CelestialColosseum.TIER3 && y >= CelestialColosseum.F && y < ColosseumInterior.underside(d)
			&& ColosseumInterior.underside(d) > 0;
		if (under) {
			discover(p, level, Find.ROOT);
			int band = ColosseumInterior.band(ColosseumInterior.ROOMS, d);
			int k = Math.floorDiv(y - CelestialColosseum.F, ColosseumInterior.STOREY);
			int sector = ColosseumInterior.sector(deg);
			if (band >= 0 && k >= 0) {
				int landmark = Landmarks.at(band, sector, k);
				if (landmark >= 0) {
					discover(p, level, LANDMARKS[landmark]);
				} else if (!StairHall.is(band, sector)) {
					walked(p, level, RoomStyles.index(band, sector, k, false));
				}
			}
		}
		double a = dx * Heartwell.COS + dz * Heartwell.SIN;
		double lat = -dx * Heartwell.SIN + dz * Heartwell.COS;
		if (a >= Heartwell.STAIR_FOOT && a < Heartwell.STAIR_TOP - 1 && Math.abs(lat) < 2.5 && y < CelestialColosseum.F) {
			discover(p, level, Find.LOWER_DOOR);
		}
		if (a >= Heartwell.ARCH && a < Heartwell.STAIR_FOOT && Math.abs(lat) < 4.5 && y >= Heartwell.FEET && y <= Heartwell.FEET + 7) {
			discover(p, level, Find.FALLEN);
		}
		for (int q = 0; q < 4; q++) {
			double[] c = Vaults.centre(q);
			if (Math.hypot(dx + 0.5 - c[0], dz + 0.5 - c[2]) < 4.5 && y > c[1] && y <= c[1] + 6) {
				discover(p, level, VAULTS[q]);
			}
		}
	}

	/** Counts a hall walked through, and rewards 25 and 100 of them. */
	private void walked(ServerPlayer p, ServerLevel level, int hall) {
		int n;
		synchronized (HollowCrown.class) {
			Set<Integer> mine = record.halls.computeIfAbsent(p.getUUID().toString(), k -> new LinkedHashSet<>());
			if (!mine.add(hall)) {
				return;
			}
			n = mine.size();
			if (n % 5 == 0) {
				save();
			}
		}
		if (n >= 25) {
			discover(p, level, Find.WANDERER);
		}
		if (n >= 100) {
			discover(p, level, Find.CARTOGRAPHER);
		}
	}

	// --- The vaults' cracked walls ---

	/** Anyone crouched at a vault's cracked wall for a second opens it. */
	private void vaults(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p instanceof BotPlayer) {
				continue;
			}
			int at = -1;
			for (int q = 0; q < 4; q++) {
				int[] e = Vaults.entrance(q);
				double x = CelestialColosseum.CX + e[0] + 0.5;
				double z = CelestialColosseum.CZ + e[2] + 0.5;
				if (Math.hypot(p.getX() - x, p.getZ() - z) < 2.8 && Math.abs(p.getY() - e[1]) < 1.6) {
					at = q;
				}
			}
			if (at < 0 || !p.isShiftKeyDown()) {
				kneeling.remove(p.getUUID());
				continue;
			}
			int held = kneeling.merge(p.getUUID(), 5, Integer::sum);
			if (held == 10) {
				p.sendOverlayMessage(Component.literal("You listen at the stone... it is hollow.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			if (held >= 25 && open(level, at)) {
				kneeling.remove(p.getUUID());
				p.sendSystemMessage(Component.literal("The cracked stone gives way. Something is hidden behind it.").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	/** Breaks the cracked bricks of vault {@code q}'s way in; returns whether there were any left. */
	static boolean open(ServerLevel level, int q) {
		int[] e = Vaults.entrance(q);
		boolean any = false;
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 1; y++) {
					BlockPos pos = new BlockPos(CelestialColosseum.CX + e[0] + x, e[1] + y, CelestialColosseum.CZ + e[2] + z);
					if (level.getBlockState(pos).is(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), pos.getX() + 0.5, pos.getY() + 0.5,
							pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						any = true;
					}
				}
			}
		}
		if (any) {
			BlockPos at = new BlockPos(CelestialColosseum.CX + e[0], e[1], CelestialColosseum.CZ + e[2]);
			level.playSound(null, at, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.5F, 0.7F);
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.0F);
		}
		return any;
	}

	// --- Discoveries and their rewards ---

	/** Records a discovery the first time, with its advancement, relic, experience and fanfare. */
	void discover(ServerPlayer p, ServerLevel level, Find find) {
		synchronized (HollowCrown.class) {
			if (!found(p.getUUID()).add(find.id)) {
				award(p, find);
				return;
			}
			save();
		}
		award(p, find);
		int levels = find == Find.ROOT ? 1 : find == Find.CHAMPION ? 30 : find == Find.SEEKER ? 10 : find.relic == Items.AMETHYST_SHARD ? 5 : 3;
		p.giveExperienceLevels(levels);
		ItemStack relic = relic(p, find);
		if (!relic.isEmpty() && !p.getInventory().add(relic)) {
			p.drop(relic, false);
		}
		boolean big = find == Find.CHAMPION || find == Find.SEEKER || find.relic == Items.AMETHYST_SHARD;
		level.playSound(null, p.blockPosition(), big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.2F);
		int count = count(p);
		p.sendSystemMessage(Component.literal("Discovery: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(find.title).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
			.append(Component.literal("  (" + count + " of " + (LANDMARKS.length + VAULTS.length) + " secrets)").withStyle(ChatFormatting.GRAY)));
		if (find != Find.ROOT && find != Find.WANDERER && find != Find.CARTOGRAPHER) {
			title(p, Component.literal(find.title).withStyle(ChatFormatting.LIGHT_PURPLE), Component.literal(find.text).withStyle(ChatFormatting.GRAY), 10, 50, 15);
		}
		if (count == LANDMARKS.length + VAULTS.length) {
			discover(p, level, Find.SEEKER);
		}
	}

	private static int count(ServerPlayer p) {
		Set<String> mine = found(p.getUUID());
		int n = 0;
		for (Find f : LANDMARKS) {
			n += mine.contains(f.id) ? 1 : 0;
		}
		for (Find f : VAULTS) {
			n += mine.contains(f.id) ? 1 : 0;
		}
		return n;
	}

	/** Grants the discovery's advancement (and the tab's root), if the data pack's advancement is loaded. */
	static void award(ServerPlayer p, Find find) {
		MinecraftServer server = p.level().getServer();
		for (Find f : find == Find.ROOT ? new Find[] {Find.ROOT} : new Find[] {Find.ROOT, find}) {
			AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath("sparbot", "hollow_crown/" + f.id));
			if (holder != null) {
				p.getAdvancements().award(holder, "found");
			}
		}
	}

	static void title(ServerPlayer p, Component title, Component subtitle, int in, int stay, int out) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	private static final String[][] RELICS = {
		{}, {"Tell's Quill", "The Archivist wrote the Chronicle with it.", "Still sharp."},
		{"Corvin's Lantern", "The Warden's own, from three hundred", "nights of watching the stands."},
		{"Imre's Candle", "Blessed for the road down.", "\"Come back up.\""},
		{"The Lower Key", "It opens nothing.", "The door was never locked."},
		{"A Champion's Coin", "From the Champion's Purse.", "The rest waits for whoever beats Vaelor."},
		{"A Ticket Stub", "Admit one: the Grand Bowl.", "Year 112 of the Crown."},
		{}, {},
		{"Fragment of Aster", "A shard of the fallen star,", "from Tell's hidden study. (1 of 4)"},
		{"Fragment of Aster", "A shard of the fallen star,", "from the Masons' Vault. (2 of 4)"},
		{"Fragment of Aster", "A shard of the fallen star,", "from the Seventh Seat. (3 of 4)"},
		{"Fragment of Aster", "A shard of the fallen star,", "from Vaelor's Armoury. (4 of 4)"},
		{"Wanderer's Map", "Twenty-five halls walked.", "There are a thousand more."},
		{"The Cartographer's Glass", "A hundred halls walked.", "You know the stands now."},
		{}, {"Aster's Compass", "Every secret of the Hollow Crown found.", "It points to what's left: the Heartwell."}};

	/** The discovery's relic: named, with its story, shining. */
	static ItemStack relic(ServerPlayer p, Find find) {
		if (find.relic == null) {
			return ItemStack.EMPTY;
		}
		String[] text = RELICS[find.ordinal()];
		ItemStack stack = new ItemStack(find.relic);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(text[0]).withStyle(st -> st.withItalic(false).withColor(0xC77DFF)));
		List<Component> lore = new ArrayList<>();
		for (int i = 1; i < text.length; i++) {
			lore.add(Component.literal(text[i]).withStyle(st -> st.withItalic(true).withColor(ChatFormatting.GRAY)));
		}
		lore.add(Component.literal("Relic of the Hollow Crown").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.DARK_PURPLE)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		return stack;
	}

	/** Vaelor's blade, for whoever beats him. */
	static ItemStack oathkeeper(ServerPlayer p) {
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		enchant(p, sword, Enchantments.SHARPNESS, 5);
		enchant(p, sword, Enchantments.UNBREAKING, 3);
		enchant(p, sword, Enchantments.MENDING, 1);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Oathkeeper").withStyle(st -> st.withItalic(false).withColor(0xC77DFF).withBold(true)));
		sword.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Vaelor's blade. He held the heart of the").withStyle(st -> st.withItalic(true).withColor(ChatFormatting.GRAY)),
			Component.literal("star shut with it for a hundred years.").withStyle(st -> st.withItalic(true).withColor(ChatFormatting.GRAY)),
			Component.literal("Given to " + p.getPlainTextName() + ", Champion of the Crown").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)))));
		sword.set(DataComponents.RARITY, Rarity.EPIC);
		return sword;
	}

	/** The laurel crown of a champion. */
	static ItemStack laurel(ServerPlayer p) {
		ItemStack crown = new ItemStack(Items.GOLDEN_HELMET);
		enchant(p, crown, Enchantments.PROTECTION, 4);
		enchant(p, crown, Enchantments.UNBREAKING, 3);
		crown.set(DataComponents.CUSTOM_NAME, Component.literal("The Champion's Laurel").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
		crown.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Worn by the one who beat Vaelor the Unbroken.").withStyle(st -> st.withItalic(true).withColor(ChatFormatting.GRAY)))));
		crown.set(DataComponents.RARITY, Rarity.EPIC);
		return crown;
	}

	private static void enchant(ServerPlayer p, ItemStack stack, ResourceKey<Enchantment> key, int level) {
		Holder<Enchantment> holder = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		stack.enchant(holder, level);
	}
}

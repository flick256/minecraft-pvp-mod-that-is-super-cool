package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a human's own inventory safe while a match puts them in a kit. The backup is written to
 * {@code config/sparbot/match-backups/<uuid>.nbt} before the kit is applied and restored when the
 * match ends, or on the player's next join if the server stopped mid-match.
 */
public final class PlayerBackup {
	private final Path dir;

	public PlayerBackup(Path dir) {
		this.dir = dir;
	}

	private Path file(UUID uuid) {
		return dir.resolve(uuid + ".nbt");
	}

	public boolean has(UUID uuid) {
		return Files.exists(file(uuid));
	}

	/** Saves all 41 inventory slots (main, armor, offhand) plus health, food and xp. */
	public void save(ServerPlayer player) throws IOException {
		RegistryOps<Tag> ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Inventory inventory = player.getInventory();
		ListTag items = new ListTag();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			items.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, inventory.getItem(i)).getOrThrow());
		}
		CompoundTag tag = new CompoundTag();
		tag.put("items", items);
		tag.putFloat("health", player.getHealth());
		tag.putInt("food", player.getFoodData().getFoodLevel());
		tag.putFloat("saturation", player.getFoodData().getSaturationLevel());
		tag.putInt("xpLevel", player.experienceLevel);
		tag.putFloat("xpProgress", player.experienceProgress);
		Files.createDirectories(dir);
		NbtIo.writeCompressed(tag, file(player.getUUID()));
	}

	/** Restores and deletes the backup. Returns false when there is none. */
	public boolean restore(ServerPlayer player) {
		Path file = file(player.getUUID());
		if (!Files.exists(file)) {
			return false;
		}
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			RegistryOps<Tag> ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
			Inventory inventory = player.getInventory();
			ListTag items = tag.getListOrEmpty("items");
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = i < items.size() ? ItemStack.OPTIONAL_CODEC.parse(ops, items.get(i)).result().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
				inventory.setItem(i, stack);
			}
			player.removeAllEffects();
			player.setHealth(tag.getFloatOr("health", player.getMaxHealth()));
			player.getFoodData().setFoodLevel(tag.getIntOr("food", 20));
			player.getFoodData().setSaturation(tag.getFloatOr("saturation", 5));
			player.experienceLevel = tag.getIntOr("xpLevel", player.experienceLevel);
			player.experienceProgress = tag.getFloatOr("xpProgress", player.experienceProgress);
			player.inventoryMenu.broadcastChanges();
			Files.delete(file);
			return true;
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not restore match backup for {}: {}", player.getPlainTextName(), e.getMessage());
			return false;
		}
	}
}

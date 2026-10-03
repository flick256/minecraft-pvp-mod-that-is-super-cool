package io.github.flick256.sparbot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.brain.DecisionTrace;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.kit.KitApplier;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** {@code /sparbot ...}: spawn, configure, inspect and remove sparring bots. */
public final class SparBotCommand {
	private static final SimpleCommandExceptionType DISABLED = new SimpleCommandExceptionType(Component.literal("SparBot is disabled on this server"));

	private static final SuggestionProvider<CommandSourceStack> BOT_NAMES =
		(ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.bots().all().stream().map(Bot::name), builder);
	private static final SuggestionProvider<CommandSourceStack> PROFILE_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.profiles().ids(), builder);
	private static final SuggestionProvider<CommandSourceStack> KIT_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.kits().ids(), builder);

	private SparBotCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sparbot")
			.requires(source -> Commands.hasPermission(permission()).test(source));

		root.then(Commands.literal("spawn")
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> spawn(ctx, SparBot.config().defaultProfile, SparBot.config().defaultKit))
				.then(Commands.argument("profile", StringArgumentType.word()).suggests(PROFILE_IDS)
					.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "profile"), SparBot.config().defaultKit))
					.then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS)
						.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "profile"), StringArgumentType.getString(ctx, "kit")))))));

		root.then(Commands.literal("remove").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			SparBot.bots().remove(bot, "removed by " + ctx.getSource().getTextName());
			return ok(ctx, "Removed " + bot.name());
		})));

		root.then(Commands.literal("kill").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			BotPlayer body = alive(bot);
			// Same as /kill: a real death through the damage pipeline, with drops per the gamerules.
			body.kill(body.level());
			return ok(ctx, "Killed " + bot.name());
		})));

		root.then(Commands.literal("respawn").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			if (!SparBot.bots().respawn(ctx.getSource().getServer(), bot)) {
				throw new SimpleCommandExceptionType(Component.literal(bot.name() + " is not dead")).create();
			}
			return ok(ctx, "Respawned " + bot.name());
		})));

		root.then(Commands.literal("fight").then(botArgument()
			.then(Commands.argument("target", EntityArgument.entity()).executes(ctx -> {
				Bot bot = bot(ctx);
				Entity target = EntityArgument.getEntity(ctx, "target");
				if (!(target instanceof LivingEntity) || target == bot.body()) {
					throw new SimpleCommandExceptionType(Component.literal("Target must be another living entity")).create();
				}
				bot.setAssignedTarget(target.getUUID());
				return ok(ctx, bot.name() + " now fights " + target.getPlainTextName());
			}))));

		root.then(Commands.literal("stop").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			bot.setAssignedTarget(null);
			return ok(ctx, bot.name() + " has no assigned target" + (SparBot.config().autoTarget ? " (auto-targeting nearby players)" : ""));
		})));

		root.then(Commands.literal("profile").then(botArgument()
			.then(Commands.argument("profile", StringArgumentType.word()).suggests(PROFILE_IDS).executes(ctx -> {
				Bot bot = bot(ctx);
				SkillProfile profile = profile(StringArgumentType.getString(ctx, "profile"));
				bot.setProfile(profile);
				return ok(ctx, bot.name() + " now plays as " + profile.displayName());
			}))));

		root.then(Commands.literal("kit").then(botArgument()
			.then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS).executes(ctx -> {
				Bot bot = bot(ctx);
				Kit kit = kit(StringArgumentType.getString(ctx, "kit"));
				bot.setKit(kit);
				// Re-kitting is a full kit reset, only done on an operator's request.
				KitApplier.apply(alive(bot), kit);
				return ok(ctx, bot.name() + " re-equipped with " + kit.displayName() + provenanceNote(kit));
			}))));

		root.then(Commands.literal("list").executes(ctx -> {
			if (SparBot.bots().all().isEmpty()) {
				return ok(ctx, "No bots");
			}
			String list = SparBot.bots().all().stream()
				.map(b -> b.name() + " [" + b.profile().id() + ", " + b.kit().id() + (b.body() != null && b.body().isDeadOrDying() ? ", dead" : "") + "]")
				.collect(Collectors.joining(", "));
			return ok(ctx, list);
		}));

		root.then(Commands.literal("stats").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			return ok(ctx, bot.name() + ": " + bot.stats().summary());
		})));

		root.then(Commands.literal("info").then(botArgument().executes(ctx -> {
			Bot bot = bot(ctx);
			DecisionTrace trace = bot.trace();
			String scores = trace.scores().entrySet().stream()
				.map(e -> e.getKey() + "=" + String.format("%.2f", e.getValue()))
				.collect(Collectors.joining(" "));
			return ok(ctx, String.format("%s [%s/%s] tactic=%s scores{%s} dist=%.2f %s", bot.name(), bot.profile().id(), bot.kit().id(),
				trace.tactic(), scores, trace.targetDistance(), trace.note()));
		})));

		root.then(Commands.literal("profiles").executes(ctx -> ok(ctx, "Profiles: " + String.join(", ", SparBot.profiles().ids()))));
		root.then(Commands.literal("kits").executes(ctx -> ok(ctx, "Kits: " + SparBot.kits().ids().stream()
			.map(id -> id + SparBot.kits().get(id).map(SparBotCommand::provenanceNote).orElse(""))
			.collect(Collectors.joining(", ")))));

		root.then(Commands.literal("reload").executes(ctx -> {
			SparBot.reloadConfigAndProfiles();
			SparBot.reloadKits(ctx.getSource().getServer());
			return ok(ctx, "Reloaded config, " + SparBot.profiles().ids().size() + " profiles and " + SparBot.kits().ids().size() + " kits");
		}));

		dispatcher.register(root);
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, String profileId, String kitId) throws CommandSyntaxException {
		if (!SparBot.config().enabled) {
			throw DISABLED.create();
		}
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		SkillProfile profile = profile(profileId);
		Kit kit = kit(kitId);
		try {
			SparBot.bots().spawn(source.getServer(), name, source.getLevel(), source.getPosition(), source.getRotation().y, profile, kit);
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create();
		}
		return ok(ctx, "Spawned " + name + " (" + profile.displayName() + ", " + kit.displayName() + provenanceNote(kit) + ")");
	}

	private static String provenanceNote(Kit kit) {
		return kit.provenance().verified() ? "" : " [UNVERIFIED layout]";
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> botArgument() {
		return Commands.argument("bot", StringArgumentType.word()).suggests(BOT_NAMES);
	}

	private static Bot bot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "bot");
		return SparBot.bots().get(name).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No bot named " + name)).create());
	}

	private static BotPlayer alive(Bot bot) throws CommandSyntaxException {
		BotPlayer body = bot.body();
		if (body == null || body.isDeadOrDying()) {
			throw new SimpleCommandExceptionType(Component.literal(bot.name() + " is dead")).create();
		}
		return body;
	}

	private static SkillProfile profile(String id) throws CommandSyntaxException {
		return SparBot.profiles().get(id).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown profile " + id)).create());
	}

	private static Kit kit(String id) throws CommandSyntaxException {
		return SparBot.kits().get(id).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown kit " + id)).create());
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static PermissionCheck permission() {
		return switch (SparBot.config().commandPermission) {
			case "all" -> Commands.LEVEL_ALL;
			case "moderators" -> Commands.LEVEL_MODERATORS;
			case "admins" -> Commands.LEVEL_ADMINS;
			case "owners" -> Commands.LEVEL_OWNERS;
			default -> Commands.LEVEL_GAMEMASTERS;
		};
	}

}

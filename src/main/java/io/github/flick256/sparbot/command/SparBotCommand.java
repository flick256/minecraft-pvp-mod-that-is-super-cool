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
import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Layout;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.style.Playstyle;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.kit.KitCapture;
import java.io.IOException;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
	private static final SuggestionProvider<CommandSourceStack> STYLE_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.playstyles().ids(), builder);
	private static final SuggestionProvider<CommandSourceStack> MODEL_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("off"), SparBot.models().ids().stream()), builder);
	private static final SuggestionProvider<CommandSourceStack> TECHNIQUE_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Technique.values()).map(Technique::id), builder);
	private static final SuggestionProvider<CommandSourceStack> LAYOUT_IDS =
		(ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.kits().layoutIds(), builder);

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

		root.then(Commands.literal("kit")
			.then(Commands.literal("set").then(botArgument()
				.then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS).executes(ctx -> {
					Bot bot = bot(ctx);
					Kit kit = kit(StringArgumentType.getString(ctx, "kit"));
					bot.setKit(kit);
					// Re-kitting is a full kit reset, only done on an operator's request.
					KitApplier.apply(alive(bot), bot.effectiveKit());
					bot.onKitReset();
					return ok(ctx, bot.name() + " re-equipped with " + kit.displayName() + provenanceNote(kit));
				}))))
			.then(Commands.literal("give").then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS)
				.executes(ctx -> giveKit(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("players", EntityArgument.players())
					.executes(ctx -> giveKit(ctx, EntityArgument.getPlayers(ctx, "players"))))))
			.then(Commands.literal("capture").then(Commands.argument("id", StringArgumentType.word())
				.executes(ctx -> captureKit(ctx, "custom"))
				.then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> captureKit(ctx, StringArgumentType.getString(ctx, "mode"))))))
			.then(Commands.literal("info").then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS).executes(ctx -> {
				Kit kit = kit(StringArgumentType.getString(ctx, "kit"));
				Kit.Provenance p = kit.provenance();
				return ok(ctx, String.format("%s (%s): source=%s, server=%s, version=%s, confidence=%s, verified=%s%s%s", kit.id(), kit.displayName(),
					p.source(), p.server(), p.minecraftVersion(), p.confidence(), p.verified(),
					p.reference() == null ? "" : ", reference=" + p.reference(),
					p.deviations() == null || p.deviations().isEmpty() ? "" : ", deviations=" + p.deviations()));
			})))
			.then(Commands.literal("list").executes(SparBotCommand::listKits)));

		root.then(Commands.literal("model").then(botArgument()
			.executes(ctx -> {
				Bot bot = bot(ctx);
				return ok(ctx, bot.name() + " fights with " + (bot.modelId() == null ? "the scripted melee" : "the learned model " + bot.modelId())
					+ " (models: " + String.join(", ", SparBot.models().ids()) + ")");
			})
			.then(Commands.argument("model", StringArgumentType.word()).suggests(MODEL_IDS).executes(ctx -> {
				Bot bot = bot(ctx);
				String id = StringArgumentType.getString(ctx, "model");
				if (id.equals("off")) {
					bot.setModel(null, null);
					return ok(ctx, bot.name() + " now fights with the scripted melee");
				}
				bot.setModel(id, SparBot.models().get(id).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown model " + id
					+ " (models: " + String.join(", ", SparBot.models().ids()) + ")")).create()));
				return ok(ctx, bot.name() + " now fights with the learned model " + id);
			}))));
		root.then(Commands.literal("models").executes(ctx -> ok(ctx, "Models: " + String.join(", ", SparBot.models().ids()))));
		root.then(Commands.literal("train")
			.then(Commands.literal("stop").executes(ctx -> {
				io.github.flick256.sparbot.model.TrainingJob job = io.github.flick256.sparbot.model.TrainingJob.running();
				if (job == null) {
					throw new SimpleCommandExceptionType(Component.literal("Nothing is training")).create();
				}
				job.stop();
				return ok(ctx, "Stopping " + job.name() + " after the current generations; the best so far is kept");
			}))
			.then(Commands.literal("status").executes(ctx -> {
				io.github.flick256.sparbot.model.TrainingJob job = io.github.flick256.sparbot.model.TrainingJob.running();
				return ok(ctx, job == null ? "Nothing is training" : "Training " + job.name() + ": " + job.status());
			}))
			.then(Commands.literal("sword").then(trainArguments(io.github.flick256.sparbot.core.ml.Train.Mode.SWORD)))
			.then(Commands.literal("uhc").then(trainArguments(io.github.flick256.sparbot.core.ml.Train.Mode.UHC)))
			.then(trainArguments(null)));

		root.then(Commands.literal("technique").then(botArgument()
			.executes(ctx -> {
				Bot bot = bot(ctx);
				return ok(ctx, bot.name() + " techniques: " + java.util.Arrays.stream(Technique.values())
					.map(t -> t.id() + (bot.disabledTechniques().contains(t) ? " off" : " on")).collect(Collectors.joining(", ")));
			})
			.then(Commands.argument("technique", StringArgumentType.word()).suggests(TECHNIQUE_IDS)
				.then(Commands.literal("on").executes(ctx -> setTechnique(ctx, true)))
				.then(Commands.literal("off").executes(ctx -> setTechnique(ctx, false))))));

		root.then(Commands.literal("layout")
			.then(Commands.literal("set").then(botArgument()
				.then(Commands.argument("layout", StringArgumentType.word()).suggests(LAYOUT_IDS).executes(ctx -> {
					Bot bot = bot(ctx);
					String id = StringArgumentType.getString(ctx, "layout");
					Layout layout = SparBot.kits().layout(id)
						.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown layout " + id)).create());
					bot.setKit(kit(layout.kit()));
					bot.setLayout(layout);
					KitApplier.apply(alive(bot), bot.effectiveKit());
					bot.onKitReset();
					return ok(ctx, bot.name() + " now uses layout " + id + " for kit " + layout.kit());
				}))))
			.then(Commands.literal("clear").then(botArgument().executes(ctx -> {
				Bot bot = bot(ctx);
				bot.setLayout(null);
				KitApplier.apply(alive(bot), bot.effectiveKit());
				bot.onKitReset();
				return ok(ctx, bot.name() + " uses the kit's default layout");
			})))
			.then(Commands.literal("capture").then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("kit", StringArgumentType.word()).suggests(KIT_IDS).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Kit kit = kit(StringArgumentType.getString(ctx, "kit"));
					Layout layout = KitCapture.captureLayout(player, StringArgumentType.getString(ctx, "id"), kit);
					try {
						SparBot.kits().save(layout);
					} catch (IOException | IllegalArgumentException e) {
						throw new SimpleCommandExceptionType(Component.literal("Could not save layout: " + e.getMessage())).create();
					}
					return ok(ctx, "Saved layout " + layout.id() + " for kit " + kit.id() + " (" + layout.slots().size() + " slots"
						+ (layout.offhand() != null ? ", offhand " + layout.offhand() : "") + ")");
				}))))
			.then(Commands.literal("list").executes(ctx -> ok(ctx, "Layouts: " + String.join(", ", SparBot.kits().layoutIds())))));

		root.then(Commands.literal("style")
			.then(Commands.literal("set").then(botArgument()
				.then(Commands.argument("style", StringArgumentType.greedyString()).suggests(STYLE_IDS).executes(ctx -> {
					Bot bot = bot(ctx);
					Playstyle style;
					try {
						style = SparBot.playstyles().resolve(StringArgumentType.getString(ctx, "style"));
					} catch (IllegalArgumentException e) {
						throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create();
					}
					bot.setPlaystyle(style);
					return ok(ctx, bot.name() + " now fights as " + style.displayName() + " (" + style.id() + ")");
				}))))
			.then(Commands.literal("list").executes(ctx -> ok(ctx, "Playstyles: " + String.join(", ", SparBot.playstyles().ids())
				+ " (mix them, e.g. aggressive_rusher:0.7,kiter:0.3)"))));

		root.then(Commands.literal("debug")
			.then(Commands.literal("off").executes(ctx -> {
				boolean was = SparBot.debug().stop(ctx.getSource().getPlayerOrException());
				return ok(ctx, was ? "Debug view off" : "Debug view was not on");
			}))
			.then(botArgument().executes(ctx -> {
				Bot bot = bot(ctx);
				SparBot.debug().watch(ctx.getSource().getPlayerOrException(), bot);
				return ok(ctx, "Watching " + bot.name() + "'s mind on your action bar; the white spark is its crosshair. /sparbot debug off to stop");
			})));

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
			return ok(ctx, String.format("%s [%s/%s/%s] tactic=%s scores{%s} dist=%.2f %s", bot.name(), bot.profile().id(), bot.playstyle().id(), bot.kit().id(),
				trace.tactic(), scores, trace.targetDistance(), trace.note()));
		})));

		root.then(Commands.literal("profiles").executes(ctx -> ok(ctx, "Profiles: " + String.join(", ", SparBot.profiles().ids()))));
		root.then(Commands.literal("kits").executes(SparBotCommand::listKits));

		root.then(Commands.literal("reload").executes(ctx -> {
			SparBot.reloadConfigAndProfiles();
			SparBot.reloadKits(ctx.getSource().getServer());
			return ok(ctx, "Reloaded config, " + SparBot.profiles().ids().size() + " profiles and " + SparBot.kits().ids().size() + " kits");
		}));

		MatchCommands.addTo(root);
		RecordCommands.addTo(root);
		ConfigCommands.addTo(root);
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

	private static int listKits(CommandContext<CommandSourceStack> ctx) {
		return ok(ctx, "Kits: " + SparBot.kits().ids().stream()
			.map(id -> id + SparBot.kits().get(id).map(SparBotCommand::provenanceNote).orElse(""))
			.collect(Collectors.joining(", ")));
	}

	/**
	 * Equips players with a kit, as a kit menu on a PvP server does: their inventory and effects are
	 * replaced and they are healed. Refused for anyone in a match, where it would be a free reset.
	 */
	private static int giveKit(CommandContext<CommandSourceStack> ctx, java.util.Collection<ServerPlayer> players) throws CommandSyntaxException {
		Kit kit = kit(StringArgumentType.getString(ctx, "kit"));
		int given = 0;
		for (ServerPlayer player : players) {
			if (player instanceof BotPlayer) {
				continue; // bots get kits with /sparbot kit set
			}
			if (SparBot.matches().inMatch(player.getUUID())) {
				throw new SimpleCommandExceptionType(Component.literal(player.getPlainTextName() + " is in a match")).create();
			}
			KitApplier.apply(player, kit);
			given++;
		}
		if (given == 0) {
			throw new SimpleCommandExceptionType(Component.literal("No players to equip")).create();
		}
		return ok(ctx, "Equipped " + (given == 1 ? players.iterator().next().getPlainTextName() : given + " players") + " with " + kit.displayName()
			+ provenanceNote(kit));
	}

	/** {@code <name> [generations] [from]} for a training of {@code mode} (null: from the model continued from, else sword). */
	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> trainArguments(io.github.flick256.sparbot.core.ml.Train.Mode mode) {
		return Commands.argument("name", StringArgumentType.word())
			.executes(ctx -> train(ctx, mode, 200, null))
			.then(Commands.argument("generations", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 5000))
				.executes(ctx -> train(ctx, mode, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "generations"), null))
				.then(Commands.argument("from", StringArgumentType.word()).suggests(MODEL_IDS)
					.executes(ctx -> train(ctx, mode, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "generations"),
						StringArgumentType.getString(ctx, "from")))));
	}

	/** Starts training a model in the background (see TrainingJob). */
	private static int train(CommandContext<CommandSourceStack> ctx, io.github.flick256.sparbot.core.ml.Train.Mode chosen, int generations, String from)
		throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		if (!name.matches("[a-z0-9_\\-]{1,32}") || name.equals("off") || SparBot.models().bundled(name)) {
			throw new SimpleCommandExceptionType(Component.literal("Model names are 1-32 lowercase letters, digits, _ or - (and not a bundled model)")).create();
		}
		io.github.flick256.sparbot.core.ml.Mlp start = null;
		if (from != null) {
			start = SparBot.models().get(from).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown model " + from)).create());
		}
		io.github.flick256.sparbot.core.ml.Train.Mode mode = chosen != null ? chosen
			: start != null ? io.github.flick256.sparbot.core.ml.Train.Mode.of(start) : io.github.flick256.sparbot.core.ml.Train.Mode.SWORD;
		if (start != null && io.github.flick256.sparbot.core.ml.Train.Mode.of(start) != mode) {
			throw new SimpleCommandExceptionType(Component.literal(from + " is not a " + mode.id() + " model")).create();
		}
		CommandSourceStack source = ctx.getSource();
		try {
			io.github.flick256.sparbot.model.TrainingJob.start(source.getServer(), SparBot.modelsDir(), mode, name, generations, start,
				line -> source.sendSuccess(() -> Component.literal("[train " + name + "] " + line), false));
		} catch (IllegalStateException e) {
			throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create();
		}
		return ok(ctx, "Training " + mode.id() + " model " + name + " for " + generations + " generations in the background (" + (from == null ? "starting from the scripted pro"
			: "continuing from " + from) + "). Progress shows here; /sparbot train stop ends it early");
	}

	private static int setTechnique(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		Bot bot = bot(ctx);
		String id = StringArgumentType.getString(ctx, "technique");
		Technique technique = Technique.byId(id).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown technique " + id
			+ " (" + java.util.Arrays.stream(Technique.values()).map(Technique::id).collect(Collectors.joining(", ")) + ")")).create());
		java.util.EnumSet<Technique> disabled = java.util.EnumSet.noneOf(Technique.class);
		disabled.addAll(bot.disabledTechniques());
		if (on) {
			disabled.remove(technique);
		} else {
			disabled.add(technique);
		}
		bot.setDisabledTechniques(disabled);
		return ok(ctx, bot.name() + ": " + technique.id() + (on ? " on" : " off"));
	}

	private static int captureKit(CommandContext<CommandSourceStack> ctx, String mode) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		Kit kit = KitCapture.captureKit(player, id, mode);
		try {
			SparBot.kits().save(kit, ctx.getSource().getServer().registryAccess());
		} catch (IOException | IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal("Could not save kit: " + e.getMessage())).create();
		}
		return ok(ctx, "Saved kit " + id + " from your inventory (" + kit.slots().size() + " stacks) to config/sparbot/kits/" + id + ".json");
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

	/** Whether {@code source} may use /sparbot (and see the menu). */
	public static boolean allowed(CommandSourceStack source) {
		return Commands.hasPermission(permission()).test(source);
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

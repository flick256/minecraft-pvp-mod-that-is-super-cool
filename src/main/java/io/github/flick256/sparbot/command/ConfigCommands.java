package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.config.ConfigEditor;
import io.github.flick256.sparbot.menu.MenuSync;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /sparbot config [get <key> | set <key> <value>]} and {@code /sparbot menu}. */
final class ConfigCommands {
	private static final SuggestionProvider<CommandSourceStack> KEYS = (ctx, b) -> SharedSuggestionProvider.suggest(ConfigEditor.keys(), b);

	private ConfigCommands() {
	}

	static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("config")
			.executes(ctx -> ok(ctx, ConfigEditor.settings(SparBot.config()).stream().map(s -> s.key() + "=" + s.value()).collect(Collectors.joining(", "))))
			.then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS).executes(ctx -> {
				String key = StringArgumentType.getString(ctx, "key");
				return ok(ctx, key + " = " + ConfigEditor.get(SparBot.config(), key).orElseThrow(() -> fail("Unknown setting " + key)));
			})))
			.then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
				.then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
					String key = StringArgumentType.getString(ctx, "key");
					String value = StringArgumentType.getString(ctx, "value");
					checkReferences(key, value.trim());
					try {
						ConfigEditor.set(SparBot.config(), key, value);
					} catch (IllegalArgumentException e) {
						throw fail(e.getMessage());
					}
					SparBot.saveConfig();
					return ok(ctx, key + " = " + ConfigEditor.get(SparBot.config(), key).orElse(value) + " (saved)");
				})))));

		root.then(Commands.literal("menu").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!MenuSync.canShow(player)) {
				throw fail("The menu needs SparBot on your client too; every option is also a /sparbot command");
			}
			MenuSync.send(player, true);
			return 1;
		}));
	}

	/** Defaults must name things that exist. */
	private static void checkReferences(String key, String value) throws CommandSyntaxException {
		boolean known = switch (key) {
			case "defaultProfile" -> SparBot.profiles().get(value).isPresent();
			case "defaultKit" -> SparBot.kits().get(value).isPresent();
			case "defaultModel" -> value.isBlank() || "none".equals(value) || SparBot.models().get(value).isPresent();
			case "defaultPlaystyle" -> {
				try {
					SparBot.playstyles().resolve(value);
					yield true;
				} catch (IllegalArgumentException e) {
					yield false;
				}
			}
			default -> true;
		};
		if (!known) {
			throw fail("No such " + key.substring("default".length()).toLowerCase() + ": " + value);
		}
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}
}

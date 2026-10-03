package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.record.FighterInfo;
import io.github.flick256.sparbot.core.record.Recording;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /sparbot record start|stop|list} and {@code /sparbot replay <name>|stop}. */
final class RecordCommands {
	private static final SuggestionProvider<CommandSourceStack> SAVED = (ctx, b) -> {
		try {
			return SharedSuggestionProvider.suggest(SparBot.recorder().saved(), b);
		} catch (IOException e) {
			return b.buildFuture();
		}
	};
	private static final SuggestionProvider<CommandSourceStack> RECORDING =
		(ctx, b) -> SharedSuggestionProvider.suggest(SparBot.recorder().active(), b);
	private static final SuggestionProvider<CommandSourceStack> REPLAYING =
		(ctx, b) -> SharedSuggestionProvider.suggest(SparBot.replays().active(), b);

	private RecordCommands() {
	}

	static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("record")
			.then(Commands.literal("start").then(Commands.argument("name", StringArgumentType.word())
				.then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					List<ServerPlayer> players = new ArrayList<>(EntityArgument.getPlayers(ctx, "players"));
					try {
						SparBot.recorder().start(name, "", players);
					} catch (IllegalArgumentException | IllegalStateException e) {
						throw fail(e.getMessage());
					}
					return ok(ctx, "Recording " + name + ": " + players.stream().map(ServerPlayer::getPlainTextName).collect(Collectors.joining(", "))
						+ ". Stop with /sparbot record stop " + name);
				}))))
			.then(Commands.literal("stop").then(Commands.argument("name", StringArgumentType.word()).suggests(RECORDING).executes(ctx -> {
				String name = StringArgumentType.getString(ctx, "name");
				try {
					Path file = SparBot.recorder().stop(name);
					return ok(ctx, "Saved " + name + " to " + file);
				} catch (IllegalStateException | IOException e) {
					throw fail(e.getMessage());
				}
			})))
			.then(Commands.literal("list").executes(ctx -> {
				try {
					List<String> saved = SparBot.recorder().saved();
					List<String> active = SparBot.recorder().active();
					return ok(ctx, "Recordings: " + (saved.isEmpty() ? "none" : String.join(", ", saved))
						+ (active.isEmpty() ? "" : "; recording now: " + String.join(", ", active)));
				} catch (IOException e) {
					throw fail(e.getMessage());
				}
			})));

		root.then(Commands.literal("replay")
			.then(Commands.literal("stop").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING).executes(ctx -> {
				String name = StringArgumentType.getString(ctx, "name");
				if (!SparBot.replays().stop(name)) {
					throw fail("Not replaying " + name);
				}
				return ok(ctx, "Stopped the replay of " + name);
			})))
			.then(Commands.argument("name", StringArgumentType.word()).suggests(SAVED).executes(ctx -> {
				String name = StringArgumentType.getString(ctx, "name");
				Recording recording;
				try {
					recording = SparBot.recorder().load(name).orElseThrow(() -> fail("No recording named " + name));
				} catch (IOException e) {
					throw fail("Could not read " + name + ": " + e.getMessage());
				}
				try {
					SparBot.replays().start(ctx.getSource().getServer(), recording);
				} catch (IllegalStateException e) {
					throw fail(e.getMessage());
				}
				return ok(ctx, "Replaying " + name + " (" + recording.fighters().stream().map(FighterInfo::name).collect(Collectors.joining(" vs "))
					+ ", " + recording.durationTicks() / 20 + " s) where it was recorded");
			})));
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}
}

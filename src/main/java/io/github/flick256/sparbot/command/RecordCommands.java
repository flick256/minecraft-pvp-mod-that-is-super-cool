package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
			.then(Commands.literal("pause").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING).executes(ctx -> toggle(ctx, false))))
			.then(Commands.literal("resume").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING).executes(ctx -> toggle(ctx, true))))
			.then(Commands.literal("speed").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING)
				.then(Commands.argument("value", DoubleArgumentType.doubleArg(.25, 2)).executes(ctx -> {
					String n = StringArgumentType.getString(ctx, "name"); double v = DoubleArgumentType.getDouble(ctx, "value");
					if (!SparBot.replays().speed(n, v)) throw fail("Speed must be .25, .5, 1, or 2 and replay must be active");
					return ok(ctx, "Replay " + n + " speed set to " + v);
				}))))
			.then(Commands.literal("seek").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING)
				.then(Commands.argument("tick", IntegerArgumentType.integer(0)).executes(ctx -> {
					String n = StringArgumentType.getString(ctx, "name"); int t = IntegerArgumentType.getInteger(ctx, "tick");
					if (!SparBot.replays().seek(n, t)) throw fail("Not replaying " + n);
					return ok(ctx, "Replay " + n + " seeked to tick " + t);
				}))))
			.then(Commands.literal("step").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING).executes(ctx -> step(ctx, 1))
				.then(Commands.literal("back").executes(ctx -> step(ctx, -1)))))
			.then(Commands.literal("event").then(Commands.argument("name", StringArgumentType.word()).suggests(REPLAYING)
				.then(Commands.argument("kind", StringArgumentType.word())
					.then(Commands.literal("on").executes(ctx -> event(ctx, true)))
					.then(Commands.literal("off").executes(ctx -> event(ctx, false))))))
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
	private static int toggle(CommandContext<CommandSourceStack> ctx, boolean resume) throws CommandSyntaxException {
		String n = StringArgumentType.getString(ctx, "name");
		if (!(resume ? SparBot.replays().resume(n) : SparBot.replays().pause(n))) throw fail("Not replaying " + n);
		return ok(ctx, (resume ? "Resumed " : "Paused ") + n);
	}
	private static int step(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
		String n = StringArgumentType.getString(ctx, "name");
		if (!SparBot.replays().step(n, amount)) throw fail("Not replaying " + n);
		return ok(ctx, "Stepped " + n + (amount < 0 ? " backward" : " forward"));
	}
	private static int event(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
		String n = StringArgumentType.getString(ctx, "name"); String kind = StringArgumentType.getString(ctx, "kind");
		if (!SparBot.replays().setEventEnabled(n, kind, enabled)) throw fail("Not replaying " + n);
		return ok(ctx, "Replay " + n + " event " + kind + (enabled ? " enabled" : " hidden"));
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}
}

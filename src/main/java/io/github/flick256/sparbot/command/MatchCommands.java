package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.stats.EloLadder;
import io.github.flick256.sparbot.core.style.Playstyle;
import io.github.flick256.sparbot.match.Arena;
import io.github.flick256.sparbot.match.Benchmark;
import io.github.flick256.sparbot.match.Fighter;
import io.github.flick256.sparbot.match.Match;
import io.github.flick256.sparbot.match.MatchManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** {@code /sparbot arena|match|modes|elo|benchmark}. */
final class MatchCommands {
	private static final SuggestionProvider<CommandSourceStack> ARENAS =
		(ctx, b) -> SharedSuggestionProvider.suggest(SparBot.matches().arenas().ids(), b);
	private static final SuggestionProvider<CommandSourceStack> MODES =
		(ctx, b) -> SharedSuggestionProvider.suggest(SparBot.matches().modes().ids(), b);
	private static final SuggestionProvider<CommandSourceStack> FIGHTERS = (ctx, b) -> SharedSuggestionProvider.suggest(
		Stream.concat(SparBot.bots().all().stream().map(io.github.flick256.sparbot.bot.Bot::name),
			ctx.getSource().getServer().getPlayerList().getPlayers().stream().map(ServerPlayer::getPlainTextName)).distinct(), b);
	private static final SuggestionProvider<CommandSourceStack> PROFILES = (ctx, b) -> SharedSuggestionProvider.suggest(SparBot.profiles().ids(), b);
	private static final SuggestionProvider<CommandSourceStack> STYLES = (ctx, b) -> SharedSuggestionProvider.suggest(SparBot.playstyles().ids(), b);

	private MatchCommands() {
	}

	static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("arena")
			.then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
				.then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(ctx -> {
					try {
						Arena arena = SparBot.matches().arenas().create(StringArgumentType.getString(ctx, "id"), ctx.getSource().getLevel(),
							BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"));
						return ok(ctx, "Arena " + arena.id() + " saved (" + (arena.maxX() - arena.minX() + 1) + "x" + (arena.maxY() - arena.minY() + 1) + "x"
							+ (arena.maxZ() - arena.minZ() + 1) + "). Now stand at each spawn and run /sparbot arena spawn " + arena.id() + " a|b");
					} catch (IOException | IllegalArgumentException e) {
						throw fail(e.getMessage());
					}
				})))))
			.then(Commands.literal("spawn").then(Commands.argument("id", StringArgumentType.word()).suggests(ARENAS)
				.then(Commands.literal("a").executes(ctx -> spawn(ctx, true)))
				.then(Commands.literal("b").executes(ctx -> spawn(ctx, false)))))
			.then(Commands.literal("reset").then(Commands.argument("id", StringArgumentType.word()).suggests(ARENAS).executes(ctx -> {
				Arena arena = arena(ctx);
				int removed = SparBot.matches().arenas().reset(ctx.getSource().getServer(), arena);
				return ok(ctx, "Arena " + arena.id() + " restored; removed " + removed + " loose entities");
			})))
			.then(Commands.literal("list").executes(ctx -> ok(ctx, "Arenas: " + SparBot.matches().arenas().ids().stream()
				.map(id -> id + (SparBot.matches().arenas().get(id).map(Arena::ready).orElse(false) ? "" : " (needs spawns)"))
				.collect(Collectors.joining(", "))))));

		root.then(Commands.literal("match")
			.then(Commands.literal("start").then(Commands.argument("mode", StringArgumentType.word()).suggests(MODES)
				.then(Commands.argument("arena", StringArgumentType.word()).suggests(ARENAS)
					.then(Commands.argument("a", StringArgumentType.word()).suggests(FIGHTERS)
						.then(Commands.argument("b", StringArgumentType.word()).suggests(FIGHTERS).executes(ctx -> {
							GameMode mode = mode(ctx);
							Arena arena = arena(ctx, "arena");
							Fighter a = fighter(ctx, StringArgumentType.getString(ctx, "a"));
							Fighter b = fighter(ctx, StringArgumentType.getString(ctx, "b"));
							try {
								SparBot.matches().start(ctx.getSource().getServer(), mode, arena, a, b, null);
							} catch (IOException | IllegalArgumentException e) {
								throw fail(e.getMessage());
							}
							return ok(ctx, mode.displayName() + ": " + a.name() + " vs " + b.name() + " in " + arena.id() + ", first to " + mode.roundsToWin());
						}))))))
			.then(Commands.literal("stop").then(Commands.argument("arena", StringArgumentType.word()).suggests(ARENAS).executes(ctx -> {
				String id = StringArgumentType.getString(ctx, "arena");
				if (!SparBot.matches().stop(ctx.getSource().getServer(), id)) {
					throw fail("No match in arena " + id);
				}
				return ok(ctx, "Match in " + id + " stopped (unrated)");
			})))
			.then(Commands.literal("list").executes(ctx -> {
				if (SparBot.matches().matches().isEmpty()) {
					return ok(ctx, "No matches running");
				}
				return ok(ctx, SparBot.matches().matches().stream().map(MatchCommands::describe).collect(Collectors.joining("; ")));
			})));

		root.then(Commands.literal("modes").executes(ctx -> ok(ctx, "Modes: " + SparBot.matches().modes().ids().stream()
			.map(id -> {
				GameMode m = SparBot.matches().modes().get(id).orElseThrow();
				return id + " (" + m.kit() + ", first to " + m.roundsToWin() + ")";
			}).collect(Collectors.joining(", ")))));

		root.then(Commands.literal("elo").executes(ctx -> {
			List<Map.Entry<String, EloLadder.Entry>> standings = SparBot.matches().elo().ladder().standings();
			if (standings.isEmpty()) {
				return ok(ctx, "No rated matches yet");
			}
			StringBuilder out = new StringBuilder("Elo ladder:");
			int rank = 1;
			for (Map.Entry<String, EloLadder.Entry> e : standings) {
				EloLadder.Entry v = e.getValue();
				out.append(String.format("%n%d. %s  %.0f  (%d-%d-%d)", rank++, e.getKey(), v.rating, v.wins, v.losses, v.draws));
				if (rank > 15) {
					break;
				}
			}
			return ok(ctx, out.toString());
		}));

		root.then(Commands.literal("benchmark").then(Commands.argument("arena", StringArgumentType.word()).suggests(ARENAS)
			.then(Commands.argument("profileA", StringArgumentType.word()).suggests(PROFILES)
				.then(Commands.argument("profileB", StringArgumentType.word()).suggests(PROFILES)
					.then(Commands.argument("matches", IntegerArgumentType.integer(1, 500))
						.executes(ctx -> benchmark(ctx, "balanced", "balanced"))
						.then(Commands.argument("styleA", StringArgumentType.word()).suggests(STYLES)
							.then(Commands.argument("styleB", StringArgumentType.word()).suggests(STYLES)
								.executes(ctx -> benchmark(ctx, StringArgumentType.getString(ctx, "styleA"), StringArgumentType.getString(ctx, "styleB"))))))))));
	}

	private static int benchmark(CommandContext<CommandSourceStack> ctx, String styleA, String styleB) throws CommandSyntaxException {
		Arena arena = arena(ctx, "arena");
		if (!arena.ready()) {
			throw fail("Arena " + arena.id() + " needs both spawns");
		}
		GameMode mode = SparBot.matches().modes().get("benchmark").orElseThrow();
		SkillProfile a = profile(StringArgumentType.getString(ctx, "profileA"));
		SkillProfile b = profile(StringArgumentType.getString(ctx, "profileB"));
		Playstyle sa;
		Playstyle sb;
		try {
			sa = SparBot.playstyles().resolve(styleA);
			sb = SparBot.playstyles().resolve(styleB);
		} catch (IllegalArgumentException e) {
			throw fail(e.getMessage());
		}
		int matches = IntegerArgumentType.getInteger(ctx, "matches");
		SparBot.matches().addBenchmark(new Benchmark(arena, mode, a, sa, b, sb, matches, ctx.getSource()));
		return ok(ctx, "Benchmark started: " + a.id() + "/" + sa.id() + " vs " + b.id() + "/" + sb.id() + ", " + matches + " matches in " + arena.id());
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, boolean sideA) throws CommandSyntaxException {
		Arena arena = arena(ctx);
		Vec3 pos = ctx.getSource().getPosition();
		try {
			SparBot.matches().arenas().setSpawn(arena.id(), sideA, new Arena.Spawn(pos.x, pos.y, pos.z, ctx.getSource().getRotation().y));
		} catch (IOException | IllegalArgumentException e) {
			throw fail(e.getMessage());
		}
		return ok(ctx, "Spawn " + (sideA ? "A" : "B") + " of " + arena.id() + " set here");
	}

	private static String describe(Match match) {
		return match.arena().id() + ": " + match.a().name() + " " + match.state().score() + " " + match.b().name() + " (round " + match.state().round()
			+ ", " + match.state().phase() + ")";
	}

	private static Fighter fighter(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var bot = SparBot.bots().get(name);
		if (bot.isPresent()) {
			return MatchManager.fighterFor(bot.get());
		}
		ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(name);
		if (player == null) {
			throw fail(name + " is neither a bot nor an online player");
		}
		return MatchManager.fighterFor(player);
	}

	private static Arena arena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return arena(ctx, "id");
	}

	private static Arena arena(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, arg);
		return SparBot.matches().arenas().get(id).orElseThrow(() -> fail("Unknown arena " + id));
	}

	private static GameMode mode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "mode");
		return SparBot.matches().modes().get(id).orElseThrow(() -> fail("Unknown mode " + id));
	}

	private static SkillProfile profile(String id) throws CommandSyntaxException {
		return SparBot.profiles().get(id).orElseThrow(() -> fail("Unknown profile " + id));
	}

	private static CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}
}

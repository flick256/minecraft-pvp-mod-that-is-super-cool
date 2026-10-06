package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sparbot practice}: the practice world. Alone it takes you to the hub; {@code leave} brings
 * you back; {@code fight <mode> [profile]} starts a match against a bot in that mode's arena.
 */
final class PracticeCommands {
	private static final SuggestionProvider<CommandSourceStack> MODES = (ctx, b) -> SharedSuggestionProvider.suggest(SparBot.matches().modes().ids(), b);
	private static final SuggestionProvider<CommandSourceStack> KITS = (ctx, b) -> SharedSuggestionProvider.suggest(SparBot.kits().ids(), b);
	private static final SuggestionProvider<CommandSourceStack> PROFILES = (ctx, b) -> SharedSuggestionProvider.suggest(SparBot.profiles().ids(), b);

	private PracticeCommands() {
	}

	static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("practice")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				try {
					SparBot.practice().toHub(player);
				} catch (IOException | IllegalStateException e) {
					throw fail(e.getMessage());
				}
				return ok(ctx, "Welcome to the practice hub: step on a pad to visit an arena, or fight from the menu's Practice tab");
			})
			.then(Commands.literal("colosseum").executes(ctx -> {
				try {
					SparBot.practice().toColosseum(ctx.getSource().getPlayerOrException());
				} catch (IOException | IllegalStateException e) {
					throw fail(e.getMessage());
				}
				return 1;
			})
				.then(Commands.literal("waves").executes(ctx -> game(ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind.WAVES, "pvphq_sword",
						"intermediate"))
					.then(Commands.argument("kit", StringArgumentType.word()).suggests(KITS)
						.executes(ctx -> game(ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind.WAVES,
							StringArgumentType.getString(ctx, "kit"), "intermediate"))))
				.then(Commands.literal("hill").executes(ctx -> game(ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind.HILL, "pvphq_sword",
						SparBot.config().defaultProfile))
					.then(Commands.argument("profile", StringArgumentType.word()).suggests(PROFILES)
						.executes(ctx -> game(ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind.HILL, "pvphq_sword",
							StringArgumentType.getString(ctx, "profile")))
						.then(Commands.argument("kit", StringArgumentType.word()).suggests(KITS)
							.executes(ctx -> game(ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind.HILL,
								StringArgumentType.getString(ctx, "kit"), StringArgumentType.getString(ctx, "profile"))))))
				.then(Commands.literal("stop").executes(ctx -> {
					if (!SparBot.practice().games().stop(ctx.getSource().getServer(), "stopped")) {
						throw fail("No colosseum game is on");
					}
					return 1;
				})))
			.then(Commands.literal("hall").executes(ctx -> {
				try {
					SparBot.practice().toHall(ctx.getSource().getPlayerOrException());
				} catch (IOException | IllegalStateException e) {
					throw fail(e.getMessage());
				}
				return 1;
			}))
			.then(Commands.literal("leave").executes(ctx -> {
				if (!SparBot.practice().leave(ctx.getSource().getPlayerOrException())) {
					throw fail("Nowhere to go back to: this world is the practice world");
				}
				return ok(ctx, "Back where you were");
			}))
			.then(Commands.literal("fight").then(Commands.argument("mode", StringArgumentType.word()).suggests(MODES)
				.executes(ctx -> fight(ctx, SparBot.config().defaultProfile))
				.then(Commands.argument("profile", StringArgumentType.word()).suggests(PROFILES)
					.executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "profile")))))));
	}

	/** {@code /sparbot drill}: lists the drills; {@code drill <id>} starts one; {@code drill stop} ends yours. */
	static void addDrillsTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		SuggestionProvider<CommandSourceStack> drills = (ctx, b) -> SharedSuggestionProvider.suggest(
			io.github.flick256.sparbot.core.drill.Drills.ALL.stream().map(io.github.flick256.sparbot.core.drill.Drill::id), b);
		root.then(Commands.literal("drill")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				java.util.Map<String, Integer> mine = SparBot.drills().medalsOf(player.getUUID());
				StringBuilder list = new StringBuilder("Skill drills (/sparbot drill <name>):");
				for (String discipline : io.github.flick256.sparbot.core.drill.Drills.disciplines()) {
					list.append("\n").append(discipline).append(": ");
					list.append(String.join(", ", io.github.flick256.sparbot.core.drill.Drills.ALL.stream().filter(d -> d.discipline().equals(discipline))
						.map(d -> d.id() + (mine.getOrDefault(d.id(), 0) > 0 ? " (" + io.github.flick256.sparbot.core.drill.Drill.medal(mine.get(d.id())) + ")" : ""))
						.toList()));
				}
				return ok(ctx, list.toString());
			})
			.then(Commands.literal("stop").executes(ctx -> {
				if (!SparBot.drills().stop(ctx.getSource().getPlayerOrException(), "you stopped it")) {
					throw fail("You aren't in a drill");
				}
				return 1;
			}))
			.then(Commands.argument("drill", StringArgumentType.word()).suggests(drills).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				String id = StringArgumentType.getString(ctx, "drill");
				io.github.flick256.sparbot.core.drill.Drill drill = io.github.flick256.sparbot.core.drill.Drills.get(id)
					.orElseThrow(() -> fail("Unknown drill " + id + " (/sparbot drill lists them)"));
				try {
					return ok(ctx, "Drill: " + SparBot.drills().start(player, drill));
				} catch (IOException | IllegalArgumentException | IllegalStateException e) {
					throw fail(e.getMessage());
				}
			})));
	}

	private static int game(CommandContext<CommandSourceStack> ctx, io.github.flick256.sparbot.practice.colosseum.ColosseumGames.Kind kind, String kit,
		String profile) throws CommandSyntaxException {
		try {
			return ok(ctx, SparBot.practice().games().start(ctx.getSource().getPlayerOrException(), kind, kit, profile));
		} catch (IOException | IllegalArgumentException | IllegalStateException e) {
			throw fail(e.getMessage());
		}
	}

	private static int fight(CommandContext<CommandSourceStack> ctx, String profileId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String modeId = StringArgumentType.getString(ctx, "mode");
		GameMode mode = SparBot.matches().modes().get(modeId).orElseThrow(() -> fail("Unknown mode " + modeId));
		SkillProfile profile = SparBot.profiles().get(profileId).orElseThrow(() -> fail("Unknown profile " + profileId));
		try {
			return ok(ctx, SparBot.practice().fight(player, mode, profile));
		} catch (IOException | IllegalArgumentException | IllegalStateException e) {
			throw fail(e.getMessage());
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

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

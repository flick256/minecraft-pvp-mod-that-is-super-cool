package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.rival.Rivals;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Commands for the named rival roster and persistent career ladder. */
public final class CareerCommands {
	private CareerCommands() { }

	public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("rival").then(Commands.argument("id", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Rivals.ALL.stream().map(r -> r.id()), builder))
			.executes(ctx -> {
				try { return ok(ctx.getSource(), SparBot.careers().challenge(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"))); }
				catch (Exception e) { throw fail(e.getMessage()); }
			})));
		var career = Commands.literal("career").executes(ctx -> career(ctx.getSource(), null));
		var mode = Commands.argument("mode", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Rivals.ALL.stream().map(r -> r.mode()).distinct(), builder))
			.executes(ctx -> career(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))
			.then(Commands.literal("continue").executes(ctx -> {
				try { return ok(ctx.getSource(), SparBot.careers().continueCareer(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"))); }
				catch (Exception e) { throw fail(e.getMessage()); }
			}));
		career.then(mode);
		career.then(Commands.literal("adapt")
			.then(Commands.literal("on").executes(ctx -> adapt(ctx.getSource(), true)))
			.then(Commands.literal("off").executes(ctx -> adapt(ctx.getSource(), false))));
		root.then(career);
	}

	private static int career(CommandSourceStack source, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		var tracks = SparBot.careers().menuCareers(player.getUUID());
		var selected = tracks.stream().filter(t -> mode == null || t.mode().equals(mode)).toList();
		if (selected.isEmpty()) throw fail("Unknown career discipline " + mode);
		String message = selected.stream().map(t -> t.mode() + " tier " + t.tier() + " — " + t.wins() + " promotions, " + t.losses()
			+ " losses; series " + t.progress() + "/" + t.required() + (t.champion() ? "; champion" : "; next: " + t.nextRival()))
			.collect(java.util.stream.Collectors.joining("\n"));
		return ok(source, message);
	}

	private static int adapt(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		try { SparBot.careers().setAdaptivePractice(player.getUUID(), enabled); }
		catch (java.io.IOException e) { throw fail("Could not save career preference: " + e.getMessage()); }
		return ok(source, "Adaptive practice " + (enabled ? "on" : "off") + ". It adjusts practice opponents between fights only.");
	}

	private static int ok(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static com.mojang.brigadier.exceptions.CommandSyntaxException fail(String message) {
		return new SimpleCommandExceptionType(Component.literal(message == null ? "Career action failed" : message)).create();
	}
}

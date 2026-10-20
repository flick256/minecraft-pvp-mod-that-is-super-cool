package io.github.flick256.sparbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.kit.editor.KitEditor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

/** Command contribution for the native kit editor. */
public final class KitEditorCommands {
	private KitEditorCommands() {}

	public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("kiteditor")
			.then(Commands.argument("kit", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SparBot.kits().ids(), builder))
				.executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "kit"))))
			.executes(ctx -> open(ctx.getSource(), "sparbot_sword")));
	}

	private static int open(CommandSourceStack source, String kit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		KitEditor.open(source.getPlayerOrException(), kit);
		return 1;
	}

}

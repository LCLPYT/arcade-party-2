package work.lclpnet.ap2.game.dance_floor.cmd

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.game.dance_floor.BlockRandomizer
import work.lclpnet.ap2.game.dance_floor.Pattern
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.concurrent.CompletableFuture

class SetPatternCommand(
    val randomizer: BlockRandomizer,
    val setPattern: (Pattern) -> Unit
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            Commands.literal("ap2:set_pattern")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("pattern", StringArgumentType.word())
                        .suggests { _, builder -> availablePatterns(builder) }
                        .executes(this::setPattern)))
    }

    private fun availablePatterns(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (id in randomizer.patternsById.keys) {
            builder.suggest(id)
        }

        return builder.buildFuture()
    }

    private fun setPattern(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "pattern")

        val pattern = randomizer.patternsById[id]

        if (pattern == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown pattern \"$id\""))
            return 0
        }

        setPattern(pattern)

        ctx.getSource().sendSystemMessage(Component.literal("Set pattern to \"$id\""))

        return 1
    }
}

package work.lclpnet.ap2.game.guess_it.util

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SkipChallengeCommand(private val skip: Runnable) : KibuCommand {
    override fun register(commands: CommandRegistrar) {
        commands.registerCommand(
            Commands.literal("ap2:skip_challenge")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes { ctx -> skipChallenge(ctx) }
        )
    }

    private fun skipChallenge(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.getSource().sendSystemMessage(Component.literal("Skipped the current challenge"))

        skip.run()

        return 1
    }
}

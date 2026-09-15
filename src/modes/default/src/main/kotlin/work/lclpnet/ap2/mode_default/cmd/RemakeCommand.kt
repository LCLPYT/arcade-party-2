package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameResults
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.concurrent.atomic.AtomicBoolean

class RemakeCommand(
    private val handle: MiniGameHandle,
    private val remake: AtomicBoolean
) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("remake")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .executes { ctx -> remake(ctx) }

    private fun remake(ctx: CommandContext<CommandSourceStack>): Int {
        if (remake.getAndSet(true)) {
            return 0
        }

        ctx.getSource().sendSystemMessage(Component.literal("Restarting the current mini game..."))

        handle.complete(MiniGameResults.EMPTY)

        return 1
    }
}

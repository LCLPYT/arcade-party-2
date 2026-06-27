package work.lclpnet.ap2.mode_default.cmd

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.mode_default.api.Skippable
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SkipCommand(private val skippable: Skippable) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(command())
    }

    private fun command() = Commands.literal("skip")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .executes { ctx -> skip(ctx) }

    private fun skip(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.getSource()

        if (skippable.isSkip) {
            source.sendSystemMessage(Component.literal("Already skipped"))
            return 0
        }

        skippable.isSkip = true
        source.sendSystemMessage(Component.literal("Skipped the preparation phase"))

        return 1
    }
}

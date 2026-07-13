package work.lclpnet.ap2.task_rush

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.task_rush.task.TaskManager
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SkipTaskCommand(private val manager: TaskManager) : KibuCommand {

    override fun register(commands: CommandRegistrar) {
        commands.registerCommand(
            Commands.literal("ap2:skip_task")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes { ctx -> skip(ctx) }
        )
    }

    private fun skip(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sendSystemMessage(Component.literal("Skipped the current task"))

        manager.skipCurrentTask()

        return 1
    }
}

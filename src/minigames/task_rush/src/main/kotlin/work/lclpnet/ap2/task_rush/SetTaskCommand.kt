package work.lclpnet.ap2.task_rush

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.task_rush.task.Task
import work.lclpnet.ap2.task_rush.task.TaskManager
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SetTaskCommand(private val manager: TaskManager) : KibuCommand {

    override fun register(commands: CommandRegistrar) {
        val root = Commands.literal("ap2:set_task")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

        for (task in manager.allTasks) {
            root.then(
                Commands.literal(task.id)
                    .executes { ctx -> setTask(ctx, task) }
            )
        }

        commands.registerCommand(root)
    }

    private fun setTask(ctx: CommandContext<CommandSourceStack>, task: Task): Int {
        ctx.source.sendSystemMessage(Component.literal("Changed task to \"${task.id}\""))

        manager.changeTask(task)

        return 1
    }
}

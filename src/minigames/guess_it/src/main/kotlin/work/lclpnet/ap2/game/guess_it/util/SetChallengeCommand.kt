package work.lclpnet.ap2.game.guess_it.util

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import work.lclpnet.ap2.game.guess_it.data.Challenge
import work.lclpnet.ap2.game.guess_it.data.GuessItManager
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class SetChallengeCommand(
    private val manager: GuessItManager,
    private val skip: Runnable
) : KibuCommand {

    override fun register(commands: CommandRegistrar) {
        val root = Commands.literal("ap2:set_challenge")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

        for (challenge in manager.getChallenges()) {
            val node = Commands.literal(challenge.id())
                .executes { ctx -> setChallenge(ctx, challenge, null) }

            val initializer = Challenge.Initializer { ctx, init ->
                setChallenge(ctx, challenge, init)
            }

            challenge.provideInitCommand(node, initializer)

            root.then(node)
        }

        commands.registerCommand(root)
    }

    private fun setChallenge(ctx: CommandContext<CommandSourceStack>, challenge: Challenge, init: Any?): Int {
        ctx.getSource().sendSystemMessage(Component.literal("Set challenge to \"${challenge.id()}\""))

        manager.pushChallenge(challenge, init)
        skip.run()

        return 1
    }
}

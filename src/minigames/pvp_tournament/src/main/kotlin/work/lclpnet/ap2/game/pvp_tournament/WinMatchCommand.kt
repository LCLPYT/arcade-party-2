package work.lclpnet.ap2.game.pvp_tournament

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Avatar
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand

class WinMatchCommand(
    private val win: (ctx: CommandSourceStack, matchOf: Avatar, winner: PlayerRef?) -> Unit,
    private val participants: (matchOf: Avatar) -> List<String>,
    private val resolvePlayer: (matchOf: Avatar, name: String) -> PlayerRef?
) : KibuCommand {

    val unknownPlayerError = DynamicCommandExceptionType {
        Component.literal("Unknown participant \"$it\"")
    }

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(Commands.literal("ap2:win_match")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes {
                val player = it.source.playerOrException

                win(it.source, player, PlayerRef.create(player))

                1
            }
            .then(Commands.argument("winner", StringArgumentType.string())
                .suggests { ctx, builder ->
                    ctx.source.player?.let { player ->
                        participants(player).forEach { name ->
                            builder.suggest(when {
                                name.contains(" ") -> "\"$name\""
                                else -> name
                            })
                        }
                    }

                    builder.suggest("draw")

                    builder.buildFuture()
                }
                .executes {
                    val player = it.source.playerOrException

                    val winner = when (val name = StringArgumentType.getString(it, "winner")) {
                        "draw" -> null
                        else -> resolvePlayer(player, name) ?: throw unknownPlayerError.create(name)
                    }

                    win(it.source, player, winner)

                    1
                }))
    }
}
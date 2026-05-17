package work.lclpnet.ap2.game.musical_minecart.cmd

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.concurrent.CompletableFuture

@JvmRecord
data class SetSongCommand(val songs: SongHandler, val skipCurrent: Runnable) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            Commands.literal("ap2:set_song")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("song", IdentifierArgument.id())
                        .suggests { _, builder -> availableSongs(builder) }
                        .executes(this::setSong)
                        .then(
                            Commands.argument("time", IntegerArgumentType.integer())
                                .suggests(this::availableTimes)
                                .executes(this::setSongTime)
                        )))
    }

    private fun availableSongs(builder: SuggestionsBuilder): CompletableFuture<Suggestions?> {
        songs.songIds.stream()
            .map { obj: Identifier? -> obj.toString() }
            .forEach { text: String? -> builder.suggest(text) }

        return builder.buildFuture()
    }

    private fun availableTimes(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions?> {
        val id = IdentifierArgument.getId(ctx, "song")

        songs.streamSongsById(id)
            .mapToInt { song -> song!!.getInfo().meta.startTick.orElse(0) ?: 0 }
            .forEach(builder::suggest)

        return builder.buildFuture()
    }

    private fun setSong(ctx: CommandContext<CommandSourceStack>): Int {
        val id = IdentifierArgument.getId(ctx, "song")

        val song = songs.getRandomSongById(id).orElse(null) ?: run {
            ctx.source.sendFailure(Component.literal("Unknown song \"$id\""))
            return 0
        }

        songs.pushPrioritySong(song)
        ctx.source.sendSystemMessage(Component.literal("Set song to \"$id\""))
        skipCurrent.run()

        return 1
    }

    private fun setSongTime(ctx: CommandContext<CommandSourceStack>): Int {
        val id = IdentifierArgument.getId(ctx, "song")
        val startTick = IntegerArgumentType.getInteger(ctx, "time")

        val song = songs.getSongByIdAndTime(id, startTick).orElse(null) ?: run {
            ctx.source.sendFailure(Component.literal("Unknown song \"$id\" with time $startTick"))
            return 0
        }

        songs.pushPrioritySong(song)
        ctx.source.sendSystemMessage(Component.literal("Set song to \"$id\" with time $startTick"))
        skipCurrent.run()

        return 1
    }
}

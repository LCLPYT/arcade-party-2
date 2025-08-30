package work.lclpnet.ap2.game.dance_floor.cmd

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.cmd.type.KibuCommand
import java.util.concurrent.CompletableFuture

@JvmRecord
data class SetSongCommand(val songs: SongHandler, val skipCurrent: Runnable) : KibuCommand {

    override fun register(registrar: CommandRegistrar) {
        registrar.registerCommand(
            CommandManager.literal("ap2:set_song")
                .requires { s -> s!!.hasPermissionLevel(2) }
                .then(
                    CommandManager.argument("song", IdentifierArgumentType.identifier())
                        .suggests { _, builder -> availableSongs(builder) }
                        .executes(this::setSong)
                        .then(
                            CommandManager.argument("time", IntegerArgumentType.integer())
                                .suggests(this::availableTimes)
                                .executes(this::setSongTime)
                        )))
    }

    private fun availableSongs(
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions?> {
        songs.songIds.stream()
            .map { obj: Identifier? -> obj.toString() }
            .forEach { text: String? -> builder.suggest(text) }

        return builder.buildFuture()
    }

    private fun availableTimes(
        ctx: CommandContext<ServerCommandSource?>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions?> {
        val id = IdentifierArgumentType.getIdentifier(ctx, "song")

        songs.streamSongsById(id)
            .mapToInt { song -> song!!.getInfo().meta.startTick.orElse(0) }
            .forEach(builder::suggest)

        return builder.buildFuture()
    }

    private fun setSong(ctx: CommandContext<ServerCommandSource?>): Int {
        val id = IdentifierArgumentType.getIdentifier(ctx, "song")

        val song = songs.getRandomSongById(id).orElse(null)

        if (song == null) {
            ctx.getSource()!!.sendError(Text.literal("Unknown song \"$id\""))
            return 0
        }

        songs.pushSong(song)

        ctx.getSource()!!.sendMessage(Text.literal("Set song to \"$id\""))

        skipCurrent.run()

        return 1
    }

    private fun setSongTime(ctx: CommandContext<ServerCommandSource?>): Int {
        val id = IdentifierArgumentType.getIdentifier(ctx, "song")
        val startTick = IntegerArgumentType.getInteger(ctx, "time")

        val song = songs.getSongByIdAndTime(id, startTick).orElse(null)

        if (song == null) {
            ctx.getSource()!!.sendError(Text.literal("Unknown song \"$id\" with time $startTick"))
            return 0
        }

        songs.pushSong(song)

        ctx.getSource()!!.sendMessage(Text.literal("Set song to \"$id\" with time $startTick"))

        skipCurrent.run()

        return 1
    }
}

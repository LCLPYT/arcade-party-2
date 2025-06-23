package work.lclpnet.ap2.game.musical_minecart.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.ap2.game.musical_minecart.MMSongs;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SetSongCommand implements KibuCommand {

    private final MMSongs songs;
    private final Runnable skipCurrent;

    public SetSongCommand(MMSongs songs, Runnable skipCurrent) {
        this.songs = songs;
        this.skipCurrent = skipCurrent;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:set_song")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("song", IdentifierArgumentType.identifier())
                        .suggests(this::availableSongs)
                        .executes(this::setSong)
                        .then(argument("time", IntegerArgumentType.integer())
                                .suggests(this::availableTimes)
                                .executes(this::setSongTime))));
    }

    private CompletableFuture<Suggestions> availableSongs(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        songs.getSongIds().stream()
                .map(Identifier::toString)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> availableTimes(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "song");

        songs.streamSongsById(id)
                .mapToInt(song -> song.getPlaybackInfo().startTick())
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private int setSong(CommandContext<ServerCommandSource> ctx) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "song");

        WeightedSong song = songs.getRandomSongById(id).orElse(null);

        if (song == null) {
            ctx.getSource().sendError(Text.literal("Unknown song \"%s\"".formatted(id)));
            return 0;
        }

        songs.pushSong(song);
        skipCurrent.run();

        ctx.getSource().sendMessage(Text.literal("Set song to \"%s\"".formatted(id)));

        return 1;
    }

    private int setSongTime(CommandContext<ServerCommandSource> ctx) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "song");
        int startTick = IntegerArgumentType.getInteger(ctx, "time");

        WeightedSong song = songs.getSongByIdAndTime(id, startTick).orElse(null);

        if (song == null) {
            ctx.getSource().sendError(Text.literal("Unknown song \"%s\" with time %d".formatted(id, startTick)));
            return 0;
        }

        songs.pushSong(song);
        skipCurrent.run();

        ctx.getSource().sendMessage(Text.literal("Set song to \"%s\" with time %d".formatted(id, startTick)));

        return 1;
    }
}

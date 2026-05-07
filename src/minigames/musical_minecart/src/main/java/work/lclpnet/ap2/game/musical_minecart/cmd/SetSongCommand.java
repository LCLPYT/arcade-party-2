package work.lclpnet.ap2.game.musical_minecart.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import work.lclpnet.ap2.api.music.WeightedSong;
import work.lclpnet.ap2.impl.music.SongHandler;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetSongCommand implements KibuCommand {

    private final SongHandler songs;
    private final Runnable skipCurrent;

    public SetSongCommand(SongHandler songs, Runnable skipCurrent) {
        this.songs = songs;
        this.skipCurrent = skipCurrent;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:set_song")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("song", IdentifierArgument.id())
                        .suggests(this::availableSongs)
                        .executes(this::setSong)
                        .then(argument("time", IntegerArgumentType.integer())
                                .suggests(this::availableTimes)
                                .executes(this::setSongTime))));
    }

    private CompletableFuture<Suggestions> availableSongs(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        songs.getSongIds().stream()
                .map(Identifier::toString)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> availableTimes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Identifier id = IdentifierArgument.getId(ctx, "song");

        songs.streamSongsById(id)
                .mapToInt(song -> song.getInfo().meta().startTick().orElse(0))
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private int setSong(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "song");

        WeightedSong song = songs.getRandomSongById(id).orElse(null);

        if (song == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown song \"%s\"".formatted(id)));
            return 0;
        }

        songs.pushPrioritySong(song);

        ctx.getSource().sendSystemMessage(Component.literal("Set song to \"%s\"".formatted(id)));

        skipCurrent.run();

        return 1;
    }

    private int setSongTime(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "song");
        int startTick = IntegerArgumentType.getInteger(ctx, "time");

        WeightedSong song = songs.getSongByIdAndTime(id, startTick).orElse(null);

        if (song == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown song \"%s\" with time %d".formatted(id, startTick)));
            return 0;
        }

        songs.pushPrioritySong(song);

        ctx.getSource().sendSystemMessage(Component.literal("Set song to \"%s\" with time %d".formatted(id, startTick)));

        skipCurrent.run();

        return 1;
    }
}

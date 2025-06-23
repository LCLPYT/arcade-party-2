package work.lclpnet.ap2.game.musical_minecart.cmd;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.server.command.CommandManager.literal;

public class SkipSongCommand implements KibuCommand {

    private final Runnable skipCurrent;

    public SkipSongCommand(Runnable skipCurrent) {
        this.skipCurrent = skipCurrent;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:skip_song")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::skip));
    }

    private int skip(CommandContext<ServerCommandSource> ctx) {
        skipCurrent.run();
        ctx.getSource().sendMessage(Text.literal("Skipped the current song."));
        return 1;
    }
}

package work.lclpnet.ap2.game.musical_minecart.cmd;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.commands.Commands.literal;

public class SkipSongCommand implements KibuCommand {

    private final Runnable skipCurrent;

    public SkipSongCommand(Runnable skipCurrent) {
        this.skipCurrent = skipCurrent;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:skip_song")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(this::skip));
    }

    private int skip(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("Skipped the current song."));
        skipCurrent.run();
        return 1;
    }
}

package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.MiniGameInstance;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.commands.Commands.literal;

public class DrawCommand implements KibuCommand {

    private final MiniGameHandle gameHandle;
    private final MiniGameInstance miniGame;

    public DrawCommand(MiniGameHandle gameHandle, MiniGameInstance miniGame) {
        this.gameHandle = gameHandle;
        this.miniGame = miniGame;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("draw")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(this::draw)
                .then(literal("now")
                        .executes(this::drawNow));
    }

    private int draw(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("Ended the current mini game with a draw"));

        dispatchDraw(miniGame);

        return 1;
    }

    private int drawNow(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("Ended the current mini game with a draw"));

        gameHandle.complete(MiniGameResults.EMPTY);

        return 1;
    }

    public static void dispatchDraw(MiniGameInstance instance) {
        instance.getWinManager().draw();
    }
}

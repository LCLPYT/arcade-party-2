package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.WinManagerView;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.cmd.KibuCommand;

import static net.minecraft.server.command.CommandManager.literal;

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

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("draw")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(this::draw)
                .then(literal("now")
                        .executes(this::drawNow));
    }

    private int draw(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendMessage(Text.literal("Ended the current mini game with a draw"));

        if (miniGame instanceof WinManagerView view) {
            WinManagerAccess winManagerAccess = view.getWinManagerAccess();
            winManagerAccess.draw();
        } else {
            gameHandle.completeWithoutWinner();
        }

        return 1;
    }

    private int drawNow(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendMessage(Text.literal("Ended the current mini game with a draw"));

        gameHandle.completeWithoutWinner();

        return 1;
    }
}

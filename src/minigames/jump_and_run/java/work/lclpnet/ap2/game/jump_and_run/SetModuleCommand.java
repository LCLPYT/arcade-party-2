package work.lclpnet.ap2.game.jump_and_run;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRun;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpModule;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SetModuleCommand implements KibuCommand {

    private final JumpAndRun jumpAndRun;
    private final Logger logger;

    public SetModuleCommand(JumpAndRun jumpAndRun, Logger logger) {
        this.jumpAndRun = jumpAndRun;
        this.logger = logger;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:set_module")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("module", StringArgumentType.string())
                        .suggests(this::suggestMaps)
                        .executes(this::setMap)));
    }

    private CompletableFuture<Suggestions> suggestMaps(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (JumpModule module : jumpAndRun.availableModules()) {
            builder.suggest(module.path());
        }

        return builder.buildFuture();
    }

    private int setMap(CommandContext<ServerCommandSource> ctx) {
        String path = StringArgumentType.getString(ctx, "module");

        JumpModule module = jumpAndRun.availableModules().stream()
                .filter(m -> m.path().equals(path))
                .findAny()
                .orElse(null);

        if (module == null) {
            ctx.getSource().sendError(Text.literal("Unknown module \"%s\"".formatted(path)));
            return 0;
        }

        try {
            jumpAndRun.setModule(module);
        } catch (Throwable t) {
            logger.error("Failed to set module", t);
            return 0;
        }

        return 1;
    }
}

package work.lclpnet.ap2.game.maze_scape.util;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import static net.minecraft.server.command.CommandManager.literal;

public class DebugPathCommand implements KibuCommand {

    private static final SimpleCommandExceptionType NO_PASSAGE_FOUND = new SimpleCommandExceptionType(Text.literal("No nearby passage was found"));
    private final MSStruct struct;
    private @Nullable Passage start = null;
    private @Nullable Passage end = null;

    public DebugPathCommand(MSStruct struct) {
        this.struct = struct;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:debugpath")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("start")
                        .executes(this::start))
                .then(literal("end")
                        .executes(this::end)));
    }

    private int start(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.start = getPassage(ctx);
        this.end = null;

        updatePath(ctx);

        return Command.SINGLE_SUCCESS;
    }

    private int end(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.end = getPassage(ctx);

        updatePath(ctx);

        return Command.SINGLE_SUCCESS;
    }

    private synchronized void updatePath(CommandContext<ServerCommandSource> ctx) {
        if (start == null || end == null) return;

        ServerWorld world = ctx.getSource().getWorld();

        // TODO visualize
    }

    @NotNull
    private Passage getPassage(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        Passage passage = struct.nearestPassageTo(player.getPos());

        if (passage == null) {
            throw NO_PASSAGE_FOUND.create();
        }

        return passage;
    }
}

package work.lclpnet.ap2.game.maze_scape.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class DebugPathCommand implements KibuCommand {

    private static final SimpleCommandExceptionType NO_PASSAGE_FOUND = new SimpleCommandExceptionType(Text.literal("No nearby passage was found"));
    private final MSStruct struct;
    private final MSDebugController debugger;
    private @Nullable Passage start = null;
    private @Nullable Passage end = null;
    private final List<Object3d> lines = new ArrayList<>();

    public DebugPathCommand(MSStruct struct, MSDebugController debugger) {
        this.struct = struct;
        this.debugger = debugger;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(literal("ap2:debug_path")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("start")
                        .executes(this::start))
                .then(literal("end")
                        .executes(this::end)));
    }

    private int start(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.start = getPassage(ctx);
        this.end = null;

        ctx.getSource().sendMessage(Text.literal("Set start room"));

        return updatePath(ctx);
    }

    private int end(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        this.end = getPassage(ctx);

        ctx.getSource().sendMessage(Text.literal("Set end room"));

        return updatePath(ctx);
    }

    private synchronized int updatePath(CommandContext<ServerCommandSource> ctx) {
        if (start == null || end == null) return 0;

        var path = struct.passagePathFinder().findPath(start, end);

        if (path.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Failed to find path"));
            return 0;
        }

        // clear old path
        debugger.parent().scene().ifPresent(scene -> lines.forEach(scene::remove));
        lines.clear();

        // display new path
        int len = path.size() - 1;
        BlockState material = Blocks.LIME_CONCRETE.getDefaultState();

        for (int i = 0; i < len; i++) {
            Passage from = path.get(i);
            Passage to = path.get(i + 1);

            var line = debugger.visualizePassage(from, to, material);

            if (line != null) {
                lines.add(line);
            }
        }

        ctx.getSource().sendMessage(Text.literal("Displaying shortest path (%s rooms)".formatted(path.size() + 1)));

        return Command.SINGLE_SUCCESS;
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

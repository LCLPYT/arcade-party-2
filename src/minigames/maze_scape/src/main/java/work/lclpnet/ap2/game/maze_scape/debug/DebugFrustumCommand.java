package work.lclpnet.ap2.game.maze_scape.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.impl.util.debug.DebugRenderer;
import work.lclpnet.ap2.util.VisibilityChecker;
import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.literal;

public class DebugFrustumCommand implements KibuCommand {

    private final MSDebugController debugger;
    private final List<Object3d> lines = new ArrayList<>();

    public DebugFrustumCommand(MSDebugController debugger) {
        this.debugger = debugger;
    }

    @Override
    public void register(CommandRegistrar commandRegistrar) {
        commandRegistrar.registerCommand(literal("ap2:debug_frustum")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("show")
                        .executes(this::showSelf))
                .then(literal("clear")
                        .executes(this::clear)));
    }

    private int showSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        DebugRenderer renderer = debugger.parent().renderer().orElse(null);

        if (renderer == null) {
            ctx.getSource().sendFailure(Component.literal("Debug renderer not initialized"));
            return 0;
        }

        reset();

        Matrix4d invViewProj = VisibilityChecker.viewProjectionMatrix(player, Math.toRadians(90), 1920 / 1080f, new Matrix4d()).invert();

        final Vec3[] frustum = {
                new Vec3(-1, -1, -1),
                new Vec3( 1, -1, -1),
                new Vec3( 1,  1, -1),
                new Vec3(-1,  1, -1),
                new Vec3(-1, -1,  1),
                new Vec3( 1, -1,  1),
                new Vec3( 1,  1,  1),
                new Vec3(-1,  1,  1)};

        for (int i = 0; i < frustum.length; i++) {
            Vector4d hom = new Vector4d(frustum[i].x, frustum[i].y, frustum[i].z, 1.d);

            invViewProj.transform(hom);

            frustum[i] = new Vec3(hom.x / hom.w, hom.y / hom.w, hom.z / hom.w);
        }

        double thickness = 0.005;
        BlockState state = Blocks.CONCRETE.black().defaultBlockState();

        for (int i = 0; i < 4; i++) {
            lines.add(renderer.line(frustum[i], frustum[(i + 1) % 4], thickness, state));
            lines.add(renderer.line(frustum[i + 4], frustum[(i + 1) % 4 + 4], thickness, state));
            lines.add(renderer.line(frustum[i], frustum[i + 4], thickness, state));
        }

        ctx.getSource().sendSystemMessage(Component.literal("Showing your camera view frustum"));

        return 1;
    }

    private void reset() {
        debugger.parent().scene().ifPresent(scene -> lines.forEach(scene::remove));
        lines.clear();
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        reset();

        ctx.getSource().sendSystemMessage(Component.literal("Frustum visualization cleared"));

        return 1;
    }
}

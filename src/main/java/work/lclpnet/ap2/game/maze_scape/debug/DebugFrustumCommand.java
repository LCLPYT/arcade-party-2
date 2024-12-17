package work.lclpnet.ap2.game.maze_scape.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.util.DebugRenderer;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class DebugFrustumCommand implements KibuCommand {

    private final MSDebugController debugger;
    private final List<Object3d> lines = new ArrayList<>();

    public DebugFrustumCommand(MSDebugController debugger) {
        this.debugger = debugger;
    }

    @Override
    public void register(CommandRegistrar commandRegistrar) {
        commandRegistrar.registerCommand(literal("ap2:debug_frustum")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("show")
                        .executes(this::showSelf))
                .then(literal("clear")
                        .executes(this::clear)));
    }

    private int showSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        DebugRenderer renderer = debugger.renderer();

        if (renderer == null) {
            ctx.getSource().sendError(Text.literal("Debug renderer not initialized"));
            return 0;
        }

        reset();

        Matrix4d invViewProj = MathUtil.viewProjectionMatrix(player, Math.toRadians(90), 1920 / 1080f, new Matrix4d()).invert();

        final Vec3d[] frustum = {
                new Vec3d(-1, -1, -1),
                new Vec3d( 1, -1, -1),
                new Vec3d( 1,  1, -1),
                new Vec3d(-1,  1, -1),
                new Vec3d(-1, -1,  1),
                new Vec3d( 1, -1,  1),
                new Vec3d( 1,  1,  1),
                new Vec3d(-1,  1,  1)};

        for (int i = 0; i < frustum.length; i++) {
            Vector4d hom = new Vector4d(frustum[i].x, frustum[i].y, frustum[i].z, 1.d);

            invViewProj.transform(hom);

            frustum[i] = new Vec3d(hom.x / hom.w, hom.y / hom.w, hom.z / hom.w);
        }

        double thickness = 0.03125;
        BlockState state = Blocks.BLACK_CONCRETE.getDefaultState();

        for (int i = 0; i < 4; i++) {
            lines.add(renderer.line(frustum[i], frustum[(i + 1) % 4], thickness, state));
            lines.add(renderer.line(frustum[i + 4], frustum[(i + 1) % 4 + 4], thickness, state));
            lines.add(renderer.line(frustum[i], frustum[i + 4], thickness, state));
        }

        ctx.getSource().sendMessage(Text.literal("Showing your camera view frustum"));

        return 1;
    }

    private void reset() {
        Scene scene = debugger.scene();

        if (scene != null) {
            lines.forEach(scene::remove);
        }

        lines.clear();
    }

    private int clear(CommandContext<ServerCommandSource> ctx) {
        reset();

        ctx.getSource().sendMessage(Text.literal("Frustum visualization cleared"));

        return 1;
    }
}

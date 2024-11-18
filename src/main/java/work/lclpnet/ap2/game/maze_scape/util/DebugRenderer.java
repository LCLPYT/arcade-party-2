package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;

public class DebugRenderer {

    private final Scene scene;

    public DebugRenderer(Scene scene) {
        this.scene = scene;
    }

    public Object3d line(Vec3d start, Vec3d end, double thickness, BlockState state) {
        return line(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), thickness, state);
    }

    public Object3d line(double x1, double y1, double z1, double x2, double y2, double z2, double thickness, BlockState state) {
        var line = new BlockDisplayObject(state);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        line.position.set(x1, y1, z1);

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // line points in x-direction
        line.scale.set(len, thickness, thickness);

        line.rotation.rotateTo(1, 0, 0, dx / len, dy / len, dz / len);

        scene.add(line);

        return line;
    }
}

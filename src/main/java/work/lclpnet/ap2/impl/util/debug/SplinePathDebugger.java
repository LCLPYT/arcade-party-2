package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.SplinePath;

import java.util.List;

public class SplinePathDebugger {

    private static final boolean DEBUG_SPACING = false;

    public static void debug(DebugController debugger, SplinePath path, int samples) {
        if (samples < 2) throw new IllegalArgumentException("Need at least 2 samples");

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        List<Vec3d> keypoints = path.getKeypoints();

        for (Vec3d keypoint : keypoints) {
            renderer.marker(keypoint, Blocks.ORANGE_CONCRETE.getDefaultState(), 0xeeff00, 0.5f);
        }

        Vec3d start = keypoints.getFirst();
        double step = 1.d / (samples - 1);

        renderer.marker(start, Blocks.YELLOW_CONCRETE.getDefaultState(), 0xeeff00, 0.2f);

        for (int i = 1; i < samples; i++) {
            Vec3d end = path.sample(i * step);

            renderer.line(start, end, 0.1, Blocks.YELLOW_CONCRETE.getDefaultState());

            if (DEBUG_SPACING) {
                renderer.marker(start, Blocks.YELLOW_CONCRETE.getDefaultState(), 0xeeff00, 0.2f);

                Vec3d diff = end.subtract(start);
                Vec3d midpoint = start.add(diff.multiply(0.5));

                Vec3d dir = diff.normalize();
                Vec3d right = dir.crossProduct(Direction.UP.getDoubleVector());
                Vec3d up = right.crossProduct(dir);

                renderer.text(midpoint.add(up.multiply(0.25)), Text.literal(String.format("%.2f", start.distanceTo(end))));
            }

            start = end;
        }
    }
}

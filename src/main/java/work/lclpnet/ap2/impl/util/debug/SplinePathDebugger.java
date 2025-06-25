package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.SplinePath;

import java.util.List;

public class SplinePathDebugger {

    public static void debug(DebugController debugger, SplinePath path, int samples) {
        if (samples < 2) throw new IllegalArgumentException("Need at least 2 samples");

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        List<Vec3d> keypoints = path.getKeypoints();

        for (Vec3d keypoint : keypoints) {
            renderer.marker(keypoint, Blocks.YELLOW_CONCRETE.getDefaultState(), 0xeeff00);
        }

        Vec3d start = keypoints.getFirst();
        double step = 1.d / (samples - 1);

        for (int i = 1; i < samples; i++) {
            Vec3d end = path.sample(i * step * keypoints.size());

            renderer.line(start, end, 0.1, Blocks.YELLOW_CONCRETE.getDefaultState());

            start = end;
        }
    }
}

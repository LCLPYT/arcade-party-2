package work.lclpnet.ap2.impl.util.debug;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableInt;
import work.lclpnet.ap2.impl.util.ColorUtil;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.gaco.scene.object.BlockDisplayObject;
import work.lclpnet.gaco.scene.object.DisplayEntityObject;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;
import java.util.function.Supplier;

public class SplinePathDebugger {

    private static final boolean
            DEBUG_SPACING = false,
            DEBUG_DIRECTION = false;

    private final DebugController debugger;
    private final SplinePath path;

    public SplinePathDebugger(DebugController debugger, SplinePath path) {
        this.debugger = debugger;
        this.path = path;
    }

    public void renderPath(int samples) {
        renderPath(samples, Blocks.YELLOW_CONCRETE.getDefaultState());
    }

    public void renderPath(int samples, BlockState pathColor) {
        if (samples < 2) throw new IllegalArgumentException("Need at least 2 samples");

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        List<Vec3d> keypoints = path.getKeypoints();

        for (Vec3d keypoint : keypoints) {
            renderer.marker(keypoint, Blocks.ORANGE_CONCRETE.getDefaultState(), 0xeeff00, 0.5f);
        }

        Vec3d start = keypoints.getFirst();
        double step = 1.d / (samples - 1);

        renderer.marker(start, pathColor, 0xeeff00, 0.2f);

        for (int i = 1; i < samples; i++) {
            double s = i * step;

            Vec3d end = path.samplePosition(s);

            renderer.line(start, end, 0.1, pathColor);

            if (DEBUG_SPACING) {
                renderer.marker(start, pathColor, 0xeeff00, 0.2f);

                Vec3d diff = end.subtract(start);
                Vec3d midpoint = start.add(diff.multiply(0.5));

                Vec3d dir = diff.normalize();
                Vec3d right = dir.crossProduct(Direction.UP.getDoubleVector());
                Vec3d up = right.crossProduct(dir);

                var label = Text.literal(String.format("%.2f", start.distanceTo(end)));
                renderer.text(midpoint.add(up.multiply(0.25)), label);
            }

            if (DEBUG_DIRECTION) {
                Vec3d dir = path.sampleDirection(s).normalize();
                Vec3d right = dir.crossProduct(Direction.UP.getDoubleVector());
                Vec3d up = right.crossProduct(dir);

                renderer.arrow(start, dir, Blocks.LIME_TERRACOTTA.getDefaultState());

                var label = Text.literal("(%.2f, %.2f)".formatted(MathUtil.yaw(dir), MathUtil.pitch(dir)));
                renderer.text(start.add(up.multiply(0.25)), label);
            }

            start = end;
        }
    }

    public void renderLiveProgress(Supplier<Iterable<? extends Entity>> playerGetter, TaskScheduler scheduler) {
        renderLiveProgress(playerGetter, scheduler, entity -> -1);
    }

    public void renderLiveProgress(Supplier<Iterable<? extends Entity>> entities, TaskScheduler scheduler,
                                   Object2IntFunction<Entity> markerColor) {

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        record Marker(Object3d obj, MutableInt color) {
            void changeColor(int color) {
                if (this.color.getValue() == color) return;

                this.color.setValue(color);

                for (Object3d o : obj.traverse()) {
                    if (o instanceof BlockDisplayObject bdo) {
                        bdo.setGlowColorOverride(color);
                    }
                }
            }
        }

        Random random = new Random();
        Map<UUID, Marker> markers = new HashMap<>();

        for (Entity entity : entities.get()) {
            Vec3d pos = path.getNearestPosition(entity.getEntityPos());

            int originalColor = markerColor.applyAsInt(entity);
            int color = originalColor;

            if (color == -1) {
                color = ColorUtil.getRandomHsvColor(random, random.nextFloat(110, 360));
            }

            Object3d obj = renderer.marker(pos, Blocks.RED_CONCRETE.getDefaultState(), color);

            for (Object3d o : obj.traverse()) {
                if (o instanceof DisplayEntityObject<?> deo) {
                    deo.setTeleportDuration(1);
                }
            }

            markers.put(entity.getUuid(), new Marker(obj, new MutableInt(originalColor)));
        }

        scheduler.interval(info -> {
            Set<UUID> removal = new HashSet<>(markers.keySet());

            for (Entity entity : entities.get()) {
                UUID uuid = entity.getUuid();
                Marker marker = markers.get(uuid);

                if (marker == null) continue;

                removal.remove(uuid);

                Vec3d pos = path.getNearestPosition(entity.getEntityPos());

                marker.obj.position.set(pos.getX(), pos.getY(), pos.getZ());
                marker.obj.updateMatrixWorld();

                int color = markerColor.apply(entity);
                marker.changeColor(color);
            }

            for (UUID uuid : removal) {
                Marker marker = markers.remove(uuid);

                if (marker != null) {
                    marker.obj.detach();
                }
            }
        }, 1).whenComplete(() -> markers.values().forEach(marker -> marker.obj.detach()));
    }
}

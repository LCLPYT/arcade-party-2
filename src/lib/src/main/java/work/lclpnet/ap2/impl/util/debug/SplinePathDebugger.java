package work.lclpnet.ap2.impl.util.debug;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
        renderPath(samples, Blocks.CONCRETE.yellow().defaultBlockState());
    }

    public void renderPath(int samples, BlockState pathColor) {
        if (samples < 2) throw new IllegalArgumentException("Need at least 2 samples");

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        List<Vec3> keypoints = path.getKeypoints();

        for (Vec3 keypoint : keypoints) {
            renderer.marker(keypoint, Blocks.CONCRETE.orange().defaultBlockState(), 0xeeff00, 0.5f);
        }

        Vec3 start = keypoints.getFirst();
        double step = 1.d / (samples - 1);

        renderer.marker(start, pathColor, 0xeeff00, 0.2f);

        for (int i = 1; i < samples; i++) {
            double s = i * step;

            Vec3 end = path.samplePosition(s);

            renderer.line(start, end, 0.1, pathColor);

            if (DEBUG_SPACING) {
                renderer.marker(start, pathColor, 0xeeff00, 0.2f);

                Vec3 diff = end.subtract(start);
                Vec3 midpoint = start.add(diff.scale(0.5));

                Vec3 dir = diff.normalize();
                Vec3 right = dir.cross(Direction.UP.getUnitVec3());
                Vec3 up = right.cross(dir);

                var label = Component.literal(String.format("%.2f", start.distanceTo(end)));
                renderer.text(midpoint.add(up.scale(0.25)), label);
            }

            if (DEBUG_DIRECTION) {
                Vec3 dir = path.sampleDirection(s).normalize();
                Vec3 right = dir.cross(Direction.UP.getUnitVec3());
                Vec3 up = right.cross(dir);

                renderer.arrow(start, dir, Blocks.DYED_TERRACOTTA.lime().defaultBlockState());

                var label = Component.literal("(%.2f, %.2f)".formatted(MathUtil.yaw(dir), MathUtil.pitch(dir)));
                renderer.text(start.add(up.scale(0.25)), label);
            }

            start = end;
        }
    }

    public void renderLiveProgress(Supplier<Iterable<? extends Entity>> playerGetter, TaskScheduler scheduler) {
        renderLiveProgress(playerGetter, scheduler, _ -> -1);
    }

    public void renderLiveProgress(Supplier<Iterable<? extends Entity>> entities, TaskScheduler scheduler,
                                   Object2IntFunction<Entity> markerColor) {

        DebugRenderer renderer = debugger.renderer().orElse(null);

        if (renderer == null) return;

        record Marker(Object3d obj, MutableInt color) {
            void changeColor(int color) {
                if (this.color.get().intValue() == color) return;

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
            Vec3 pos = path.getNearestPosition(entity.position());

            int originalColor = markerColor.applyAsInt(entity);
            int color = originalColor;

            if (color == -1) {
                color = ColorUtil.getRandomHsvColor(random, random.nextFloat(110, 360));
            }

            Object3d obj = renderer.marker(pos, Blocks.CONCRETE.red().defaultBlockState(), color);

            for (Object3d o : obj.traverse()) {
                if (o instanceof DisplayEntityObject<?> deo) {
                    deo.setTeleportDuration(1);
                }
            }

            markers.put(entity.getUUID(), new Marker(obj, new MutableInt(originalColor)));
        }

        scheduler.interval(_ -> {
            Set<UUID> removal = new HashSet<>(markers.keySet());

            for (Entity entity : entities.get()) {
                UUID uuid = entity.getUUID();
                Marker marker = markers.get(uuid);

                if (marker == null) continue;

                removal.remove(uuid);

                Vec3 pos = path.getNearestPosition(entity.position());

                marker.obj.position.set(pos.x(), pos.y(), pos.z());
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

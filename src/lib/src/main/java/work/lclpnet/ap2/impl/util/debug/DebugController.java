package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.StructureMask;
import work.lclpnet.gaco.math.AffineIntMatrix;
import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.ServerWorldMountContext;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;
import java.util.function.Consumer;

public class DebugController {

    private @Nullable Scene scene = null;
    private @Nullable DebugRenderer renderer = null;
    private @Nullable Map<String, List<Object3d>> namedObjects = null;
    private @Nullable ThreadLocal<@Nullable List<Object3d>> group = null;
    private volatile StopWatchImpl stopWatch = null;

    public synchronized void init(ModelManager modelManager, ServerLevel world) {
        destroy();

        scene = new Scene(new ServerWorldMountContext(world));
        renderer = new DebugRenderer(scene, modelManager, this::groupObject);
        namedObjects = new HashMap<>();
        group = ThreadLocal.withInitial(() -> null);
    }

    public Optional<Scene> scene() {
        return Optional.ofNullable(scene);
    }

    public Optional<DebugRenderer> renderer() {
        return Optional.ofNullable(renderer);
    }

    public void groupObject(Object3d obj) {
        if (group == null) return;

        List<Object3d> objects = group.get();

        if (objects != null) {
            objects.add(obj);
        }
    }

    public void visualizeStructureMask(StructureMask mask, BlockPos pos, Matrix3i transformation, BlockState state) {
        visualizeBoxes(mask.greedyMeshing().generateBoxes(), pos, transformation, state);
    }

    public void visualizeBlockPositions(Collection<BlockPos> positions, BlockState state) {
        if (renderer == null || positions.isEmpty()) return;

        var it = positions.iterator();
        var minPos = it.next().mutable();
        var maxPos = minPos.immutable().mutable();

        for (BlockPos pos : positions) {
            minPos.set(
                    Math.min(minPos.getX(), pos.getX()),
                    Math.min(minPos.getY(), pos.getY()),
                    Math.min(minPos.getZ(), pos.getZ())
            );

            maxPos.set(
                    Math.max(maxPos.getX(), pos.getX()),
                    Math.max(maxPos.getY(), pos.getY()),
                    Math.max(maxPos.getZ(), pos.getZ())
            );
        }

        var mask = StructureMask.createEmpty(new BlockBox(minPos, maxPos));

        for (BlockPos pos : positions) {
            mask.setVoxelAt(pos.getX() - minPos.getX(), pos.getY() - minPos.getY(), pos.getZ() - minPos.getZ(), true);
        }

        visualizeStructureMask(mask, minPos, Matrix3i.IDENTITY, state);
    }

    public void visualizeBoxes(List<BlockBox> boxes, BlockState state) {
        if (renderer == null) return;

        for (BlockBox box : boxes) {
            renderer.box(box, state);
        }
    }

    public void visualizeBoxes(List<BlockBox> boxes, BlockPos pos, Matrix3i transformation, BlockState state) {
        if (renderer == null) return;

        for (BlockBox box : boxes) {
            var affineMat = AffineIntMatrix.makeTranslation(pos).multiply(new AffineIntMatrix(transformation));
            box = box.transform(affineMat);
            renderer.box(box, state);
        }
    }

    public void exclusive(String name, Consumer<DebugController> action) {
        if (namedObjects == null || scene == null || group == null) return;

        var objects = namedObjects.computeIfAbsent(name, _ -> new ArrayList<>());
        objects.forEach(scene::remove);

        group.set(objects);

        action.accept(this);

        group.remove();
    }

    public StopWatch stopWatch() {
        if (stopWatch != null) return stopWatch;

        synchronized (this) {
            if (stopWatch != null) return stopWatch;

            stopWatch = new StopWatchImpl();
        }

        if (ApConstants.DEBUG) {
            stopWatch.enable();
        }

        return stopWatch;
    }

    public synchronized void destroy() {
        if (namedObjects != null) {
            namedObjects = null;
        }

        if (group != null) {
            group.remove();
            group = null;
        }

        if (scene != null) {
            scene.clear();
            scene = null;
        }

        renderer = null;
        stopWatch = null;
    }
}

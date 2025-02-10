package work.lclpnet.ap2.impl.util.debug;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.ds.StructureMask;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.ServerWorldMountContext;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;
import java.util.function.Consumer;

public class DebugController {

    private @Nullable Scene scene = null;
    private @Nullable DebugRenderer renderer = null;
    private @Nullable Map<String, List<Object3d>> namedObjects = null;
    private @Nullable ThreadLocal<@Nullable List<Object3d>> group = null;
    private volatile StopWatchImpl stopWatch = null;

    public void init(ModelManager modelManager, ServerWorld world) {
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

        var objects = namedObjects.computeIfAbsent(name, n -> new ArrayList<>());
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
}

package work.lclpnet.ap2.impl.game.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.scene.MixedMountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.animation.AnimationContext;
import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.SimpleGravityGradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;
import work.lclpnet.ap2.impl.scene.simulation.solver.EulerSolver;
import work.lclpnet.ap2.impl.scene.simulation.solver.NumericalSolver;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.pow;

public class SpecialItemScene {

    private final Scene scene;
    private final List<SpecialItemObject> objects = new ArrayList<>();
    private final Object2IntMap<SpecialItemObject> indices = new Object2IntOpenHashMap<>();
    private final Gradient gravity = new SimpleGravityGradient(0.04 * pow(20, 2));
    private final NumericalSolver solver = EulerSolver.INSTANCE;
    private StateVector state = new StateVector(new Vector3d[0]);

    public SpecialItemScene(ServerWorld world) {
        var dynamicEntityManager = new DynamicEntityManager(world);
        this.scene = new Scene(new MixedMountContext(world, dynamicEntityManager));
        indices.defaultReturnValue(-1);
    }

    public void init(TaskScheduler scheduler) {
        scene.animate(1, scheduler);
        scene.onUpdateAnimation(this::updateSimulation);
    }

    private synchronized void updateSimulation(double dt, AnimationContext ctx) {
        solver.solve(state, dt, gravity);

        for (int i = 0; i < objects.size(); i++) {
            SpecialItemObject obj = objects.get(i);

            if (obj.isOnGround(ctx.world())) {
                // reset velocity
                state.getVector3(2 * i + 1).set(0);
                continue;
            }

            obj.position.set(state.getVector3(2 * i));
            obj.updateMatrixWorld();
        }
    }

    public SpecialItemObject spawnItem(Vec3d pos, ItemStack stack) {
        var obj = new SpecialItemObject(stack);
        obj.position.set(pos.x, pos.y, pos.z);

        scene.add(obj);

        synchronized (this) {
            indices.put(obj, objects.size());
            objects.add(obj);

            int size = state.size();
            var vectors = new Vector3d[size + 2];

            for (int i = 0; i < size; i++) {
                vectors[i] = state.getVector3(i);
            }

            vectors[size] = new Vector3d(pos.x, pos.y, pos.z);
            vectors[size + 1] = new Vector3d();

            state = new StateVector(vectors);
        }

        return obj;
    }

    public void remove(SpecialItemObject obj) {
        scene.remove(obj);

        synchronized (this) {
            int i = indices.removeInt(obj);

            if (i == -1) return;

            objects.remove(i);

            for (int j = i; j < objects.size(); j++) {
                indices.put(objects.get(j), j);
            }

            int size = state.size();
            var vectors = new Vector3d[size - 2];

            for (int j = 0; j < i; j++) {
                vectors[j] = state.getVector3(j);
            }

            for (int j = i + 2; j < size; j++) {
                vectors[j - 2] = state.getVector3(j);
            }

            state = new StateVector(vectors);
        }
    }
}

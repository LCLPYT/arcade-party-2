package work.lclpnet.ap2.impl.game.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
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
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.pow;

public class SpecialItemScene {

    private final Random random;
    private final Scene scene;
    private final List<SpecialItemObject> objects = new ArrayList<>();
    private final Object2IntMap<SpecialItemObject> indices = new Object2IntOpenHashMap<>();
    private final Gradient gravity = new SimpleGravityGradient(0.04 * pow(20, 2));
    private final NumericalSolver solver = EulerSolver.INSTANCE;
    private final Hook<SpecialItemPickup> onPickup = HookFactory.createArrayBacked(SpecialItemPickup.class, hooks -> (player, object) -> {
        boolean pickup = false;

        for (SpecialItemPickup hook : hooks) {
            if (hook.shouldPickup(player, object)) {
                pickup = true;
            }
        }

        return pickup;
    });
    private final DynamicEntityManager dynamicEntityManager;
    private final List<SpecialItemObject> removal = new ArrayList<>();
    private final double minY;
    private StateVector state = new StateVector(new Vector3d[0]);

    public SpecialItemScene(Random random, ServerWorld world) {
        this.random = random;
        dynamicEntityManager = new DynamicEntityManager(world);
        this.scene = new Scene(new MixedMountContext(world, dynamicEntityManager));
        indices.defaultReturnValue(-1);
        minY = world.getBottomY() - 20.d;
    }

    public void init(TaskScheduler scheduler, HookRegistrar hooks) {
        scene.animate(1, scheduler);
        scene.onUpdateAnimation(this::updateSimulation);
        dynamicEntityManager.init(scheduler, hooks);
    }

    public Vector3d velocity(SpecialItemObject obj) {
        synchronized (this) {
            int i = indices.getInt(obj);

            return i == -1 ? new Vector3d(0) : state.getVector3(2 * i + 1);
        }
    }

    private synchronized void updateSimulation(double dt, AnimationContext ctx) {
        solver.solve(state, dt, gravity);

        removal.clear();

        for (int i = 0; i < objects.size(); i++) {
            SpecialItemObject obj = objects.get(i);

            if (obj.isPickedUp() || obj.isOnGround(ctx.world())) {
                // reset velocity
                state.getVector3(2 * i + 1).set(0);
                continue;
            }

            obj.position.set(state.getVector3(2 * i));

            if (obj.position.y < minY) {
                removal.add(obj);
            }
        }

        removal.forEach(this::remove);
    }

    public SpecialItemObject spawnItem(Vec3d pos, SpecialItem item, ItemStack stack, Translations translations, TranslatedText name) {
        var obj = new SpecialItemObject(scene, item, stack, translations, name);
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

    public void tickPickUp(ServerPlayerEntity player) {
        if (player.isSpectator() || player.getHealth() <= 0.f) return;

        Box box;
        Entity vehicle = player.getVehicle();

        if (vehicle != null && !vehicle.isRemoved()) {
            box = player.getBoundingBox().union(vehicle.getBoundingBox()).expand(1.0, 0.0, 1.0);
        } else {
            box = player.getBoundingBox().expand(1.0, 0.5, 1.0);
        }

        for (SpecialItemObject object : objects) {
            if (object.isPickedUp()
                    || object.getPickupDelay() > 0
                    || !object.intersects(box)
                    || !onPickup.invoker().shouldPickup(player, object)) continue;

            object.startPickup(player, () -> remove(object));

            float pitch = (random.nextFloat() - random.nextFloat()) * 1.4F + 2.0F;
            player.getWorld().playSound(null, object.position.x, object.position.y, object.position.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F, pitch);
        }
    }

    public Hook<SpecialItemPickup> onPickup() {
        return onPickup;
    }

    public int itemCount() {
        return objects.size();
    }

    public boolean contains(SpecialItemObject obj) {
        return indices.containsKey(obj);
    }

    public interface SpecialItemPickup {

        boolean shouldPickup(ServerPlayerEntity player, SpecialItemObject object);
    }
}

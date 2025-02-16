package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.scene.MixedMountContext;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class SpecialItemScene {

    private final Scene scene;

    public SpecialItemScene(ServerWorld world) {
        var dynamicEntityManager = new DynamicEntityManager(world);
        this.scene = new Scene(new MixedMountContext(world, dynamicEntityManager));
    }

    public void init(TaskScheduler scheduler) {
        scene.animate(1, scheduler);
    }

    public SpecialItemObject spawnItem(Vec3d pos, ItemStack stack) {
        var obj = new SpecialItemObject(stack);
        obj.position.set(pos.x, pos.y, pos.z);

        scene.add(obj);

        return obj;
    }

    public void remove(SpecialItemObject obj) {
        scene.remove(obj);
    }
}

package work.lclpnet.ap2.impl.scene;

import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;

public class Scene {

    private final ServerWorld world;
    private final Collection<Object3d> objects = new ArrayList<>();

    public Scene(ServerWorld world) {
        this.world = world;
    }

    public void add(Object3d object) {
        objects.add(object);
    }

    public void spawnObjects() {
        for (Object3d object : objects) {
            object.updateMatrixWorld();

            object.traverse(obj -> {
                if (obj instanceof Spawnable spawnable) {
                    spawnable.spawn(world);
                }
            });
        }
    }
}

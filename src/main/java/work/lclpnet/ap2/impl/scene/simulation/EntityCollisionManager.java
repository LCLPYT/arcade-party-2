package work.lclpnet.ap2.impl.scene.simulation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.world.World;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.kibu.physics.impl.bullet.collision.space.MinecraftSpace;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static work.lclpnet.kibu.physics.impl.bullet.math.Convert.toBullet;

public class EntityCollisionManager {

    private final World world;
    private final Supplier<Iterable<? extends Entity>> entities;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public EntityCollisionManager(World world, Supplier<Iterable<? extends Entity>> entities) {
        this.world = world;
        this.entities = entities;
    }

    public void init(TaskScheduler scheduler) {
        tick();

        scheduler.interval(this::tick, 1);
    }

    public synchronized void tick() {
        MinecraftSpace space = MinecraftSpace.get(world);

        // remove invalid entities and their rigid bodies
        entries.keySet().removeIf(uuid -> {
            Entity entity = world.getEntity(uuid);
            boolean removed = entity == null || !entity.isAlive();
            
            if (removed) {
                Entry entry = entries.get(uuid);
                
                if (entry != null) {
                    space.removeCollisionObject(entry.element.getRigidBody());
                }
            }
            
            return removed;
        });
        
        // update or add entity rigid bodies
        for (Entity entity : entities.get()) {
            if (!entity.isAlive()) continue;
            
            var element = entries.computeIfAbsent(entity.getUuid(), uuid -> {
                var entry = new Entry(entity);

                space.add(entry.element.getRigidBody());
                
                return entry;
            });

            element.update(entity);
        }
    }

    public Optional<EntityRefRigidBody> getRigidBody(Entity entity) {
        Optional<Entry> opt;

        synchronized (this) {
            opt = Optional.ofNullable(entries.get(entity.getUuid()));
        }

        return opt.map(e -> e.element.getRigidBody());
    }

    private static class Entry {
        private final EntityRefPhysicsElement element;
        private EntityPose lastPose;

        public Entry(Entity entity) {
            element = new EntityRefPhysicsElement(new EntityRef<>(entity));

            EntityRefRigidBody rigidBody = element.getRigidBody();
            rigidBody.setKinematic(true);
            rigidBody.setMass(0);
            rigidBody.setEnableSleep(false);

            lastPose = entity.getPose();
        }

        public void update(Entity entity) {
            EntityPose pose = entity.getPose();

            if (pose != lastPose) {
                lastPose = pose;
                element.getRigidBody().setCollisionShape(element.createShape());
            }

            EntityDimensions dimensions = entity.getDimensions(pose);

            EntityRefRigidBody rigidBody = element.getRigidBody();
            rigidBody.setPhysicsLocation(toBullet(entity.getPos().add(0, dimensions.height() * 0.5, 0)));
            rigidBody.activate();
        }
    }
}

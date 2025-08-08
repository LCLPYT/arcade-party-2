package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.object.BlockDisplayObject;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntity;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;

public class MonsterReveal {

    private static final double
            MARKER_DISTANCE = 20.0, MARKER_SCALE = 0.75;

    private final ModelManager modelManager;
    private final Participants participants;
    private final Collection<MonsterData<?>> monsters;
    private final DynamicEntityManager dynamicEntities;
    private final Scene scene;
    private final List<DangerMark> marks = new ArrayList<>();
    private @Nullable TaskHandle tickHandle = null;

    public MonsterReveal(ModelManager modelManager, Participants participants, ServerWorld world, Collection<MonsterData<?>> monsters) {
        this.modelManager = modelManager;
        this.participants = participants;
        this.monsters = monsters;

        this.dynamicEntities = new DynamicEntityManager(world);
        this.scene = new Scene(new DangerMountContext(world, dynamicEntities, new HashMap<>()));
    }

    public void start(TaskScheduler scheduler, HookRegistrar hooks) {
        dynamicEntities.init(scheduler, hooks);

        Model dangerModel = modelManager.getModel(Models.DANGER).orElseThrow();

        for (ServerPlayerEntity player : participants) {
            for (var monster : monsters) {
                var mark = new DangerMark(scene, player.getUuid(), monster);

                if (!mark.updatePosition(player)) continue;

                Object3d instance = dangerModel.createInstance(scene);
                instance.position.set(-0.5, -2.9, -0.5);

                mark.addChild(instance);
                mark.scale.set(MARKER_SCALE);

                for (Object3d obj : mark.traverse()) {
                    if (obj instanceof BlockDisplayObject display) {
                        display.setInterpolationDuration(1);
                        display.setGlowing(true);
                        display.setGlowColorOverride(0xff0000);
                    }
                }

                marks.add(mark);
                scene.add(mark);
            }
        }

        tickHandle = scheduler.interval(this::tick, 1);
    }

    public void stop() {
        if (tickHandle != null) {
            tickHandle.cancel();
            tickHandle = null;
        }

        scene.clear();
        marks.clear();
        dynamicEntities.clear();
    }

    private void tick() {
        var it = marks.iterator();

        while (it.hasNext()) {
            DangerMark mark = it.next();

            participants.getParticipant(mark.playerUuid).ifPresent(player -> {
                if (!mark.updatePosition(player)) {
                    it.remove();
                }
            });
        }
    }

    private record DangerMountContext(ServerWorld world, DynamicEntityManager manager, Map<DangerMark, DangerMarkEntity> marks) implements MountContext {

        @Override
        public <T extends Entity> Resolvable<T> spawn(@Nullable T entity, Object3d origin) {
            if (entity == null) {
                return Resolvable.none();
            }

            DangerMark mark = findDangerMark(origin);

            if (mark == null) {
                return Resolvable.none();
            }

            var markEntity = new DangerMarkEntity(entity, mark.playerUuid);

            marks.put(mark, markEntity);
            manager.add(markEntity);

            return Resolvable.constant(entity);
        }

        private @Nullable DangerMark findDangerMark(Object3d origin) {
            for (Object3d obj : origin.traverseParents()) {
                if (obj instanceof DangerMark mark) {
                    return mark;
                }
            }

            return null;
        }

        @Override
        public <T extends Entity> void remove(@Nullable T entity, Object3d origin) {
            if (entity != null) {
                entity.discard();
            }

            DangerMark mark = findDangerMark(origin);

            if (mark == null) return;

            var markEntity = marks.remove(mark);

            if (markEntity == null) return;

            manager.remove(markEntity);
        }
    }

    private static class DangerMark extends Object3d {
        private final UUID playerUuid;
        private final MonsterData<?> monster;

        private DangerMark(Scene scene, UUID playerUuid, MonsterData<?> monster) {
            super(scene);
            this.playerUuid = playerUuid;
            this.monster = monster;
        }

        public boolean updatePosition(ServerPlayerEntity player) {
            MobEntity mob = monster.mob();

            if (mob == null) {
                scene.remove(this);
                return false;
            }

            Vec3d playerEyePos = player.getEyePos();
            Vec3d dir = mob.getEyePos().subtract(playerEyePos).normalize();

            Vec3d pos = playerEyePos.add(dir.multiply(MARKER_DISTANCE));

            position.set(pos.getX(), pos.getY(), pos.getZ());
            updateMatrixWorld();

            return true;
        }
    }

    private record DangerMarkEntity(Entity entity, UUID playerUuid) implements DynamicEntity {

        @Override
        public Vec3d getPosition() {
            return entity.getPos();
        }

        @Override
        public Entity getEntity(ServerPlayerEntity player) {
            if (player.getUuid().equals(playerUuid)) {
                return entity;
            }

            return null;
        }

        @Override
        public void cleanup(ServerPlayerEntity player) {}
    }
}

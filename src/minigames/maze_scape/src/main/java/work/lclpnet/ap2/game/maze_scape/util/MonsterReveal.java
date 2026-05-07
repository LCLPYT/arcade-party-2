package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.gaco.core.api.Resolvable;
import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.gaco.scene.MountContext;
import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.animation.Interpolatable;
import work.lclpnet.gaco.scene.object.BlockDisplayObject;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.*;

import static work.lclpnet.ap2.impl.util.StreamUtil.instanceOf;

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

    public MonsterReveal(ModelManager modelManager, Participants participants, ServerLevel world, Collection<MonsterData<?>> monsters) {
        this.modelManager = modelManager;
        this.participants = participants;
        this.monsters = monsters;

        this.dynamicEntities = new DynamicEntityManager(world);
        this.scene = new Scene(new DangerMountContext(world, dynamicEntities, new HashMap<>()));
    }

    public void start(TaskScheduler scheduler, HookRegistrar hooks) {
        dynamicEntities.init(scheduler, hooks);

        Model dangerModel = modelManager.getModel(Models.DANGER).orElseThrow();

        for (ServerPlayer player : participants) {
            for (var monster : monsters) {
                var mark = new DangerMark(scene, player.getUUID(), monster);

                if (mark.update(player)) continue;

                Object3d instance = dangerModel.createInstance(scene);
                instance.position.set(-0.5, -2.9, -0.5);

                // manually enable interpolation for block displays
                instance.stream()
                        .flatMap(instanceOf(Interpolatable.class))
                        .forEach(obj -> obj.updateTickRate(1));

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
                if (mark.update(player)) {
                    it.remove();
                }
            });
        }
    }

    private record DangerMountContext(ServerLevel world, DynamicEntityManager manager, Map<DangerMark, DangerMarkEntity> marks) implements MountContext {

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

        public boolean update(ServerPlayer player) {
            Mob mob = monster.mob();

            if (mob == null) {
                scene.remove(this);
                return true;
            }

            Vec3 playerEyePos = player.getEyePosition();
            Vec3 dir = mob.getEyePosition().subtract(playerEyePos).normalize();

            Vec3 pos = playerEyePos.add(dir.scale(MARKER_DISTANCE));

            position.set(pos.x(), pos.y(), pos.z());
            updateMatrixWorld();

            return false;
        }
    }

    private record DangerMarkEntity(Entity entity, UUID playerUuid) implements DynamicEntity {

        @Override
        public Vec3 getPosition() {
            return entity.position();
        }

        @Override
        public Entity getEntity(ServerPlayer player) {
            if (player.getUUID().equals(playerUuid)) {
                return entity;
            }

            return null;
        }

        @Override
        public void cleanup(ServerPlayer player) {}
    }
}

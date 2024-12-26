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
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.MountContext;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.util.model.Models;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntity;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class MonsterReveal {

    private static final double
            MARKER_DISTANCE = 20.0, MARKER_SCALE = 0.75;

    private final ModelManager modelManager;
    private final Participants participants;
    private final Collection<MonsterData> monsters;
    private final DynamicEntityManager dynamicEntities;
    private final Scene scene;
    private final List<DangerMark> marks = new ArrayList<>();
    private @Nullable TaskHandle tickHandle = null;

    public MonsterReveal(ModelManager modelManager, Participants participants, ServerWorld world, Collection<MonsterData> monsters) {
        this.modelManager = modelManager;
        this.participants = participants;
        this.monsters = monsters;

        this.dynamicEntities = new DynamicEntityManager(world);
        this.scene = new Scene(new DangerMountContext(world, dynamicEntities));
    }

    public void start(TaskScheduler scheduler) {
        dynamicEntities.init(scheduler);

        Model dangerModel = modelManager.getModel(Models.DANGER).orElseThrow();

        for (ServerPlayerEntity player : participants) {
            for (MonsterData monster : monsters) {
                var mark = new DangerMark(player.getUuid(), monster);

                if (!mark.updatePosition(player)) continue;

                Object3d instance = dangerModel.createInstance();
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

    private record DangerMountContext(ServerWorld world, DynamicEntityManager manager) implements MountContext {

        @Override
        public <T extends Entity> Resolvable<T> spawn(T entity, Object3d origin) {
            DangerMark dangerMark = null;

            for (Object3d obj : origin.traverseParents()) {
                if (obj instanceof DangerMark mark) {
                    dangerMark = mark;
                    break;
                }
            }

            if (dangerMark == null) {
                return Resolvable.none();
            }

            manager.add(new DangerMarkEntity(entity, dangerMark.playerUuid));

            return Resolvable.constant(entity);
        }
    }

    private class DangerMark extends Object3d {
        private final UUID playerUuid;
        private final MonsterData monster;

        private DangerMark(UUID playerUuid, MonsterData monster) {
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

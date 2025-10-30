package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;

import java.util.function.BiConsumer;

public class SameRoomBehaviour<T extends MobEntity> implements MonsterBehaviour {

    private final MSStruct struct;
    private final int timeout;
    private final BiConsumer<T, LivingEntity> action;
    private int sameRoomTimer = 0;

    public SameRoomBehaviour(MSStruct struct, int timeout, BiConsumer<T, LivingEntity> action) {
        this.struct = struct;
        this.timeout = timeout;
        this.action = action;
    }

    @Override
    public void tick(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || !isInSameRoom(mob.getEntityPos(), target.getEntityPos())) {
            sameRoomTimer = 0;
            return;
        }

        if (sameRoomTimer++ < timeout) return;

        sameRoomTimer = 0;

        trigger(mob, target);
    }

    @SuppressWarnings("unchecked")
    private void trigger(MobEntity mob, LivingEntity target) {
        action.accept((T) mob, target);
    }

    private boolean isInSameRoom(Vec3d first, Vec3d second) {
        var wardenNode = struct.nodeAt(first);
        var targetNode = struct.nodeAt(second);

        return wardenNode != null && wardenNode == targetNode;
    }
}

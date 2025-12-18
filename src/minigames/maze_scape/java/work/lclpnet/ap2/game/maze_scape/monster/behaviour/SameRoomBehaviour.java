package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;

import java.util.function.BiConsumer;

public class SameRoomBehaviour<T extends Mob> implements MonsterBehaviour {

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
    public void tick(Mob mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || !isInSameRoom(mob.position(), target.position())) {
            sameRoomTimer = 0;
            return;
        }

        if (sameRoomTimer++ < timeout) return;

        sameRoomTimer = 0;

        trigger(mob, target);
    }

    @SuppressWarnings("unchecked")
    private void trigger(Mob mob, LivingEntity target) {
        action.accept((T) mob, target);
    }

    private boolean isInSameRoom(Vec3 first, Vec3 second) {
        var wardenNode = struct.nodeAt(first);
        var targetNode = struct.nodeAt(second);

        return wardenNode != null && wardenNode == targetNode;
    }
}

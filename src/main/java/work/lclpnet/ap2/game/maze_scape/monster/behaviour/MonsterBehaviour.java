package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.entity.mob.MobEntity;

public interface MonsterBehaviour {

    void tick(MobEntity mob);

    default void init(MobEntity mob) {}

    default void onKillAcquired(MobEntity mob) {}
}

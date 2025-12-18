package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.world.entity.Mob;

public interface MonsterBehaviour {

    void tick(Mob mob);

    default void init(Mob mob) {}

    default void onKillAcquired(Mob mob) {}
}

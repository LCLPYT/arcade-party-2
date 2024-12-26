package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;

public interface MonsterData {

    void init();

    void tick();

    void onKillAcquired();

    @Nullable MobEntity mob();
}

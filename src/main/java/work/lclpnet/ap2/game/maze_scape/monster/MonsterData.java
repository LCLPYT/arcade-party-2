package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;

public interface MonsterData<T extends MobEntity> {

    @Nullable T mob();

    void init(T mob);

    void tick(T mob);

    void onKillAcquired(T mob);

    default void init() {
        T mob = mob();

        if (mob != null) {
            init(mob);
        }
    }

    default void tick() {
        T mob = mob();

        if (mob != null) {
            tick(mob);
        }
    }

    default void onKillAcquired() {
        T mob = mob();

        if (mob != null) {
            onKillAcquired(mob);
        }
    }
}

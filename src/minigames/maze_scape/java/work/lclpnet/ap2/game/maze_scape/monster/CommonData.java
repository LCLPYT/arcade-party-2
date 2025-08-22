package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.MonsterBehaviour;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;

import java.util.List;
import java.util.UUID;

class CommonData implements MonsterData<MobEntity> {

    private final UUID uuid;
    private final MSManager manager;
    private final List<MonsterBehaviour> behaviours;

    public CommonData(MonsterArgs args, List<MonsterBehaviour> behaviours) {
        this.uuid = args.uuid();
        this.manager = args.manager();
        this.behaviours = behaviours;
    }

    @Override
    public @Nullable MobEntity mob() {
        if (manager.world().getEntity(uuid) instanceof MobEntity mob) {
            return mob;
        }

        return null;
    }

    public MSManager manager() {
        return manager;
    }

    @Override
    public void init(MobEntity mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.init(mob);
        }
    }

    @Override
    public void tick(MobEntity mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.tick(mob);
        }
    }

    @Override
    public void onKillAcquired(MobEntity mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.onKillAcquired(mob);
        }
    }
}

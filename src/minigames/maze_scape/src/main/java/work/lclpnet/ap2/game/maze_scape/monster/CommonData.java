package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.MonsterBehaviour;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;

import java.util.List;
import java.util.UUID;

class CommonData implements MonsterData<Mob> {

    private final UUID uuid;
    private final MSManager manager;
    private final List<MonsterBehaviour> behaviours;

    public CommonData(MonsterArgs args, List<MonsterBehaviour> behaviours) {
        this.uuid = args.uuid();
        this.manager = args.manager();
        this.behaviours = behaviours;
    }

    @Override
    public @Nullable Mob mob() {
        if (manager.world().getEntity(uuid) instanceof Mob mob) {
            return mob;
        }

        return null;
    }

    public MSManager manager() {
        return manager;
    }

    @Override
    public void init(Mob mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.init(mob);
        }
    }

    @Override
    public void tick(Mob mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.tick(mob);
        }
    }

    @Override
    public void onKillAcquired(Mob mob) {
        for (MonsterBehaviour behaviour : behaviours) {
            behaviour.onKillAcquired(mob);
        }
    }
}

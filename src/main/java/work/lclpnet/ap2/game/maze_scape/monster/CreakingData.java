package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.CreakingEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;

import java.util.List;

public class CreakingData implements MonsterData<CreakingEntity> {

    private final CommonData common;

    public CreakingData(MonsterArgs args) {
        common = new CommonData(args, List.of(
                new ValidPositionBehaviour(args.manager(), args.logger())
        ));
    }

    @Override
    public @Nullable CreakingEntity mob() {
        if (common.mob() instanceof CreakingEntity creaking) {
            return creaking;
        }

        return null;
    }

    @Override
    public void init(CreakingEntity mob) {
        common.init(mob);
    }

    @Override
    public void tick(CreakingEntity mob) {
        common.tick(mob);
    }

    @Override
    public void onKillAcquired(CreakingEntity mob) {
        common.onKillAcquired(mob);
    }
}

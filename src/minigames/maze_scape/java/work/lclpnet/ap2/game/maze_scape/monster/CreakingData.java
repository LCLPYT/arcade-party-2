package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.CreakingEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.ap2.game.maze_scape.util.VisibilityChecker;

import java.util.List;

public class CreakingData implements MonsterData<CreakingEntity> {

    private final MonsterArgs args;
    private final CommonData common;
    private final VisibilityChecker visibilityChecker;

    public CreakingData(MonsterArgs args) {
        this.args = args;

        common = new CommonData(args, List.of(
                new ValidPositionBehaviour(args.manager(), args.logger())
        ));

        this.visibilityChecker = new VisibilityChecker(args.manager().world());
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

    public boolean isBeingLookedAt(CreakingEntity mob) {
        return visibilityChecker.isAnyoneLookingAt(mob, mob.getPos(), args.manager().participants());
    }
}

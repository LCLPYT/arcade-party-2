package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.world.entity.monster.creaking.Creaking;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.ap2.impl.util.VisibilityChecker;

import java.util.List;

public class CreakingData implements MonsterData<Creaking> {

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
    public @Nullable Creaking mob() {
        if (common.mob() instanceof Creaking creaking) {
            return creaking;
        }

        return null;
    }

    @Override
    public void init(Creaking mob) {
        common.init(mob);
    }

    @Override
    public void tick(Creaking mob) {
        common.tick(mob);
    }

    @Override
    public void onKillAcquired(Creaking mob) {
        common.onKillAcquired(mob);
    }

    public boolean isBeingLookedAt(Creaking mob) {
        return visibilityChecker.isAnyoneLookingAt(mob, mob.position(), args.manager().participants());
    }
}

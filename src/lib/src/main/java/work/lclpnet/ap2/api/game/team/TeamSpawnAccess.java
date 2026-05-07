package work.lclpnet.ap2.api.game.team;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.util.PositionRotation;

public interface TeamSpawnAccess {

    @Nullable
    PositionRotation getSpawn(Team team);
}

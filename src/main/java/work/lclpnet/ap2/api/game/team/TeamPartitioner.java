package work.lclpnet.ap2.api.game.team;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public interface TeamPartitioner {

    @NotNull
    Map<ServerPlayerEntity, Team> splitIntoTeams(Set<ServerPlayerEntity> players, Set<Team> teams);
}

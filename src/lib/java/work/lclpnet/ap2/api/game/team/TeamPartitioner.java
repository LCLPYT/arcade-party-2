package work.lclpnet.ap2.api.game.team;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public interface TeamPartitioner {

    @NotNull
    Map<ServerPlayer, Team> splitIntoTeams(Set<ServerPlayer> players, Set<Team> teams);
}

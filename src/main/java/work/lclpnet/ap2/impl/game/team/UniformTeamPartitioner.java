package work.lclpnet.ap2.impl.game.team;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamPartitioner;

import java.util.*;

public class UniformTeamPartitioner implements TeamPartitioner {

    private final Random random;

    public UniformTeamPartitioner(Random random) {
        this.random = random;
    }

    @Override
    public @NotNull Map<ServerPlayerEntity, Team> splitIntoTeams(Set<ServerPlayerEntity> players, Set<Team> teams) {
        if (teams.isEmpty()) {
            throw new IllegalArgumentException("Teams must not be empty");
        }

        Object2IntMap<Team> playerCount = new Object2IntOpenHashMap<>(teams.size());
        teams.forEach(team -> playerCount.put(team, team.getPlayerCount()));

        // fill the teams with the fewest members first
        PriorityQueue<Team> queue = new PriorityQueue<>(Comparator.comparingInt(playerCount::getInt));
        queue.addAll(teams);

        List<ServerPlayerEntity> playerList = new ArrayList<>(players);
        Map<ServerPlayerEntity, Team> mapping = new HashMap<>();

        while (!playerList.isEmpty()) {
            ServerPlayerEntity player = playerList.remove(random.nextInt(playerList.size()));

            Team team = Objects.requireNonNull(queue.poll(), "Team is null");

            mapping.put(player, team);
            playerCount.compute(team, (k, v) -> v == null ? 1 : v + 1);

            queue.offer(team);
        }

        return mapping;
    }
}

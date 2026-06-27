package work.lclpnet.ap2.impl.game.team;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.player.PlayerRankView;
import work.lclpnet.ap2.game.team.BalancedTeamPartitioner;
import work.lclpnet.ap2.game.team.Team;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalancedTeamPartitionerTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void splitIntoTeams_noTeams_throws() {
        var part = new BalancedTeamPartitioner(rankView(Map.of()), new Random());

        assertThrows(IllegalArgumentException.class, () -> part.splitIntoTeams(Set.of(), Set.of()));
    }

    @Test
    void splitIntoTeams_noPlayers_empty() {
        var part = new BalancedTeamPartitioner(rankView(Map.of()), new Random());

        var mapping = part.splitIntoTeams(Set.of(), Set.of(team()));

        assertTrue(mapping.isEmpty());
    }

    @RepeatedTest(100)
    void splitIntoTeams_single() {
        ServerPlayer playerA = player("a");
        var part = new BalancedTeamPartitioner(rankView(Map.of(playerA, 1)), new Random());

        Team teamRed = team(), teamBlue = team();
        Set<Team> teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(Set.of(playerA), teams);

        assertTrue(teams.contains(mapping.get(playerA)));
    }

    @RepeatedTest(100)
    void splitIntoTeams_bestPlayersNotSameTeam() {
        ServerPlayer playerA = player("a"), playerB = player("b"),
                playerC = player("c"), playerD = player("d");

        // a and b are the two best players
        var rankView = rankView(Map.of(playerA, 1, playerB, 2, playerC, 3, playerD, 4));
        var part = new BalancedTeamPartitioner(rankView, new Random());

        Team teamRed = team(), teamBlue = team();
        var players = Set.of(playerA, playerB, playerC, playerD);
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(players, teams);

        // the two best players must end up on different teams
        assertNotSame(mapping.get(playerA), mapping.get(playerB));

        // every team must have the same rank sum (1 + 4 == 2 + 3 == 5)
        assertEquals(Set.of(5), rankSums(mapping, rankView).stream().collect(Collectors.toSet()));
    }

    @RepeatedTest(100)
    void splitIntoTeams_uniformSizes() {
        ServerPlayer[] players = new ServerPlayer[8];
        Map<ServerPlayer, Integer> ranks = new HashMap<>();
        for (int i = 0; i < players.length; i++) {
            players[i] = player("p" + i);
            ranks.put(players[i], i + 1);
        }

        var rankView = rankView(ranks);
        var part = new BalancedTeamPartitioner(rankView, new Random());

        Team teamRed = team(), teamBlue = team(), teamGreen = team();
        var teams = Set.of(teamRed, teamBlue, teamGreen);

        var mapping = part.splitIntoTeams(Set.of(players), teams);
        var byTeam = mapping.keySet().stream().collect(Collectors.groupingBy(mapping::get));

        // 8 players over 3 teams -> sizes 3, 3, 2 (differ by at most 1)
        var sizes = byTeam.values().stream().map(List::size).sorted().toList();
        assertEquals(List.of(2, 3, 3), sizes);
    }

    @RepeatedTest(100)
    void splitIntoTeams_balancedRankSums() {
        ServerPlayer[] players = new ServerPlayer[8];
        Map<ServerPlayer, Integer> ranks = new HashMap<>();
        for (int i = 0; i < players.length; i++) {
            players[i] = player("p" + i);
            ranks.put(players[i], i + 1);
        }

        var rankView = rankView(ranks);
        var part = new BalancedTeamPartitioner(rankView, new Random());

        Team teamRed = team(), teamBlue = team();
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(Set.of(players), teams);

        // ranks 1..8 split into two teams -> both rank sums equal 18
        assertEquals(Set.of(18), rankSums(mapping, rankView).stream().collect(Collectors.toSet()));
    }

    @RepeatedTest(100)
    void splitIntoTeams_preConfigured_balancesSizes() {
        ServerPlayer playerA = player("a"), playerB = player("b"),
                playerC = player("c"), playerD = player("d"),
                playerE = player("e"), playerF = player("f");

        var rankView = rankView(Map.of(
                playerA, 1, playerB, 2, playerC, 3,
                playerD, 4, playerE, 5, playerF, 6));
        var part = new BalancedTeamPartitioner(rankView, new Random());

        Team teamRed = team(), teamBlue = team(Set.of(playerA, playerB));
        var players = Set.of(playerC, playerD, playerE, playerF);
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(players, teams);
        var byTeam = mapping.keySet().stream().collect(Collectors.groupingBy(mapping::get));

        assertEquals(3, byTeam.get(teamRed).size());
        assertEquals(1, byTeam.get(teamBlue).size());
    }

    private static List<Integer> rankSums(Map<ServerPlayer, Team> mapping, PlayerRankView rankView) {
        return mapping.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue,
                        Collectors.summingInt(e -> rankView.rank(e.getKey()))))
                .values().stream().toList();
    }

    private static PlayerRankView rankView(Map<ServerPlayer, Integer> ranks) {
        PlayerRankView rankView = mock();

        when(rankView.rank(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ServerPlayer player = invocation.getArgument(0);
            return ranks.getOrDefault(player, 1);
        });

        return rankView;
    }

    private static Team team() {
        return team(Set.of());
    }

    private static Team team(Set<ServerPlayer> members) {
        Team team = mock();

        when(team.getPlayers()).thenReturn(members);
        when(team.getPlayerCount()).thenReturn(members.size());

        return team;
    }

    private static ServerPlayer player(String name) {
        ServerPlayer player = mock();
        UUID uuid = UUID.randomUUID();

        when(player.getUUID()).thenReturn(uuid);
        when(player.getScoreboardName()).thenReturn(name);

        return player;
    }
}

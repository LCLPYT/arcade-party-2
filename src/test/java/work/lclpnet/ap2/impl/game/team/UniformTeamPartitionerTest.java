package work.lclpnet.ap2.impl.game.team;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UniformTeamPartitionerTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void splitIntoTeams_noTeams_throws() {
        var part = new UniformTeamPartitioner(new Random());

        assertThrows(IllegalArgumentException.class, () -> part.splitIntoTeams(Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> part.splitIntoTeams(Set.of(player("a")), Set.of()));
    }

    @Test
    void splitIntoTeams_noPlayers_empty() {
        var part = new UniformTeamPartitioner(new Random());

        var mapping = part.splitIntoTeams(Set.of(), Set.of(team()));

        assertTrue(mapping.isEmpty());
    }

    @Test
    void splitIntoTeams_single() {
        var part = new UniformTeamPartitioner(new Random());

        ServerPlayerEntity playerA = player("a");
        Team teamRed = team(), teamBlue = team();

        Set<Team> teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(Set.of(playerA), teams);

        assertTrue(teams.contains(mapping.get(playerA)));
    }

    @RepeatedTest(100)
    void splitIntoTeams_notSameTeam() {
        var part = new UniformTeamPartitioner(new Random());

        ServerPlayerEntity playerA = player("a"), playerB = player("b");
        Team teamRed = team(), teamBlue = team();

        var players = Set.of(playerA, playerB);
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(players, teams);

        assertNotSame(mapping.get(playerA), mapping.get(playerB));
    }

    @RepeatedTest(100)
    void splitIntoTeams_preConfigured() {
        var part = new UniformTeamPartitioner(new Random());

        ServerPlayerEntity playerA = player("a"), playerB = player("b");
        Team teamRed = team(), teamBlue = team(Set.of(playerA));

        var players = Set.of(playerB);
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(players, teams);

        assertEquals(Map.of(playerB, teamRed), mapping);
    }

    @RepeatedTest(100)
    void splitIntoTeams_preConfigured_complex() {
        var part = new UniformTeamPartitioner(new Random());

        ServerPlayerEntity
                playerA = player("a"),
                playerB = player("b"),
                playerC = player("c"),
                playerD = player("d"),
                playerE = player("e"),
                playerF = player("f");

        Team teamRed = team(), teamBlue = team(Set.of(playerA, playerB));

        var players = Set.of(playerC, playerD, playerE, playerF);
        var teams = Set.of(teamRed, teamBlue);

        var mapping = part.splitIntoTeams(players, teams);
        var byTeam = mapping.keySet().stream().collect(Collectors.groupingBy(mapping::get));

        assertEquals(3, byTeam.get(teamRed).size());
        assertEquals(1, byTeam.get(teamBlue).size());
    }

    private static Team team() {
        return team(Set.of());
    }

    private static Team team(Set<ServerPlayerEntity> members) {
        Team team = mock();

        when(team.getPlayers()).thenReturn(members);
        when(team.getPlayerCount()).thenReturn(members.size());

        return team;
    }

    private static ServerPlayerEntity player(String name) {
        ServerPlayerEntity player = mock();
        UUID uuid = UUID.randomUUID();

        when(player.getUuid()).thenReturn(uuid);
        when(player.getEntityName()).thenReturn(name);

        return player;
    }
}
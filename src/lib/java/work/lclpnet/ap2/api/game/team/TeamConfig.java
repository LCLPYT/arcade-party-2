package work.lclpnet.ap2.api.game.team;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.game.team.UniformTeamPartitioner;

import java.util.Map;
import java.util.Random;

public interface TeamConfig {

    @NotNull
    TeamPartitioner getPartitioner();

    @NotNull
    Map<ServerPlayerEntity, TeamKey> getMapping();

    static TeamConfig defaultConfig() {
        UniformTeamPartitioner partitioner = new UniformTeamPartitioner(new Random());

        return new TeamConfig() {
            @Override
            public @NotNull TeamPartitioner getPartitioner() {
                return partitioner;
            }

            @Override
            public @NotNull Map<ServerPlayerEntity, TeamKey> getMapping() {
                return Map.of();
            }
        };
    }
}

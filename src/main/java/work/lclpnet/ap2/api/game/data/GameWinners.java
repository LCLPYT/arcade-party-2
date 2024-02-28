package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public interface GameWinners<Ref extends SubjectRef> {

    Set<ServerPlayerEntity> getPlayers();

    Set<Ref> getSubjects();
}

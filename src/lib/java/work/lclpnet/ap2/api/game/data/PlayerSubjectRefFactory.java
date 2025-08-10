package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a reference for the subject of a player.
 * The subject can be the player itself, or the team the player is in.
 * If the player has no subject, null will be returned.
 * @param <Ref> The SubjectRef type.
 */
public interface PlayerSubjectRefFactory<Ref extends SubjectRef> extends SubjectRefFactory<ServerPlayerEntity, Ref> {

    @Nullable
    Ref create(ServerPlayerEntity subject);
}

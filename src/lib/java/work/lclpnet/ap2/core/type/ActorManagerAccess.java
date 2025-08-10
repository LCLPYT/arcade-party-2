package work.lclpnet.ap2.core.type;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.actor.ActorManager;

public interface ActorManagerAccess {

    @NotNull ActorManager ap2$getActorManager();

    static @NotNull ActorManager get(ServerWorld world) {
        return ((ActorManagerAccess) world).ap2$getActorManager();
    }
}

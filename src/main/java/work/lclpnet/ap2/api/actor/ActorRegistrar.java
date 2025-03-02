package work.lclpnet.ap2.api.actor;

import net.minecraft.util.Identifier;

public interface ActorRegistrar {

    void register(Identifier id, ActorFactory<? extends Actor> factory);
}

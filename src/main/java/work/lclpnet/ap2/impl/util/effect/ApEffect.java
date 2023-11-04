package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.server.network.ServerPlayerEntity;

public interface ApEffect {

    void apply(ServerPlayerEntity player);

    void remove(ServerPlayerEntity player);

    /**
     * @return Whether this effect should be applied to everyone or only to participants.
     */
    default boolean isGlobal() {
        return true;
    }
}

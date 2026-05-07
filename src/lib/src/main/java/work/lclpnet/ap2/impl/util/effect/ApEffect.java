package work.lclpnet.ap2.impl.util.effect;

import net.minecraft.server.level.ServerPlayer;

public interface ApEffect {

    void apply(ServerPlayer player);

    void remove(ServerPlayer player);

    /**
     * @return Whether this effect should be applied to everyone or only to participants.
     */
    default boolean isGlobal() {
        return true;
    }
}

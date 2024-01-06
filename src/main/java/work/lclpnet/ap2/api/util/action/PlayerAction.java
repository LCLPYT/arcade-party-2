package work.lclpnet.ap2.api.util.action;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerAction {

    void onAction(ServerPlayerEntity player);
}

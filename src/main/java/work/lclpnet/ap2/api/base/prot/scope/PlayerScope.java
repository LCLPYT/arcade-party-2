package work.lclpnet.ap2.api.base.prot.scope;

import net.minecraft.entity.player.PlayerEntity;

public interface PlayerScope {

    boolean isWithinScope(PlayerEntity player);
}

package work.lclpnet.ap2.api.base.prot.type;

import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.scope.PlayerScope;

public class PlayerProtectionType implements ProtectionType<PlayerScope> {

    @Override
    public PlayerScope getGlobalScope() {
        return player -> true;
    }

    @Override
    public PlayerScope getResultingScope(PlayerScope disallowed, PlayerScope allowed) {
        return player -> disallowed.isWithinScope(player) && !allowed.isWithinScope(player);
    }
}

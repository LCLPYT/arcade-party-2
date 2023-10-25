package work.lclpnet.ap2.api.base.prot.type;

import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.scope.WorldBlockScope;

public class WorldBlockProtectionType implements ProtectionType<WorldBlockScope> {

    @Override
    public WorldBlockScope getGlobalScope() {
        return (world, pos) -> true;
    }

    @Override
    public WorldBlockScope getResultingScope(WorldBlockScope disallowed, WorldBlockScope allowed) {
        return (world, pos) -> disallowed.isWithinScope(world, pos) && !allowed.isWithinScope(world, pos);
    }
}

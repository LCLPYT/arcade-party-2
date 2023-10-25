package work.lclpnet.ap2.api.base.prot.type;

import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.scope.EntityBlockScope;

public class EntityBlockProtectionType implements ProtectionType<EntityBlockScope> {

    @Override
    public EntityBlockScope getGlobalScope() {
        return (entity, pos) -> true;
    }

    @Override
    public EntityBlockScope getResultingScope(EntityBlockScope disallowed, EntityBlockScope allowed) {
        return (entity, pos) -> disallowed.isWithinScope(entity, pos) && !allowed.isWithinScope(entity, pos);
    }
}

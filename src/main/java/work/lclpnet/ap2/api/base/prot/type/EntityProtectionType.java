package work.lclpnet.ap2.api.base.prot.type;

import work.lclpnet.ap2.api.base.prot.ProtectionType;
import work.lclpnet.ap2.api.base.prot.scope.EntityScope;

public class EntityProtectionType implements ProtectionType<EntityScope> {

    @Override
    public EntityScope getGlobalScope() {
        return entity -> true;
    }

    @Override
    public EntityScope getResultingScope(EntityScope disallowed, EntityScope allowed) {
        return entity -> disallowed.isWithinScope(entity) && !allowed.isWithinScope(entity);
    }
}

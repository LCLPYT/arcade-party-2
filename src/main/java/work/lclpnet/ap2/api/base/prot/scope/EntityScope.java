package work.lclpnet.ap2.api.base.prot.scope;

import net.minecraft.entity.Entity;

public interface EntityScope {

    boolean isWithinScope(Entity entity);
}

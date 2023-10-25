package work.lclpnet.ap2.api.base.prot.scope;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public interface EntityBlockScope {

    boolean isWithinScope(Entity entity, BlockPos pos);
}

package work.lclpnet.ap2.api.base.prot.scope;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface WorldBlockScope {

    boolean isWithinScope(World world, BlockPos pos);
}

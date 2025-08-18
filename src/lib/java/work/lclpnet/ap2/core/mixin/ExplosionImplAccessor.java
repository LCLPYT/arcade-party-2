package work.lclpnet.ap2.core.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ExplosionImpl.class)
public interface ExplosionImplAccessor {

    @Invoker
    List<BlockPos> invokeGetBlocksToDestroy();

    @Invoker
    void invokeDamageEntities();
}

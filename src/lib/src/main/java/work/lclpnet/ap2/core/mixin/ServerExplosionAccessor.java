package work.lclpnet.ap2.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ServerExplosion.class)
public interface ServerExplosionAccessor {

    @Invoker
    List<BlockPos> invokeCalculateExplodedPositions();

    @Invoker
    void invokeHurtEntities();
}

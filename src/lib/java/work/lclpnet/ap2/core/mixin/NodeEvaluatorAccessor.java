package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NodeEvaluator.class)
public interface NodeEvaluatorAccessor {

    @Accessor
    Mob getMob();
}

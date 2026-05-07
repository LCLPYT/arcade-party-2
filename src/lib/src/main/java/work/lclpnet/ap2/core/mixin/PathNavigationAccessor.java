package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {

    @Accessor
    NodeEvaluator getNodeEvaluator();
}

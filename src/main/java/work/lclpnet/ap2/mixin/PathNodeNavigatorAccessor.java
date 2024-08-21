package work.lclpnet.ap2.mixin;

import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNodeNavigator.class)
public interface PathNodeNavigatorAccessor {

    @Accessor
    PathNodeMaker getPathNodeMaker();
}

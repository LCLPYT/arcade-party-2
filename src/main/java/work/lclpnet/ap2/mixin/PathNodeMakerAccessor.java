package work.lclpnet.ap2.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNodeMaker.class)
public interface PathNodeMakerAccessor {

    @Accessor
    Int2ObjectMap<PathNode> getPathNodeCache();
}

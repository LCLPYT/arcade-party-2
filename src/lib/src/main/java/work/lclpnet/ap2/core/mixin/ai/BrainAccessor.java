package work.lclpnet.ap2.core.mixin.ai;

import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(Brain.class)
public interface BrainAccessor {

    @Accessor("availableBehaviorsByPriority")
    Map<?, ?> getAvailableBehaviorsByPriority();

    @Accessor("activityRequirements")
    Map<?, ?> getActivityRequirements();

    @Accessor("activityMemoriesToEraseWhenStopped")
    Map<?, ?> getActivityMemoriesToEraseWhenStopped();
}

package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.CreakingBrain;
import net.minecraft.entity.mob.CreakingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreakingBrain.class)
public interface CreakingBrainAccessor {

    @Invoker static void invokeAddCoreTasks(Brain<CreakingEntity> brain) {}
}

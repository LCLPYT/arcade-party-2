package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.WardenBrain;
import net.minecraft.entity.mob.WardenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WardenBrain.class)
public interface WardenBrainAccessor {

    @Invoker static void invokeAddCoreActivities(Brain<WardenEntity> brain) {}
    @Invoker static void invokeAddIdleActivities(Brain<WardenEntity> brain) {}
    @Invoker static void invokeAddRoarActivities(Brain<WardenEntity> brain) {}
    @Invoker static void invokeAddInvestigateActivities(Brain<WardenEntity> brain) {}
    @Invoker static void invokeAddSniffActivities(Brain<WardenEntity> brain) {}
}

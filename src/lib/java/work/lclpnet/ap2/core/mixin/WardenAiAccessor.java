package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WardenAi.class)
public interface WardenAiAccessor {

    @Invoker static void invokeInitCoreActivity(Brain<Warden> brain) {}
    @Invoker static void invokeInitIdleActivity(Brain<Warden> brain) {}
    @Invoker static void invokeInitRoarActivity(Brain<Warden> brain) {}
    @Invoker static void invokeInitInvestigateActivity(Brain<Warden> brain) {}
    @Invoker static void invokeInitSniffingActivity(Brain<Warden> brain) {}
}

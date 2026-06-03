package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WardenAi.class)
public interface WardenAiAccessor {

    @Invoker static ActivityData<Warden> invokeInitCoreActivity() {
        throw new AssertionError();
    }

    @Invoker static ActivityData<Warden> invokeInitIdleActivity() {
        throw new AssertionError();
    }

    @Invoker static ActivityData<Warden> invokeInitRoarActivity() {
        throw new AssertionError();
    }

    @Invoker static ActivityData<Warden> invokeInitInvestigateActivity() {
        throw new AssertionError();
    }

    @Invoker static ActivityData<Warden> invokeInitSniffingActivity() {
        throw new AssertionError();
    }
}

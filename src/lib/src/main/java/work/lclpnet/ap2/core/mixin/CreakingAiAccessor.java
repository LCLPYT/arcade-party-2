package work.lclpnet.ap2.core.mixin;

import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.creaking.CreakingAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreakingAi.class)
public interface CreakingAiAccessor {

    @Invoker static ActivityData<Creaking> invokeInitCoreActivity() {
        throw new AssertionError();
    }
}

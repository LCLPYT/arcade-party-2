package work.lclpnet.ap2.core.mixin;

import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;

import java.util.List;

@Mixin(WardenAi.class)
public abstract class WardenAiMixin {

    @Shadow @Final private static List<MemoryModuleType<?>> MEMORY_TYPES;

    @Shadow @Final private static List<SensorType<? extends Sensor<? super Warden>>> SENSOR_TYPES;

    @Inject(
            method = "makeBrain",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ap2$overrideCreate(Warden warden, Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        var override = BrainCreationCallback.Warden.HOOK.invoker().createBrain(warden, () ->
                Brain.provider(MEMORY_TYPES, SENSOR_TYPES).makeBrain(dynamic));

        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}

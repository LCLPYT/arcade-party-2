package work.lclpnet.ap2.core.mixin;

import com.mojang.serialization.Dynamic;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.mob.WardenBrain;
import net.minecraft.entity.mob.WardenEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.BrainCreationCallback;

import java.util.List;

@Mixin(WardenBrain.class)
public abstract class WardenBrainMixin {

    @Shadow @Final private static List<MemoryModuleType<?>> MEMORY_MODULES;

    @Shadow @Final private static List<SensorType<? extends Sensor<? super WardenEntity>>> SENSORS;

    @Inject(
            method = "create",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ap2$overrideCreate(WardenEntity warden, Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        var override = BrainCreationCallback.Warden.HOOK.invoker().createBrain(warden, () ->
                Brain.createProfile(MEMORY_MODULES, SENSORS).deserialize(dynamic));

        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}

package work.lclpnet.ap2.mixin;

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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.hook.BrainCreationCallback;
import work.lclpnet.ap2.type.WardenBrainHandle;

import java.util.List;

@Mixin(WardenBrain.class)
public abstract class WardenBrainMixin {

    @Shadow @Final private static List<MemoryModuleType<?>> MEMORY_MODULES;

    @Shadow @Final private static List<SensorType<? extends Sensor<? super WardenEntity>>> SENSORS;

    @Shadow private static void addCoreActivities(Brain<WardenEntity> brain) {}

    @Shadow private static void addIdleActivities(Brain<WardenEntity> brain) {}

    @Shadow private static void addRoarActivities(Brain<WardenEntity> brain) {}

    @Shadow private static void addInvestigateActivities(Brain<WardenEntity> brain) {}

    @Shadow private static void addSniffActivities(Brain<WardenEntity> brain) {}

    @Inject(
            method = "create",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ap2$overrideCreate(WardenEntity warden, Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        var override = BrainCreationCallback.Warden.HOOK.invoker().createBrain(warden, dynamic, WardenBrainMixin::createHandle);

        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Unique
    private static WardenBrainHandle createHandle() {
        return new WardenBrainHandle() {
            @Override
            public void addCoreActivities(Brain<WardenEntity> brain) {
                WardenBrainMixin.addCoreActivities(brain);
            }

            @Override
            public void addIdleActivities(Brain<WardenEntity> brain) {
                WardenBrainMixin.addIdleActivities(brain);
            }

            @Override
            public void addRoarActivities(Brain<WardenEntity> brain) {
                WardenBrainMixin.addRoarActivities(brain);
            }

            @Override
            public void addInvestigateActivities(Brain<WardenEntity> brain) {
                WardenBrainMixin.addInvestigateActivities(brain);
            }

            @Override
            public void addSniffActivities(Brain<WardenEntity> brain) {
                WardenBrainMixin.addSniffActivities(brain);
            }

            @Override
            public List<MemoryModuleType<?>> memoryModules() {
                return MEMORY_MODULES;
            }

            @Override
            public List<SensorType<? extends Sensor<? super WardenEntity>>> sensors() {
                return SENSORS;
            }
        };
    }
}

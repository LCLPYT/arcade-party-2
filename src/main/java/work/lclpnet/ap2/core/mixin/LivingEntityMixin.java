package work.lclpnet.ap2.core.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.core.type.ApLivingEntity;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements ApLivingEntity {

    @Unique private float serverSidedScale = 1f;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/LivingEntity;attributes:Lnet/minecraft/entity/attribute/AttributeContainer;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    public void ap2$afterAttributesInitialized(EntityType<?> entityType, World world, CallbackInfo ci) {
        var self = (LivingEntity) (Object) this;

        LivingEntityAttributeInitCallback.HOOK.invoker().onAttributesInitialized(self);
    }

    @Inject(
            method = "getScale",
            at = @At("RETURN"),
            cancellable = true
    )
    public void ap2$modifyServerSidedScale(CallbackInfoReturnable<Float> cir) {
        if (Float.isNaN(serverSidedScale) || !Float.isFinite(serverSidedScale) || Math.abs(serverSidedScale - 1) <= 1e-4) return;

        cir.setReturnValue(serverSidedScale * cir.getReturnValueF());
    }

    @Override
    public void ap2$setServerSidedScale(float scale) {
        serverSidedScale = scale;
    }
}

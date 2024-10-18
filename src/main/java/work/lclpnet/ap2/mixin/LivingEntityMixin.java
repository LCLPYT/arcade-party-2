package work.lclpnet.ap2.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.hook.LivingEntityAttributeInitCallback;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

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
}

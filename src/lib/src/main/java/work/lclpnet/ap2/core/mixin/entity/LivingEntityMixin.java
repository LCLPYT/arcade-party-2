package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.ArmorAbsorbDamageCallback;
import work.lclpnet.ap2.core.hook.DamageKnockbackCallback;
import work.lclpnet.ap2.core.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.core.hook.PowderedSnowSlowCallback;
import work.lclpnet.ap2.core.type.ApLivingEntity;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements ApLivingEntity {

    @Unique private float serverSidedScale = 1f;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;attributes:Lnet/minecraft/world/entity/ai/attributes/AttributeMap;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    public void ap2$afterAttributesInitialized(EntityType<?> type, Level level, CallbackInfo ci) {
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

    @Inject(
            method = "tryAddFrost",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;addTransientModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V"
            ),
            cancellable = true
    )
    public void ap2$addPowderSnowSlow(CallbackInfo ci) {
        var self = (LivingEntity) (Object) this;

        if (PowderedSnowSlowCallback.ADD.invoker().shouldCancel(self)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "removeFrost",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;removeModifier(Lnet/minecraft/resources/Identifier;)Z"
            ),
            cancellable = true
    )
    public void ap2$removePowderSnowSlow(CallbackInfo ci) {
        var self = (LivingEntity) (Object) this;

        if (PowderedSnowSlowCallback.REMOVE.invoker().shouldCancel(self)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "getDamageAfterArmorAbsorb",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtArmor(Lnet/minecraft/world/damagesource/DamageSource;F)V"
            ),
            cancellable = true
    )
    public void ap2$armorAbsorbDamage(DamageSource damageSource, float damage, CallbackInfoReturnable<Float> cir) {
        var self = (LivingEntity) (Object) this;

        if (ArmorAbsorbDamageCallback.HOOK.invoker().mayAbsorbDamage(self, damageSource, damage)) return;

        cir.setReturnValue(damage);
    }

     @Inject(
             method = "dealDefaultKnockback",
             at = @At("HEAD"),
             cancellable = true
     )
    public void ap2$onDealDefaultKnockback(DamageSource source, float damage, boolean blocked, CallbackInfo ci) {
         var self = (LivingEntity) (Object) this;

         if (!DamageKnockbackCallback.HOOK.invoker().shouldTakeKnockback(self, source, damage)) {
             ci.cancel();
         }
     }
}

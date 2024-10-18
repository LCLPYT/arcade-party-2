package work.lclpnet.ap2.hook;

import net.minecraft.entity.LivingEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

/**
 * Called after the {@link net.minecraft.entity.attribute.EntityAttributes} of a {@link LivingEntity} have been initialized.
 * @implNote Attributes are typically initialized when the {@link LivingEntity} is constructed.
 * Keep in mind that any the instance received by this hook is not fully constructed.
 * To be safe, only modify the attributes of the entity with this hook with {@link LivingEntity#getAttributes()}.
 */
public interface LivingEntityAttributeInitCallback {

    Hook<LivingEntityAttributeInitCallback> HOOK = HookFactory.createArrayBacked(LivingEntityAttributeInitCallback.class, hooks -> (living) -> {
        for (var hook : hooks) {
            hook.onAttributesInitialized(living);
        }
    });

    void onAttributesInitialized(LivingEntity living);
}

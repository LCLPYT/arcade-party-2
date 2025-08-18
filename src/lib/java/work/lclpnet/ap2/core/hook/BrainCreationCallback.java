package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.mob.WardenEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.function.Supplier;

public interface BrainCreationCallback<T extends LivingEntity> {

    @Nullable Brain<T> createBrain(T entity, Supplier<Brain<T>> brainGetter);

    interface Warden extends BrainCreationCallback<WardenEntity> {
        Hook<Warden> HOOK = HookFactory.createArrayBacked(Warden.class, hooks -> (entity, handleGetter) -> invoke(hooks, entity, handleGetter));
    }

    interface Creaking extends BrainCreationCallback<CreakingEntity> {
        Hook<Creaking> HOOK = HookFactory.createArrayBacked(Creaking.class, hooks -> (entity, handleGetter) -> invoke(hooks, entity, handleGetter));
    }

    private static <T extends LivingEntity, I extends BrainCreationCallback<T>> @Nullable Brain<T> invoke(I[] hooks, T entity, Supplier<Brain<T>> handleGetter) {
        Brain<T> override = null;

        for (var hook : hooks) {
            var res = hook.createBrain(entity, handleGetter);

            if (res != null) {
                override = res;
            }
        }

        return override;
    }
}

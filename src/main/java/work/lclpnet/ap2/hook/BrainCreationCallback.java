package work.lclpnet.ap2.hook;

import com.mojang.serialization.Dynamic;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.mob.WardenEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.type.BrainHandle;
import work.lclpnet.ap2.type.WardenBrainHandle;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.function.Supplier;

public interface BrainCreationCallback<T extends LivingEntity, H extends BrainHandle<T>> {

    @Nullable
    Brain<T> createBrain(T entity, Dynamic<?> dynamic, Supplier<H> handleGetter);

    interface Warden extends BrainCreationCallback<WardenEntity, WardenBrainHandle> {

        Hook<Warden> HOOK = HookFactory.createArrayBacked(Warden.class, hooks -> (entity, dynamic, handleGetter) -> {
            Brain<WardenEntity> override = null;

            for (var hook : hooks) {
                var res = hook.createBrain(entity, dynamic, handleGetter);

                if (res != null) {
                    override = res;
                }
            }

            return override;
        });
    }
}

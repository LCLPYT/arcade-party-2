package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface AnimalBreedCallback {

    Hook<AnimalBreedCallback> HOOK = HookFactory.createArrayBacked(AnimalBreedCallback.class, hooks -> (breeder, parent, partner, child) -> {
        for (var hook : hooks) {
            hook.onBreed(breeder, parent, partner, child);
        }
    });

    void onBreed(ServerPlayer breeder, Animal parent, Animal partner, AgeableMob child);
}

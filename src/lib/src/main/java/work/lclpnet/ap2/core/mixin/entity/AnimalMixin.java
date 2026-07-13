package work.lclpnet.ap2.core.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.lclpnet.ap2.core.hook.AnimalBreedCallback;

@Mixin(Animal.class)
public abstract class AnimalMixin {

    @Inject(
            method = "finalizeSpawnChildFromBreeding",
            at = @At("HEAD")
    )
    private void ap2$onBreed(ServerLevel level, Animal partner, @Nullable AgeableMob offspring, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;

        ServerPlayer breeder = self.getLoveCause();

        if (breeder == null) {
            breeder = partner.getLoveCause();
        }

        if (breeder == null) return;

        AnimalBreedCallback.HOOK.invoker().onBreed(breeder, self, partner, offspring);
    }
}

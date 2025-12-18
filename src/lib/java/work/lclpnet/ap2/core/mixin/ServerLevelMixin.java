package work.lclpnet.ap2.core.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.api.actor.ActorManager;
import work.lclpnet.ap2.core.hook.EntitySpawnCallback;
import work.lclpnet.ap2.core.type.ActorManagerAccess;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements ActorManagerAccess {

    @Unique
    private final ActorManager actorManager = new ActorManager();

    @Inject(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
            ),
            cancellable = true
    )
    public void ap2$beforeAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var self = (ServerLevel) (Object) this;

        if (EntitySpawnCallback.HOOK.invoker().onSpawn(entity, self)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public @NotNull ActorManager ap2$getActorManager() {
        return actorManager;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V",
                    shift = At.Shift.AFTER
            )
    )
    public void ap2$tickActors(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        actorManager.tick();
    }
}

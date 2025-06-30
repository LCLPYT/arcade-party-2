package work.lclpnet.ap2.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import work.lclpnet.ap2.core.hook.ExplosionAffectedEntitiesCallback;

import java.util.List;

@Mixin(ExplosionImpl.class)
public class ExplosionImplMixin {

    @WrapOperation(
            method = "damageEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"
            )
    )
    private List<Entity> ap2$alterEffectedEntities(ServerWorld instance, Entity entity, Box box, Operation<List<Entity>> original) {
        List<Entity> entities = original.call(instance, entity, box);
        ExplosionImpl self = (ExplosionImpl) (Object) this;

        return ExplosionAffectedEntitiesCallback.HOOK.invoker().overrideAffectedEntities(self, entities);
    }
}

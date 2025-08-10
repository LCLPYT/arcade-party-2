package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.Explosion;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.List;

public interface ExplosionAffectedEntitiesCallback {

    Hook<ExplosionAffectedEntitiesCallback> HOOK = HookFactory.createArrayBacked(ExplosionAffectedEntitiesCallback.class, hooks -> (explosion, affected) -> {
        for (var hook : hooks) {
            affected = hook.overrideAffectedEntities(explosion, affected);
        }

        return affected;
    });

    List<Entity> overrideAffectedEntities(Explosion explosion, List<Entity> affected);
}

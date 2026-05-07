package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface ProjectileHitEntityCallback {

    Hook<ProjectileHitEntityCallback> HOOK = HookFactory.createArrayBacked(ProjectileHitEntityCallback.class, hooks -> (projectile, hit) -> {
        for (var hook : hooks) {
            hook.onHitEntity(projectile, hit);
        }
    });

    void onHitEntity(Projectile projectile, EntityHitResult hit);
}

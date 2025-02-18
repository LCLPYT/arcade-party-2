package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

// subject to be moved to kibu
public interface ProjectileShootCallback {

    Hook<ProjectileShootCallback> HOOK = HookFactory.createArrayBacked(ProjectileShootCallback.class, hooks -> (shooter, projectile) -> {
        for (ProjectileShootCallback hook : hooks) {
            hook.onShoot(shooter, projectile);
        }
    });

    void onShoot(LivingEntity shooter, ProjectileEntity projectile);
}

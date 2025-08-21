package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.thread.ThreadExecutor;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.util.ThreadUtil;

public interface MountContext {

    ServerWorld world();

    <T extends Entity> Resolvable<@Nullable T> spawn(@Nullable T entity, Object3d origin);

    <T extends Entity> void remove(@Nullable T entity, Object3d origin);

    /**
     * Checks whether currently running on the given {@link ThreadExecutor} thread, in which case false is returned.
     * Otherwise, the given action is dispatched to the given {@link ThreadExecutor}, in which case true is returned and the caller should prevent further execution.
     * @param runnable The (reference to an) action to be executed.
     * @return True, if currently running off-thread and whether the runnable was thereby dispatched to the given {@link ThreadExecutor}, false if running of thread and nothing was dispatched.
     */
    default boolean onThreadOrDispatch(Runnable runnable) {
        return ThreadUtil.onThreadOrDispatch(world().getServer(), runnable);
    }
}

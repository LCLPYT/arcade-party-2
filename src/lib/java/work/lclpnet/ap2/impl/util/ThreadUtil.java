package work.lclpnet.ap2.impl.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.ThreadExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public class ThreadUtil {

    private ThreadUtil() {}

    public static <T> Function<T, CompletionStage<Void>> onThread(MinecraftServer server, Consumer<T> action) {
        return res -> server.submit(() -> action.accept(res));
    }

    public static <T, U> Function<T, CompletionStage<U>> onThread(MinecraftServer server, Function<T, U> action) {
        return res -> server.submit(() -> action.apply(res));
    }

    public static void executeOn(ThreadExecutor<?> executor, Runnable runnable) {
        if (executor.isOnThread()) {
            runnable.run();
        } else {
            executor.execute(runnable);
        }
    }

    public static <T extends Thread & Executor> void executeOn(T executor, Runnable runnable) {
        if (Thread.currentThread() == executor) {
            runnable.run();
        } else {
            executor.execute(runnable);
        }
    }

    public static <T extends Thread & Executor> CompletableFuture<Void> submitOn(T executor, Runnable runnable) {
        if (Thread.currentThread() == executor) {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(runnable, executor);
        }
    }

    public static void forceThread(ThreadExecutor<?> executor) {
        if (executor.isOnThread()) return;

        throw new IllegalStateException("Called on the wrong thread. Expected to run on " + executor);
    }

    public static void forceThread(Thread thread) {
        if (Thread.currentThread() == thread) return;

        throw new IllegalStateException("Called on the wrong thread. Expected to run on " + thread.getName());
    }

    /**
     * Checks whether currently running on the given {@link ThreadExecutor} thread, in which case false is returned.
     * Otherwise, the given action is dispatched to the given {@link ThreadExecutor}, in which case true is returned and the caller should prevent further execution.
     * @param executor The {@link ThreadExecutor} to check and / or dispatch on.
     * @param runnable The (reference to an) action to be executed.
     * @return True, if currently running off-thread and whether the runnable was thereby dispatched to the given {@link ThreadExecutor}, false if running of thread and nothing was dispatched.
     */
    public static boolean onThreadOrDispatch(ThreadExecutor<?> executor, Runnable runnable) {
        if (executor.isOnThread()) return false;

        executor.execute(runnable);
        return true;
    }
}

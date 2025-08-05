package work.lclpnet.ap2.impl.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.ThreadExecutor;

import java.util.concurrent.CompletionStage;
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

    public static void forceThread(ThreadExecutor<?> executor) {
        if (executor.isOnThread()) return;

        throw new IllegalStateException("Called on the wrong thread. Expected to run on " + executor);
    }
}

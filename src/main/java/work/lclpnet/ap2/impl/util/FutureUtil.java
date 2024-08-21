package work.lclpnet.ap2.impl.util;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

public class FutureUtil {

    private FutureUtil() {}

    public static <T> Function<T, CompletionStage<Void>> onThread(MinecraftServer server, Consumer<T> action) {
        return res -> server.submit(() -> action.accept(res));
    }
}

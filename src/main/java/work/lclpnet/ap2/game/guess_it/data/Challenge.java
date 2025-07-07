package work.lclpnet.ap2.game.guess_it.data;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public interface Challenge {

    String id();

    String getPreparationKey();

    int getDurationTicks();

    void begin(InputInterface input, ChallengeMessenger messenger);

    void evaluate(PlayerChoices choices, ChallengeResult result);

    default void destroy() {}

    default void prepare() {}

    default boolean shouldPlayBeginSound() {
        return true;
    }

    default void init(@Nullable Object init) {}

    default void provideInitCommand(LiteralArgumentBuilder<ServerCommandSource> node, Initializer init) {}

    interface Initializer {
        void accept(CommandContext<ServerCommandSource> ctx, Object config);
    }
}

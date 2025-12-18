package work.lclpnet.ap2.api.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface EliminationController {

    void eliminateAll(Iterable<? extends ServerPlayer> players);

    void eliminate(ServerPlayer player, @Nullable DamageSource source, @Nullable TranslatedText customMsg);

    default void eliminate(ServerPlayer player) {
        eliminate(player, null, null);
    }

    default void eliminate(ServerPlayer player, @Nullable DamageSource source) {
        eliminate(player, source, null);
    }

    default void eliminate(ServerPlayer player, @Nullable TranslatedText customMsg) {
        eliminate(player, null, customMsg);
    }
}

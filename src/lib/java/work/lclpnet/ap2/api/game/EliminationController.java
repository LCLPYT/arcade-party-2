package work.lclpnet.ap2.api.game;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface EliminationController {

    void eliminateAll(Iterable<? extends ServerPlayerEntity> players);

    void eliminate(ServerPlayerEntity player, @Nullable DamageSource source, @Nullable TranslatedText customMsg);

    default void eliminate(ServerPlayerEntity player) {
        eliminate(player, null, null);
    }

    default void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
        eliminate(player, source, null);
    }

    default void eliminate(ServerPlayerEntity player, @Nullable TranslatedText customMsg) {
        eliminate(player, null, customMsg);
    }
}

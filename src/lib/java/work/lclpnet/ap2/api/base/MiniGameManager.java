package work.lclpnet.ap2.api.base;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import work.lclpnet.ap2.api.game.MiniGame;

import java.util.Optional;
import java.util.Set;

public interface MiniGameManager {

    Set<MiniGame> getGames();

    Optional<MiniGame> getGame(ResourceLocation gameId);

    Codec<MiniGame> getGameCodec();
}

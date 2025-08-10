package work.lclpnet.ap2.api.base;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGame;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MiniGameManager {

    Set<MiniGame> getGames();

    Optional<MiniGame> getGame(Identifier gameId);

    static Set<MiniGame> getAllGames() {
        List<MiniGame> miniGames = FabricLoader.getInstance().getEntrypoints("ap2:minigame", MiniGame.class);

        return new LinkedHashSet<>(miniGames);
    }
}

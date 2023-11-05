package work.lclpnet.ap2.base.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.kibu.inv.type.RestrictedInventory;

import java.util.List;
import java.util.WeakHashMap;

public class DevGameChooser {

    private final WeakHashMap<Inventory, GameChooserInventory> inventories = new WeakHashMap<>();

    @Nullable
    public MiniGame getGame(Inventory inv, int slot) {
        GameChooserInventory chooser = inventories.get(inv);

        if (chooser == null) return null;

        return chooser.getGame(slot);
    }

    public void registerInventory(RestrictedInventory inv, List<MiniGame> games) {
        inventories.put(inv, new GameChooserInventory(games));
    }

    public static class GameChooserInventory {
        private final Int2ObjectMap<MiniGame> games;

        private GameChooserInventory(List<MiniGame> games) {
            this.games = new Int2ObjectArrayMap<>(games.size());

            for (int i = 0, gamesSize = games.size(); i < gamesSize; i++) {
                MiniGame game = games.get(i);
                this.games.put(i, game);
            }
        }

        @Nullable
        public MiniGame getGame(int i) {
            return games.get(i);
        }
    }
}

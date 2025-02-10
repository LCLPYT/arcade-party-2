package work.lclpnet.ap2.impl.game.item;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.impl.ds.WeightedList;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Random;
import java.util.function.Consumer;

public class SpecialItems {

    private final GameMap map;
    private final SpecialItemPositions positions;
    private final SpecialItemRegistry registry;
    private WeightedList<SpecialItem> weightedItems = WeightedList.empty();

    public SpecialItems(GameMap map, SpecialItemPositions positions, SpecialItemRegistry registry) {
        this.map = map;
        this.positions = positions;
        this.registry = registry;
    }

    public void init() {
        JSONObject cfg = map.requireProperty("items");

        JSONObject overrides = cfg.optJSONObject("overrides");

        weightedItems = registry.weightedItems(overrides != null ? overrides : new JSONObject());

        JSONObject areaJson = cfg.getJSONObject("spawn-area");
        BlockPos mapSpawn = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));

        positions.init(areaJson, mapSpawn);
    }

    public void setup() {
        positions.update();
    }

    public static SpecialItems create(GameMap map, ServerWorld world, Random random, Consumer<SpecialItemRegistrar> config) {
        return create(map, world, new WalkableBlockPredicate(world), random, config);
    }

    public static SpecialItems create(GameMap map, ServerWorld world, BlockPredicate validSpawn, Random random, Consumer<SpecialItemRegistrar> config) {
        var debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);
        }

        var positions = new SpecialItemPositions(validSpawn, random, debugController);
        var registry = new SpecialItemRegistry();

        config.accept(registry);

        var specialItems = new SpecialItems(map, positions, registry);

        specialItems.init();

        return specialItems;
    }
}

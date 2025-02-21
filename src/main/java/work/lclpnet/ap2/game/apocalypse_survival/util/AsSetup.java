package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.server.world.ServerWorld;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class AsSetup {

    private final GameMap map;
    private final ServerWorld world;
    private final Random random;
    private final TargetManager targetManager;

    public AsSetup(GameMap map, ServerWorld world, Random random, TargetManager targetManager) {
        this.map = map;
        this.world = world;
        this.random = random;
        this.targetManager = targetManager;
    }

    public List<MonsterSpawner<?>> readSpawners() {
        List<MonsterSpawner<?>> spawners = new LinkedList<>();

        JSONArray array = map.requireProperty("spawners");

        for (Object o : array) {
            if (!(o instanceof JSONObject json)) continue;

            spawners.add(createSpawner(json));
        }

        return spawners;
    }

    private MonsterSpawner<?> createSpawner(JSONObject json) {
        JSONObject stageJson = json.getJSONObject("stage");

        BlockShape blockShape = MapUtil.readShape(stageJson);
        var stageWithRadius = validateStage(blockShape);

        return new MonsterSpawner<>(world, stageWithRadius, random, targetManager);
    }

    @SuppressWarnings("unchecked")
    private <S extends BlockShape & BlockShape.WithRadius> S validateStage(BlockShape blockShape) {
        if (!(blockShape instanceof BlockShape.WithRadius)) throw new IllegalArgumentException("Stage with radius required");
        return (S) blockShape;
    }
}

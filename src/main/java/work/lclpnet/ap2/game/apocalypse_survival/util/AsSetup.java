package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.server.world.ServerWorld;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.ap2.impl.util.world.stage.StageReader;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class AsSetup {

    private final GameMap map;
    private final ServerWorld world;
    private final Random random;

    public AsSetup(GameMap map, ServerWorld world, Random random) {
        this.map = map;
        this.world = world;
        this.random = random;
    }

    public List<MonsterSpawner> readSpawners() {
        List<MonsterSpawner> spawners = new LinkedList<>();

        JSONArray array = map.requireProperty("spawners");

        for (Object o : array) {
            if (!(o instanceof JSONObject json)) continue;

            spawners.add(createSpawner(json));
        }

        return spawners;
    }

    private MonsterSpawner createSpawner(JSONObject json) {
        JSONObject stageJson = json.getJSONObject("stage");

        Stage stage = StageReader.readStage(stageJson);

        return new MonsterSpawner(world, stage, random);
    }
}

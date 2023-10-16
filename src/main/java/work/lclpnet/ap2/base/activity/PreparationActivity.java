package work.lclpnet.ap2.base.activity;

import work.lclpnet.activity.Activity;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.util.WorldContainer;

import java.util.Optional;

public class PreparationActivity implements Activity {

    private final MapManager mapManager;
    private final WorldContainer worldContainer;

    public PreparationActivity(MapManager mapManager, WorldContainer worldContainer) {
        this.mapManager = mapManager;
        this.worldContainer = worldContainer;
    }

    @Override
    public void start() {
        GameMap map = mapManager.getMapCollection()
                .getMap(ArcadeParty.identifier("preparation"))
                .orElseThrow();

        System.out.println(map);

//        mapManager.pull(map, )
    }

    @Override
    public void stop() {

    }
}

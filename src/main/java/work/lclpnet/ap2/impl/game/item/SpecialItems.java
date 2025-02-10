package work.lclpnet.ap2.impl.game.item;

import work.lclpnet.lobby.game.map.GameMap;

public class SpecialItems {

    private final GameMap map;
    private final SpecialItemPositions positions;

    public SpecialItems(GameMap map, SpecialItemPositions positions) {
        this.map = map;
        this.positions = positions;
    }

    public void init() {
        positions.init();
        positions.update();
    }
}

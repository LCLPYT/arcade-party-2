package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.lobby.game.map.GameMap;

public class MSGenerator {

    private final ServerWorld world;
    private final GameMap map;
    private final MSLoader.Result loaded;

    public MSGenerator(ServerWorld world, GameMap map, MSLoader.Result loaded) {
        this.world = world;
        this.map = map;
        this.loaded = loaded;
    }

    public void generate() {
        debugPieces();
    }

    private void debugPieces() {
        var pos = new BlockPos.Mutable(0, 64, 0);

        for (StructurePiece piece : loaded.pieces()) {
            BlockStructure struct = piece.wrapper().getStructure();
            BlockBox bounds = StructureUtil.getBounds(struct);

            StructureUtil.placeStructureFast(struct, world, pos);

            pos.setX(pos.getX() + bounds.width() + 5);
        }
    }
}

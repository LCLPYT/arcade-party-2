package work.lclpnet.ap2.game.maze_scape.setup;

import work.lclpnet.ap2.game.maze_scape.gen.Piece;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;

import java.util.List;

public record StructurePiece(
        FabricStructureWrapper wrapper,
        BVH bounds,
        List<Connector3> connectors,
        float weight,
        int maxCount
) implements Piece<Connector3> {

    public boolean limitedCount() {
        return maxCount != -1;
    }
}

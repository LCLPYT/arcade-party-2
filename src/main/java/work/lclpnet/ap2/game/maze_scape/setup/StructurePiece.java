package work.lclpnet.ap2.game.maze_scape.setup;

import work.lclpnet.ap2.game.maze_scape.gen.Piece;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;

import java.util.List;
import java.util.Set;

public record StructurePiece(
        FabricStructureWrapper wrapper,
        BVH bounds,
        List<Connector3> connectors,
        float weight,
        int maxCount,
        boolean connectSame,
        Set<ClusterDef> clusters,
        int minDistance,
        boolean updateBlocks
) implements Piece<Connector3> {

    public boolean limitedCount() {
        return maxCount != -1;
    }

    public boolean deadEnd() {
        return connectors.size() == 1;
    }
}

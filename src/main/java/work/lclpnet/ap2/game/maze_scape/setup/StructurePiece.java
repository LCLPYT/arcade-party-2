package work.lclpnet.ap2.game.maze_scape.setup;

import work.lclpnet.ap2.game.maze_scape.gen.Piece;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;

public record StructurePiece(
        BlockStructure structure,
        BVH bounds,
        List<Connector3d> connectors,
        float weight,
        int maxCount
) implements Piece<Connector3d> {

}

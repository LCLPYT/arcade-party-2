package work.lclpnet.ap2.game.maze_scape.setup;

import java.util.HashSet;
import java.util.Set;

public class Cluster {

    private final ClusterDef definition;
    private final int targetPieceCount;
    private final Set<OrientedStructurePiece> pieces;

    public Cluster(ClusterDef definition, int targetPieceCount) {
        this.definition = definition;
        this.targetPieceCount = targetPieceCount;
        this.pieces = new HashSet<>(targetPieceCount);
    }

    public boolean complete() {
        return pieces.size() >= targetPieceCount;
    }

    public void add(OrientedStructurePiece piece) {
        pieces.add(piece);
    }

    public void remove(OrientedStructurePiece piece) {
        pieces.remove(piece);
    }

    public ClusterDef definition() {
        return definition;
    }
}

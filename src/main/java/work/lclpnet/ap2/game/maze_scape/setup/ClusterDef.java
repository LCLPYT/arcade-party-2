package work.lclpnet.ap2.game.maze_scape.setup;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

public record ClusterDef(float chance, int minPieces, int maxPieces, Set<StructurePiece> pieces) {

    public ClusterDef(float chance, int minPieces, int maxPieces) {
        this(chance, minPieces, maxPieces, new HashSet<>());
    }

    public ClusterDef {
        minPieces = Math.max(minPieces, 1);
        maxPieces = Math.max(minPieces, maxPieces);
    }

    @Nullable
    public static ClusterDef fromJson(JSONObject json, Logger logger) {
        Number chance = json.optNumber("chance");

        if (chance == null) {
            logger.warn("Cluster definition is missing the 'chance' float property");
            return null;
        }

        Number minPieces = json.optNumber("min-pieces");

        if (minPieces == null) {
            logger.warn("Cluster definition is missing the 'min-pieces' int property");
            return null;
        }

        Number maxPieces = json.optNumber("max-pieces");

        if (maxPieces == null) {
            logger.warn("Cluster definition is missing the 'max-pieces' int property");
            return null;
        }

        return new ClusterDef(chance.floatValue(), minPieces.intValue(), maxPieces.intValue());
    }
}

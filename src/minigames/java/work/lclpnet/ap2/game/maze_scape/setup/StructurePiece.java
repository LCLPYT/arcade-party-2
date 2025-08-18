package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Piece;
import work.lclpnet.ap2.impl.ds.BVH;
import work.lclpnet.ap2.impl.ds.StructureMask;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StructurePiece(
        String name,
        FabricStructureWrapper wrapper,
        BVH bounds,
        List<Connector3> connectors,
        float weight,
        int maxCount,
        boolean connectSame,
        Set<ClusterDef> clusters,
        int minDistance,
        boolean updateBlocks,
        boolean noUnstuck,
        List<BlockBox> extraGeneratorBounds,
        @Nullable Vec3d spawn,
        List<BlockPos> jigsaws,
        StructureMask pit
) implements Piece<Connector3> {

    public boolean limitedCount() {
        return maxCount != -1;
    }

    public boolean deadEnd() {
        return connectors.size() == 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StructurePiece piece = (StructurePiece) o;
        return Objects.equals(name, piece.name) && Objects.equals(wrapper, piece.wrapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, wrapper);
    }
}

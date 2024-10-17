package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.enums.Orientation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.OrientedPiece;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OrientedStructurePiece implements OrientedPiece<Connector3, StructurePiece> {

    private final StructurePiece piece;
    private final BlockPos pos;
    private final int rotation;
    private final List<Connector3> connectors;
    private final Matrix3i mat;
    private final BVH bounds;
    private final @Nullable Vec3d spawn;
    @Nullable private volatile Matrix3i invMat = null;
    @Nullable private Cluster cluster = null;

    public OrientedStructurePiece(StructurePiece piece, BlockPos pos, int rotation, int parentConnector, @Nullable BVH bounds) {
        this.piece = piece;
        this.pos = pos;
        this.rotation = rotation;

        this.mat = Matrix3i.makeRotationY(rotation);
        this.bounds = bounds != null ? bounds : piece.bounds().transform(new AffineIntMatrix(mat, pos));

        // rotate and translate base connectors
        var base = piece().connectors();
        List<Connector3> connectors = new ArrayList<>(parentConnector == -1 ? base.size() : base.size() - 1);

        for (int i = 0, baseSize = base.size(); i < baseSize; i++) {
            if (i == parentConnector) continue;

            Connector3 connector = base.get(i);

            // find connector position
            BlockPos connectorPos = mat.transform(connector.pos());
            var newConnectorPos = pos.add(connectorPos);

            // transform facing vector
            Vec3i vec = mat.transform(connector.orientation().getFacing().getVector());
            Direction dir = Direction.fromVector(vec.getX(), vec.getY(), vec.getZ());

            if (dir == null) {
                throw new IllegalArgumentException("Invalid transformation: Direction is not canonical");
            }

            var newOrientation = Orientation.byDirections(dir, connector.orientation().getRotation());

            if (newOrientation == null) {
                throw new IllegalArgumentException("Invalid transformation: Invalid orientation");
            }

            connectors.add(new Connector3(newConnectorPos, newOrientation, connector.name(), connector.target()));
        }

        this.connectors = connectors;

        Vec3d spawn = piece.spawn();

        if (spawn != null) {
            spawn = mat.transform(spawn).add(pos.getX(), pos.getY(), pos.getZ());
        }

        this.spawn = spawn;
    }

    @Override
    public StructurePiece piece() {
        return piece;
    }

    @Override
    public List<Connector3> connectors() {
        return connectors;
    }

    public BVH bounds() {
        return bounds;
    }

    public BlockPos pos() {
        return pos;
    }

    public Matrix3i transformation() {
        return mat;
    }

    public Matrix3i inverseTransformation() {
        if (invMat != null) return invMat;

        synchronized (this) {
            if (invMat == null) {
                invMat = mat.invert();
            }
        }

        return invMat;
    }

    public void setCluster(@Nullable Cluster cluster) {
        this.cluster = cluster;
    }

    public @Nullable Cluster cluster() {
        return cluster;
    }

    @Nullable
    public Vec3d spawn() {
        return spawn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrientedStructurePiece that = (OrientedStructurePiece) o;
        return rotation == that.rotation && Objects.equals(piece, that.piece) && Objects.equals(pos, that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(piece, pos, rotation);
    }

    @Override
    public String toString() {
        return "OrientedStructurePiece{pos=%s, rotation=%d}".formatted(pos, rotation);
    }
}

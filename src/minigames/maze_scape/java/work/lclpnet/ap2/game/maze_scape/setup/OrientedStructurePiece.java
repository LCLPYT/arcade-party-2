package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.gen.OrientedPiece;
import work.lclpnet.gaco.ds.BVH;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OrientedStructurePiece implements OrientedPiece<Connector3, StructurePiece, OrientedStructurePiece> {

    private final StructurePiece piece;
    private final BlockPos pos;
    private final int rotation;
    private final List<Connector3> connectors;
    private final Matrix3i mat;
    private final BVH bounds;
    private final List<BlockBox> extraGeneratorBounds;
    private final @Nullable Vec3 spawn;
    private final @Nullable Connector3 parentConnector;
    @Nullable private volatile Matrix3i invMat = null;
    @Nullable private Cluster cluster = null;
    @Nullable private Node<Connector3, StructurePiece, OrientedStructurePiece> node = null;

    public OrientedStructurePiece(StructurePiece piece, BlockPos pos, int rotation, int parentConnectorIndex, @Nullable BVH bounds) {
        this.piece = piece;
        this.pos = pos;
        this.rotation = rotation;

        this.mat = Matrix3i.makeRotationY(rotation);

        AffineIntMatrix affineMatrix = new AffineIntMatrix(mat, pos);
        this.bounds = bounds != null ? bounds : piece.bounds().transform(affineMatrix);

        // rotate and translate base connectors
        var base = piece().connectors();
        List<Connector3> connectors = new ArrayList<>(parentConnectorIndex == -1 ? base.size() : base.size() - 1);

        Connector3 parentConnector = null;

        for (int i = 0, baseSize = base.size(); i < baseSize; i++) {
            Connector3 connector = base.get(i);

            // find connector position
            BlockPos connectorPos = mat.transform(connector.pos());
            var newConnectorPos = pos.offset(connectorPos);

            // transform facing vector
            Vec3i vec = mat.transform(connector.orientation().front().getUnitVec3i());
            Direction dir = Direction.getNearest(vec.getX(), vec.getY(), vec.getZ(), null);

            if (dir == null) {
                throw new IllegalArgumentException("Invalid transformation: Direction is not canonical");
            }

            var newOrientation = FrontAndTop.fromFrontAndTop(dir, connector.orientation().top());

            if (newOrientation == null) {
                throw new IllegalArgumentException("Invalid transformation: Invalid orientation");
            }

            Connector3 transformed = new Connector3(newConnectorPos, newOrientation, connector.name(), connector.target());

            if (i == parentConnectorIndex) {
                parentConnector = transformed;
            } else {
                connectors.add(transformed);
            }
        }

        this.connectors = connectors;
        this.parentConnector = parentConnector;

        Vec3 spawn = piece.spawn();

        if (spawn != null) {
            spawn = mat.transform(spawn.x() - 0.5, spawn.y() - 0.5, spawn.z() - 0.5)
                    .add(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        this.spawn = spawn;

        this.extraGeneratorBounds = piece.extraGeneratorBounds().stream()
                .map(box -> box.transform(affineMatrix))
                .toList();
    }

    @Override
    public StructurePiece piece() {
        return piece;
    }

    @Override
    public List<Connector3> connectors() {
        return connectors;
    }

    public @Nullable Connector3 parentConnector() {
        return parentConnector;
    }

    public BVH bounds() {
        return bounds;
    }

    public boolean intersectsGeneratorBounds(BVH bvh) {
        if (bvh.intersects(bounds)) {
            return true;
        }

        for (BlockBox box : extraGeneratorBounds) {
            if (bvh.intersects(box)) {
                return true;
            }
        }

        return false;
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

    public int rotation() {
        return rotation;
    }

    public void setCluster(@Nullable Cluster cluster) {
        this.cluster = cluster;
    }

    public @Nullable Cluster cluster() {
        return cluster;
    }

    @Nullable
    public Vec3 spawn() {
        return spawn;
    }

    @Override
    @Nullable
    public Node<Connector3, StructurePiece, OrientedStructurePiece> node() {
        return node;
    }

    @Override
    public void setNode(@Nullable Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
        this.node = node;
    }

    public boolean isPitAt(Vec3i pos) {
        return isPitAt(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isPitAt(int x, int y, int z) {
        // could be optimized by querying a transformed precomputed pit structure mask
        var local = inverseTransformation().transform(x - pos.getX(), y - pos.getY(), z - pos.getZ());

        return piece.pit().isVoxelAt(local.getX(), local.getY(), local.getZ());
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

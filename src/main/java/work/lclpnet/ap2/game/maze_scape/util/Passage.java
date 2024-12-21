package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Passage implements
        UndirectedGraphNode<Passage>,
        Pair<@NotNull Node<Connector3, StructurePiece, OrientedStructurePiece>, @NotNull Node<Connector3, StructurePiece, OrientedStructurePiece>> {

    private final List<Passage> neighbours = new ArrayList<>();
    private final BlockPos pos;
    private final Node<Connector3, StructurePiece, OrientedStructurePiece> left, right;

    public Passage(BlockPos pos,
                   @NotNull Node<Connector3, StructurePiece, OrientedStructurePiece> left,
                   @NotNull Node<Connector3, StructurePiece, OrientedStructurePiece> right) {
        this.pos = pos;
        this.left = left;
        this.right = right;
    }

    @Override
    public @NotNull List<Passage> neighbours() {
        return neighbours;
    }

    @Override
    public @NotNull Node<Connector3, StructurePiece, OrientedStructurePiece> left() {
        return left;
    }

    @Override
    public @NotNull Node<Connector3, StructurePiece, OrientedStructurePiece> right() {
        return right;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passage passage = (Passage) o;
        return Objects.equals(pos, passage.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pos);
    }

    @Override
    public String toString() {
        return "Passage{pos=%s}".formatted(pos);
    }

    public double estimateDistance(Passage other) {
        return MathUtil.manhattanDistance(this.pos, other.pos);
    }

    @Nullable
    public Node<Connector3, StructurePiece, OrientedStructurePiece> commonNode(Passage other) {
        if (this.left == other.left || this.left == other.right) {
            return left;
        }

        if (this.right == other.left || this.right == other.right) {
            return right;
        }

        return null;
    }
}

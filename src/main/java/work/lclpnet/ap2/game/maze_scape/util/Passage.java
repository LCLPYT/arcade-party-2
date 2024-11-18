package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Passage implements UndirectedGraphNode<Passage> {

    private final List<Passage> neighbours = new ArrayList<>();
    private final BlockPos pos;

    public Passage(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public @NotNull List<Passage> neighbours() {
        return neighbours;
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
}

package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DirectedGraphNode<T extends DirectedGraphNode<T>> {

    @NotNull
    List<T> children();
}

package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface UndirectedGraphNode<T extends UndirectedGraphNode<T>> {

    @NotNull
    List<T> neighbours();
}

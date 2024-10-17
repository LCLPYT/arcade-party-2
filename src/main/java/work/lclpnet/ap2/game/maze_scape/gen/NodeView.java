package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface NodeView<T extends NodeView<T>> {

    int level();

    @Nullable List<@Nullable T> children();
}

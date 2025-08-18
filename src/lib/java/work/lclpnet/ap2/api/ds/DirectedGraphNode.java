package work.lclpnet.ap2.api.ds;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DirectedGraphNode<T extends DirectedGraphNode<T>> {

    @NotNull
    List<T> children();
}

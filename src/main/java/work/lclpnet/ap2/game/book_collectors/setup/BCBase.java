package work.lclpnet.ap2.game.book_collectors.setup;

import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.UnionCollider;

import java.util.List;

public record BCBase(Collider bounds) {

    public BCBase(List<BlockBox> bounds) {
        this(new UnionCollider(bounds.toArray(BlockBox[]::new)));
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.collidesWith(x, y, z);
    }
}

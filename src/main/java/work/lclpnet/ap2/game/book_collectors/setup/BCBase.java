package work.lclpnet.ap2.game.book_collectors.setup;

import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.collision.UnionCollider;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;

import java.util.List;

public record BCBase(Collider bounds) {

    public BCBase(List<BlockShape> bounds) {
        this(new UnionCollider(bounds.toArray(BlockShape[]::new)));
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.collidesWith(x, y, z);
    }
}

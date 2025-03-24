package work.lclpnet.ap2.game.book_collectors.setup;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.collision.UnionCollider;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;

public record BCBase(
        Collider bounds,
        @Nullable BlockStructure doorSchematic,
        @Nullable BlockPos doorPos
) {

    public BCBase(List<BlockShape> bounds, @Nullable BlockStructure doorSchematic, @Nullable BlockPos doorPos) {
        this(new UnionCollider(bounds.toArray(BlockShape[]::new)), doorSchematic, doorPos);
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.collidesWith(x, y, z);
    }
}

package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Iterator;

public class BoxBlockShape implements BlockShape {

    public static final String TYPE_CUBE = "cube";

    private final BlockBox box;
    private final BlockPos center, origin;

    public BoxBlockShape(BlockBox box) {
        this.box = box;
        this.center = BlockPos.ofFloored(box.getCenter());
        this.origin = center.withY(box.min().getY());
    }

    public BlockBox box() {
        return box;
    }

    @Override
    public BlockPos origin() {
        return origin;
    }

    @Override
    public BlockPos center() {
        return center;
    }

    @Override
    public boolean contains(BlockPos pos) {
        return box.contains(pos);
    }

    @Override
    public BlockBox bounds() {
        return box;
    }

    @Override
    public @NotNull Iterator<BlockPos> iterator() {
        return box.iterator();
    }
}

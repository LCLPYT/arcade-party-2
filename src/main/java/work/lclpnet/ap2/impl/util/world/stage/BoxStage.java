package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Iterator;

public class BoxStage implements Stage {

    private final BlockBox box;
    private final BlockPos center, origin;

    public BoxStage(BlockBox box) {
        this.box = box;
        this.center = BlockPos.ofFloored(box.getCenter());
        this.origin = center.withY(box.min().getY());
    }

    public BlockBox box() {
        return box;
    }

    @Override
    public BlockPos getOrigin() {
        return origin;
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }

    @Override
    public boolean contains(BlockPos pos) {
        return box.contains(pos);
    }

    @Override
    public @NotNull Iterator<BlockPos> iterator() {
        return box.iterator();
    }
}

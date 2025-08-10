package work.lclpnet.ap2.impl.util.world.block_shape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Iterator;
import java.util.Optional;

public class BoxBlockShape implements BlockShape {

    public static final String TYPE_CUBE = "cube", TYPE_BOX = "box";

    public static final MapCodec<BoxBlockShape> BOX_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockBox.CODEC.fieldOf("bounds").forGetter(BoxBlockShape::bounds)
    ).apply(instance, BoxBlockShape::new));

    public static final MapCodec<BoxBlockShape> CUBE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Vec3i.CODEC.optionalFieldOf("origin").forGetter(shape -> shape.box.isCube() ? Optional.of(shape.center) : Optional.empty()),
            Codec.INT.optionalFieldOf("radius").forGetter(shape -> shape.box.isCube() ? Optional.of(shape.box.width() / 2) : Optional.empty())
    ).apply(instance, (radius, origin) -> new BoxBlockShape(BlockBox.ofRadius(
            radius.orElseThrow(() -> new IllegalStateException("Not a cube")),
            origin.orElseThrow(() -> new IllegalStateException("Not a cube"))
    ))));

    private final BlockBox box;
    private final BlockPos center, origin;

    public BoxBlockShape(BlockBox box) {
        this.box = box;
        this.center = BlockPos.ofFloored(box.getCenter());
        this.origin = center.withY(box.min().getY());
    }

    @Override
    public BlockShapes.Type<?> type() {
        return box.isCube() ? BlockShapes.TYPE_CUBE : BlockShapes.TYPE_BOX;
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
    public boolean contains(double x, double y, double z) {
        return box.contains(x, y, z);
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

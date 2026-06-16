package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.SpaceFinder;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class SizedSpaceFinder implements SpaceFinder {

    private final BlockGetter blockView;
    private final float halfWidth, height, halfLength;

    public SizedSpaceFinder(BlockGetter blockView, float width, float height, float length) {
        this.blockView = blockView;
        this.halfWidth = width * 0.5f;
        this.height = height;
        this.halfLength = length * 0.5f;
    }

    @Override
    public List<Vec3> findSpaces(Iterator<BlockPos> positions) {
        var spliterator = Spliterators.spliteratorUnknownSize(positions, 0);

        return StreamSupport.stream(spliterator, false)
                .map(this::spaceAt)
                .filter(Objects::nonNull)
                .toList();
    }

    @Nullable
    private Vec3 spaceAt(BlockPos pos) {
        final double minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        final double maxX = minX + 1, maxY = minY + 1, maxZ = minZ + 1;

        // prefer the block center, then fall back to the edges
        double[] xs = {minX + 0.5, minX, maxX};
        double[] zs = {minZ + 0.5, minZ, maxZ};

        for (double x : xs)
            for (double z : zs)
                for (double y = minY; y <= maxY; y += 0.5)
                    if (hasSpace(x, y, z)) return new Vec3(x, y, z);

        return null;
    }

    private boolean hasSpace(double x, double y, double z) {
        double minX = x - halfWidth, minZ = z - halfLength;
        double maxX = x + halfWidth, maxY = y + height, maxZ = z + halfLength;

        VoxelShape space = Shapes.create(minX, y, minZ, maxX, maxY, maxZ);

        return BlockPos.betweenClosedStream(
                (int) Math.floor(minX),
                (int) Math.floor(y),
                (int) Math.floor(minZ),
                (int) Math.ceil(maxX),
                (int) Math.ceil(maxY),
                (int) Math.ceil(maxZ)
        ).noneMatch(pos -> {
            VoxelShape shape = blockView.getBlockState(pos).getCollisionShape(blockView, pos)
                    .move(pos.getX(), pos.getY(), pos.getZ());

            return Shapes.joinIsNotEmpty(shape, space, BooleanOp.AND);
        });
    }

    public static SizedSpaceFinder create(BlockGetter blockView, EntityType<?> entityType) {
        EntityDimensions dimensions = entityType.getDimensions();

        int width = (int) Math.ceil(dimensions.width());
        int height = (int) Math.ceil(dimensions.height());

        return new SizedSpaceFinder(blockView, width, height, width);
    }
}

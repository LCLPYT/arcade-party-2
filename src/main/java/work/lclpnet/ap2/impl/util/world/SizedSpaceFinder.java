package work.lclpnet.ap2.impl.util.world;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.SpaceFinder;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class SizedSpaceFinder implements SpaceFinder {

    private final BlockView blockView;
    private final float halfWidth, height, halfLength;

    public SizedSpaceFinder(BlockView blockView, float width, float height, float length) {
        this.blockView = blockView;
        this.halfWidth = width * 0.5f;
        this.height = height;
        this.halfLength = length * 0.5f;
    }

    @Override
    public List<Vec3d> findSpaces(Iterator<BlockPos> positions) {
        var spliterator = Spliterators.spliteratorUnknownSize(positions, 0);

        return StreamSupport.stream(spliterator, false)
                .map(this::spaceAt)
                .filter(Objects::nonNull)
                .toList();
    }

    @Nullable
    private Vec3d spaceAt(BlockPos pos) {
        final double minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        final double maxX = minX + 1, maxY = minY + 1, maxZ = minZ + 1;

        for (double x = minX; x <= maxX; x += 0.5)
            for (double z = minZ; z <= maxZ; z += 0.5)
                for (double y = minY; y <= maxY; y += 0.5)
                    if (hasSpace(x, y, z)) return new Vec3d(x, y, z);

        return null;
    }

    private boolean hasSpace(double x, double y, double z) {
        double minX = x - halfWidth, minZ = z - halfLength;
        double maxX = x + halfWidth, maxY = y + height, maxZ = z + halfLength;

        VoxelShape space = VoxelShapes.cuboidUnchecked(minX, y, minZ, maxX, maxY, maxZ);

        return BlockPos.stream(
                (int) Math.floor(minX),
                (int) Math.floor(y),
                (int) Math.floor(minZ),
                (int) Math.ceil(maxX),
                (int) Math.ceil(maxY),
                (int) Math.ceil(maxZ)
        ).noneMatch(pos -> {
            VoxelShape shape = blockView.getBlockState(pos).getCollisionShape(blockView, pos)
                    .offset(pos.getX(), pos.getY(), pos.getZ());

            return VoxelShapes.matchesAnywhere(shape, space, BooleanBiFunction.AND);
        });
    }

    public static SizedSpaceFinder create(BlockView blockView, EntityType<?> entityType) {
        EntityDimensions dimensions = entityType.getDimensions();

        int width = (int) Math.ceil(dimensions.width);
        int height = (int) Math.ceil(dimensions.height);

        return new SizedSpaceFinder(blockView, width, height, width);
    }
}

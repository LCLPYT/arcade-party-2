package work.lclpnet.ap2.game.anvil_fall;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnvilFallSetup {

    private final Pos[] positions;
    private final Random random;

    private AnvilFallSetup(Pos[] positions, Random random) {
        this.positions = positions;
        this.random = random;
    }

    public BlockPos getRandomPosition() {
        Pos randomPos = positions[random.nextInt(positions.length)];
        int offset = random.nextInt(Math.max(randomPos.height, 1));
        return randomPos.pos.up(offset);
    }

    public static AnvilFallSetup scanWorld(BlockView world, BlockBox box, Random random) {
        BlockPos min = box.getMin(), max = box.getMax();

        final int xMin = min.getX(), yMin = min.getY(), zMin = min.getZ();
        final int xMax = max.getX(), yMax = max.getY(), zMax = max.getZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        List<Pos> positions = new ArrayList<>();

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                int height = 0;

                for (int y = yMin; y <= yMax; y++) {
                    pos.set(x, y, z);

                    BlockState state = world.getBlockState(pos);
                    if (!state.getCollisionShape(world, pos).isEmpty()) break;

                    height++;
                }

                if (height <= 0) continue;

                positions.add(new Pos(new BlockPos(x, yMin, z), height));
            }
        }

        return new AnvilFallSetup(positions.toArray(Pos[]::new), random);
    }

    private record Pos(BlockPos pos, int height) {}
}

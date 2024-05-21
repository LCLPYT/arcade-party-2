package work.lclpnet.ap2.game.splashy_dropper.data;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.IndexedSet;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.ap2.impl.util.world.stage.StageReader;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class SdGenerator {

    private static final int PLACEMENT_FLAGS = Block.FORCE_STATE | Block.SKIP_DROPS;
    private final ServerWorld world;
    private final GameMap map;
    private final Random random;

    public SdGenerator(ServerWorld world, GameMap map, Random random) {
        this.world = world;
        this.map = map;
        this.random = random;
    }

    public void generate() {
        Stage stage = new StageReader(map).readStage();

        WeightedList<SdShape> shapes = new WeightedList<>();
        shapes.add(SdShape.square(1, 1), 0.2f);
        shapes.add(SdShape.square(2, 2), 0.44f);
        shapes.add(SdShape.square(3, 3), 0.36f);

        generatePuddles(stage, shapes);
    }

    private void generatePuddles(Stage stage, WeightedList<SdShape> shapes) {
        WeightedList<ShapeSpace> pool = createPool(stage, shapes);

        /* Algorithm:
         * - pick a random shape from the pool
         * - pick a random position from the shapes remaining possible spaces
         * - if no such position exists, remove the shape from the pool and pick a different shape, or terminate
         * - place the shape at that position and remove all possible spaces intersecting the shape from all shapes
         */

        while (!pool.isEmpty()) {
            // select random shape from pool
            int index = pool.getRandomIndex(random);
            ShapeSpace shape = pool.get(index);

            if (shape.space.isEmpty()) {
                pool.remove(index);
                continue;
            }

            // select random position where the shape can be placed
            BlockPos pos = shape.randomPosition(random);

            if (pos == null) {
                pool.remove(index);
                continue;
            }

            // place the shape in the world
            shape.placeAt(pos, world);

            // remove possible spaces from all pools
            for (ShapeSpace space : pool) {
                space.remove(shape.shape, pos);
            }
        }
    }

    private static @NotNull WeightedList<ShapeSpace> createPool(Stage stage, WeightedList<SdShape> shapes) {
        Set<BlockPos> space = new HashSet<>();

        var it = stage.groundPositionIterator();

        while (it.hasNext()) {
            space.add(it.next().toImmutable());
        }

        // create mutable pool of shapes and their possible spaces
        return shapes.map(shape -> new ShapeSpace(shape, space.stream()
                .filter(pos -> shape.hasSpace(space, pos))
                .collect(Collectors.toCollection(IndexedSet::new))));
    }

    private record ShapeSpace(SdShape shape, IndexedSet<BlockPos> space) {

        @Nullable
        BlockPos randomPosition(Random random) {
            if (space.isEmpty()) return null;

            return space.get(random.nextInt(space.size()));
        }

        public void placeAt(BlockPos pos, ServerWorld world) {
            BlockState water = Blocks.WATER.getDefaultState();

            for (BlockPos shapePos : shape.positions()) {
                shapePos = pos.add(shapePos);
                world.setBlockState(shapePos, water, PLACEMENT_FLAGS);
            }
        }

        public void remove(SdShape shape, BlockPos pos) {
            var it = shape.combinedPositions();

            while (it.hasNext()) {
                BlockPos occupied = pos.add(it.next());

                for (BlockPos selfPos : this.shape.positions()) {
                    space.remove(occupied.subtract(selfPos));
                }
            }
        }
    }
}

package work.lclpnet.ap2.impl.game.item;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.impl.ds.StructureMask;
import work.lclpnet.ap2.impl.ds.WeightedList;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SpecialItemPositions {

    private static final boolean DEBUG_SPAWNS = false;

    private final GameMap map;
    private final BlockPredicate validPos;
    private final Random random;
    private @Nullable WeightedList<BlockBox> spawnBoxes = null;
    private final DebugController debugController;

    public SpecialItemPositions(GameMap map, BlockView world, Random random, DebugController debugController) {
        this(map, new WalkableBlockPredicate(world), random, debugController);
    }

    public SpecialItemPositions(GameMap map, BlockPredicate validPos, Random random, DebugController debugController) {
        this.map = map;
        this.validPos = validPos;
        this.random = random;
        this.debugController = debugController;
    }

    public void scan() {
        JSONObject cfg = map.requireProperty("items");

        BlockPos mapSpawn = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));
        BlockShape shape = MapUtil.readShape(cfg.getJSONObject("spawn-area"), mapSpawn);
        BlockBox bounds = shape.bounds();
        BlockPos minPos = bounds.min();
        StructureMask mask = StructureMask.createEmpty(bounds);

        for (BlockPos pos : shape) {
            if (validPos.test(pos)) {
                mask.setVoxelAt(pos.getX() - minPos.getX(), pos.getY() - minPos.getY(), pos.getZ() - minPos.getZ(), true);
            }
        }

        List<BlockBox> boxes = mask.greedyMeshing().generateBoxes();
        spawnBoxes = WeightedList.of(boxes, BlockBox::volume);

        if (DEBUG_SPAWNS) {
            debugController.visualizeBoxes(boxes, minPos, Matrix3i.IDENTITY, Blocks.LIME_STAINED_GLASS.getDefaultState());
        }
    }

    public Optional<BlockPos> randomPos() {
        if (spawnBoxes == null) {
            return Optional.empty();
        }

        BlockBox box = spawnBoxes.getRandomElement(random);

        if (box == null) {
            return Optional.empty();
        }

        var pos = new BlockPos.Mutable();
        box.randomBlockPos(pos, random);

        return Optional.empty();
    }
}

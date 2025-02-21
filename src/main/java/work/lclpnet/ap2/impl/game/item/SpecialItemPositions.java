package work.lclpnet.ap2.impl.game.item;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.impl.ds.StructureMask;
import work.lclpnet.ap2.impl.ds.WeightedList;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SpecialItemPositions {

    private static final boolean
            DEBUG_SPAWNS = false,
            DEBUG_TIMINGS = false;

    private final BlockPredicate validPos;
    private final DebugController debugController;
    private @Nullable WeightedList<BlockBox> spawnBoxes = null;
    private @Nullable BlockShape shape = null;
    private @Nullable StructureMask mask = null;

    public SpecialItemPositions(BlockPredicate validPos, DebugController debugController) {
        this.validPos = validPos;
        this.debugController = debugController;
    }

    public void init(JSONObject areaJson, BlockPos mapSpawn) {
        shape = MapUtil.readShape(areaJson, mapSpawn);
        mask = StructureMask.createEmpty(shape.bounds());
    }

    public synchronized void update() {
        if (shape == null || mask == null) return;

        debugController.stopWatch().start("scan");

        BlockPos minPos = shape.bounds().min();

        for (BlockPos pos : shape) {
            boolean valid = validPos.test(pos);
            mask.setVoxelAt(pos.getX() - minPos.getX(), pos.getY() - minPos.getY(), pos.getZ() - minPos.getZ(), valid);
        }

        debugController.stopWatch().start("meshing");

        List<BlockBox> boxes = mask.greedyMeshing().generateBoxes();
        spawnBoxes = WeightedList.of(boxes, BlockBox::volume);

        if (DEBUG_TIMINGS) {
            debugController.stopWatch().printResults(System.out);
        }

        if (DEBUG_SPAWNS) {
            debugController.exclusive("spawn_boxes", controller ->
                    controller.visualizeBoxes(boxes, minPos, Matrix3i.IDENTITY, Blocks.LIME_STAINED_GLASS.getDefaultState()));
        }
    }

    public Optional<BlockPos> randomPos(Random random) {
        if (spawnBoxes == null || shape == null) {
            return Optional.empty();
        }

        BlockBox box = spawnBoxes.getRandomElement(random);

        if (box == null) {
            return Optional.empty();
        }

        var pos = new BlockPos.Mutable();
        box.randomBlockPos(pos, random);
        pos.move(shape.bounds().min());

        return Optional.of(pos);
    }
}

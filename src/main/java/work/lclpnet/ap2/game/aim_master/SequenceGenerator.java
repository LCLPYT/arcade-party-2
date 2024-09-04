package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.IndexedSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class SequenceGenerator {
    private final PositionGenerator posGen;
    private final BlockOptions blockOps;
    private final ServerWorld world;
    private final int scoreGoal;

    AimMasterSequence sequence = new AimMasterSequence();
    private final Random random = new Random();

    public SequenceGenerator(PositionGenerator posGen, BlockOptions blockOps, ServerWorld world, int scoreGoal) {
        this.posGen = posGen;
        this.blockOps = blockOps;
        this.world = world;
        this.scoreGoal = scoreGoal;

        this.sequence = generateSequence();
    }

    public AimMasterSequence getSequence() {return sequence;}

    private AimMasterSequence generateSequence() {

        for (int i = 0; i < scoreGoal; i++) {

            HashMap<BlockPos, Block> posBlockMap = new HashMap<>();

            ArrayList<BlockPos> positions = posGen.pickPositions();
            IndexedSet<Block> options = blockOps.getBlockOptions();

            for (BlockPos p : positions) {
                int r = random.nextInt(options.size());
                Block block = options.remove(r);
                posBlockMap.put(p, block);
            }

            Integer targetIndex = random.nextInt(positions.size());
            Pair<Integer, HashMap<BlockPos, Block>> sequenceItem = new Pair<>(targetIndex, posBlockMap);

            sequence.add(sequenceItem);
        }
        //test generation with first sequence item
        for (BlockPos pos : sequence.get(0).getRight().keySet()) world.setBlockState(pos, sequence.get(0).getRight().get(pos).getDefaultState());

        return sequence;
    }
}

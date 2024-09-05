package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AimMasterSequence {

    private final List<Item> sequence = new ArrayList<>();

    public List<Item> getItems() {
        return sequence;
    }

    public record Item(Map<BlockPos, Block> blockMap, BlockPos target) {}
}

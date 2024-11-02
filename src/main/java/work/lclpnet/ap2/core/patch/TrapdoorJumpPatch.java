package work.lclpnet.ap2.core.patch;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.registry.tag.BlockTags;

public class TrapdoorJumpPatch {

    private TrapdoorJumpPatch() {}

    public static boolean preventJumping(BlockState state) {
        return state.isIn(BlockTags.TRAPDOORS) && state.contains(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.OPEN);
    }
}

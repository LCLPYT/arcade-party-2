package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.HashMap;
import java.util.Map;

public class ResetBlockWorldModifier implements WorldModifier {

    private final ServerLevel world;
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final int undoFlags;

    public ResetBlockWorldModifier(ServerLevel world, int undoFlags) {
        this.world = world;
        this.undoFlags = undoFlags;
    }

    @Override
    public void setBlockState(BlockPos pos, BlockState state, int flags) {
        synchronized (this) {
            if (!states.containsKey(pos)) {
                BlockState prevState = world.getBlockState(pos);

                if (prevState != state) {
                    states.put(new BlockPos(pos), prevState);
                }
            }
        }

        world.setBlock(pos, state, flags);
    }

    @Override
    public void spawnEntity(Entity entity) {
        throw new UnsupportedOperationException("World modifier only supports block changes");
    }

    public void undo() {
        synchronized (this) {
            for (var entry : states.entrySet()) {
                world.setBlock(entry.getKey(), entry.getValue(), undoFlags);
            }

            states.clear();
        }
    }
}

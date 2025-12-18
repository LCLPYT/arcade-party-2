package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;

public class PitPathFindingPredicate implements PathFindingPredicate {

    private final MSStruct struct;

    public PitPathFindingPredicate(MSStruct struct) {
        this.struct = struct;
    }

    @Override
    public boolean canReach(int x, int y, int z, Mob entity, BlockPos from) {
        var node = struct.nodeAt(x, y, z);

        if (node == null) {
            return false;
        }

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            return false;
        }

        return !oriented.isPitAt(x, y, z);
    }
}

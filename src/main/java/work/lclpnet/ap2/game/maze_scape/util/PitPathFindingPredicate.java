package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;

public class PitPathFindingPredicate implements PathFindingPredicate {

    private final MSStruct struct;
    private final MutPos pos = new MutPos();

    public PitPathFindingPredicate(MSStruct struct) {
        this.struct = struct;
    }

    @Override
    public boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from) {
        pos.x = x;
        pos.y = y;
        pos.z = z;

        var node = struct.nodeAt(pos);

        if (node == null) {
            return false;
        }

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            return false;
        }

        return !oriented.isPitAt(x, y, z);
    }

    private static class MutPos implements Position {
        double x = 0, y = 0, z = 0;

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public double getZ() {
            return z;
        }
    }
}

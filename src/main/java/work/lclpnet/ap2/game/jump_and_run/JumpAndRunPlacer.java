package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.Matrix3i;

public class JumpAndRunPlacer {

    private final ServerWorld world;

    public JumpAndRunPlacer(ServerWorld world) {
        this.world = world;
    }

    public void place(JumpAndRun jumpAndRun) {
        for (OrientedPart part : jumpAndRun.getParts()) {
            placePart(part);
        }
    }

    private void placePart(OrientedPart part) {
        Matrix3i mat3 = Matrix3i.makeRotationY(part.rotation());

        StructureUtil.placeStructure(part.structure(), world, new BlockPos(part.offset()), mat3);
    }
}

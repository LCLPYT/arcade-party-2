package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;

public class JumpAndRunPlacer {

    private final ServerWorld world;

    public JumpAndRunPlacer(ServerWorld world) {
        this.world = world;
    }

    public void place(JumpAndRun jumpAndRun) {
        for (Segment segment : jumpAndRun.segments()) {
            for (JumpPart part : segment.parts()) {
                StructureUtil.placeStructureFast(part, world);
            }
        }
    }
}

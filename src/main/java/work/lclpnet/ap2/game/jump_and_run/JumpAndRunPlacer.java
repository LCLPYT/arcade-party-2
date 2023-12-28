package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.impl.util.StructureUtil;

public class JumpAndRunPlacer {

    private final ServerWorld world;

    public JumpAndRunPlacer(ServerWorld world) {
        this.world = world;
    }

    public void place(JumpAndRun jumpAndRun) {
        for (JumpPart part : jumpAndRun.parts()) {
            StructureUtil.placeStructure(part.printable(), world);
        }
    }
}

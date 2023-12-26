package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.kibu.mc.BuiltinBlockState;
import work.lclpnet.kibu.structure.BlockStructure;

public class JumpEnd {

    private final BlockStructure structure;
    private final BlockBox bounds;
    private final @Nullable BlockPos spawn;
    private final Connector exit;

    public JumpEnd(BlockStructure structure, BlockBox bounds, @Nullable BlockPos spawn, Connector exit) {
        this.structure = structure;
        this.bounds = bounds;
        this.spawn = spawn;
        this.exit = exit;
    }

    public BlockStructure getStructure() {
        return structure;
    }

    public BlockBox getBounds() {
        return bounds;
    }

    public @Nullable BlockPos getSpawn() {
        return spawn;
    }

    public Connector getExit() {
        return exit;
    }

    public static JumpEnd from(BlockStructure structure) {
        BlockBox bounds = StructureUtil.getBounds(structure);

        Connector exit = null;
        BlockPos spawn = null;

        final var origin = structure.getOrigin();
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (var pos : structure.getBlockPositions()) {
            int x = pos.getX() - ox, y = pos.getY() - oy, z = pos.getZ() - oz;

            var state = structure.getBlockState(pos);
            String mat = state.getAsString();

            if (mat.startsWith("minecraft:command_block")) {
                spawn = new BlockPos(x, y, z);

                if (exit != null) break;

                continue;
            }

            if (!mat.startsWith("minecraft:chain_command_block")) continue;

            Direction dir = bounds.getTangentSurface(x, y, z);

            if (dir == null || dir.getAxis() == Direction.Axis.Y) continue;

            exit = new Connector(new BlockPos(x, y, z), dir);

            if (spawn != null) break;
        }

        if (exit == null) throw new IllegalStateException("Exit not found");

        if (spawn != null) {
            structure.setBlockState(new work.lclpnet.kibu.mc.BlockPos(
                    spawn.getX() + ox,
                    spawn.getY() + oy,
                    spawn.getZ() + oz
            ), BuiltinBlockState.AIR);
        }

        BlockPos exitPos = exit.pos();

        structure.setBlockState(new work.lclpnet.kibu.mc.BlockPos(
                exitPos.getX() + ox,
                exitPos.getY() + oy,
                exitPos.getZ() + oz
        ), BuiltinBlockState.AIR);

        return new JumpEnd(structure, bounds, spawn, exit);
    }
}

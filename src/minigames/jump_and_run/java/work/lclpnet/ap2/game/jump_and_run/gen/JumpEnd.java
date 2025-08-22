package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.kibu.mc.BuiltinKibuBlockState;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.structure.BlockStructure;

public record JumpEnd(BlockStructure structure, BlockBox bounds, @Nullable BlockPos spawn, Connector exit) {

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

            Direction dir = bounds.tangentSurface(x, y, z);

            if (dir == null || dir.getAxis() == Direction.Axis.Y) continue;

            exit = new Connector(new BlockPos(x, y, z), dir);

            if (spawn != null) break;
        }

        if (exit == null) throw new IllegalStateException("Exit not found");

        if (spawn != null) {
            structure.setBlockState(new KibuBlockPos(
                    spawn.getX() + ox,
                    spawn.getY() + oy,
                    spawn.getZ() + oz
            ), BuiltinKibuBlockState.AIR);
        }

        BlockPos exitPos = exit.pos();

        structure.setBlockState(new KibuBlockPos(
                exitPos.getX() + ox,
                exitPos.getY() + oy,
                exitPos.getZ() + oz
        ), BuiltinKibuBlockState.AIR);

        return new JumpEnd(structure, bounds, spawn, exit);
    }
}

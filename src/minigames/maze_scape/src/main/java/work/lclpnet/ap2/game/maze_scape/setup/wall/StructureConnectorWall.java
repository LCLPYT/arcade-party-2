package work.lclpnet.ap2.game.maze_scape.setup.wall;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.game.util.WorldModifier;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.List;
import java.util.Random;

public class StructureConnectorWall implements ConnectorWall {

    private final BlockStructure struct;
    private final List<Connector3> connectors;
    private final Logger logger;

    public StructureConnectorWall(BlockStructure struct, List<Connector3> connectors, Logger logger) {
        this.struct = struct;
        this.connectors = connectors;
        this.logger = logger;
    }

    @Override
    public void place(Connector3 connector, OrientedStructurePiece oriented, WorldModifier modifier, Random random) {
        // find fitting connector and place
        String target = connector.target();
        BlockPos connectorPos = connector.pos();

        var other = connectors.stream().filter(c -> target.equals(c.name())).findAny().orElse(null);

        if (other == null) return;

        int rotation = connector.rotateToFace(other);
        var mat = Matrix3i.makeRotationY(rotation);
        var rotatedOtherPos = mat.transform(other.pos());

        BlockPos offset = connectorPos.subtract(rotatedOtherPos);
        int ox = offset.getX(), oy = offset.getY(), oz = offset.getZ();
        var pos = new BlockPos.MutableBlockPos();

        var adapter = FabricBlockStateAdapter.getInstance();
        int flags = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

        for (var kibuPos : struct.getBlockPositions()) {
            var kibuState = struct.getBlockState(kibuPos);

            if (kibuState.isAir()) continue;

            BlockState state = adapter.revert(kibuState);

            if (state == null) {
                logger.warn("Unknown block state {}", kibuState.getAsString());
                continue;
            }

            if (state.is(Blocks.JIGSAW)) continue;

            mat.transform(kibuPos.getX(), kibuPos.getY(), kibuPos.getZ(), pos);
            pos.set(pos.getX() + ox, pos.getY() + oy, pos.getZ() + oz);

            state = RotationUtil.rotate(state, mat);

            modifier.setBlockState(pos, state, flags);
        }
    }
}

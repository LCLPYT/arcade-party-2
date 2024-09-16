package work.lclpnet.ap2.game.maze_scape.setup.wall;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.*;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Random;

public class PaletteConnectorWall implements ConnectorWall {

    private final BlockPalette palette;

    public PaletteConnectorWall(BlockPalette palette) {
        this.palette = palette;
    }

    @Override
    public void place(Connector3 connector, OrientedStructurePiece oriented, WorldModifier modifier, Random random) {
        // determine connector position in rotated local space
        BlockPos connectorPos = connector.pos();

        Orientation orientation = connector.orientation();
        Vec3i normal = orientation.getFacing().getVector();
        FabricStructureWrapper room = oriented.piece().wrapper();

        BVH bounds = oriented.bounds();
        BlockBox box = bounds.box();

        if (box == null) return;

        BlockPos min = box.min(), roomPos = oriented.pos();
        int mx = min.getX(), my = min.getY(), mz = min.getZ();
        int rx = roomPos.getX(), ry = roomPos.getY(), rz = roomPos.getZ();

        var plane = new PlanePredicate(connectorPos, normal);
        var notWall = new NotWallPredicate(room, oriented.inverseTransformation());

        int flags = Block.FORCE_STATE | Block.SKIP_DROPS;

        new BoxFloodFill(mx, my, mz, bounds.width(), bounds.height(), bounds.length()).execute(
                // start above connector
                connectorPos.offset(orientation.getRotation()),
                // place random block from palette
                pos -> {
                    BlockState state = palette.sample(random);
                    modifier.setBlockState(pos, state, flags);
                },
                // advance to neighbour if in plane and transparent
                (x, y, z) -> plane.test(x, y, z) && notWall.test(x - rx, y - ry, z - rz));
    }
}

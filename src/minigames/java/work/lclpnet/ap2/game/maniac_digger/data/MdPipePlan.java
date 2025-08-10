package work.lclpnet.ap2.game.maniac_digger.data;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.function.Function;

public class MdPipePlan {

    public static final BlockState WALL_MATERIAL = Blocks.BEDROCK.getDefaultState();
    private final FabricStructureWrapper struct;
    private final int diameter;
    private final Vec3d spawn;
    private final BlockBox bounds;
    private final Function<BlockPos, BlockState> fillMaterial;
    private final BlockPos.Mutable tmpPos = new BlockPos.Mutable();

    public MdPipePlan(Vec3i dimensions, int diameter, Vec3d spawn, BlockBox bounds, Function<BlockPos, BlockState> fillMaterial) {
        var structure = FabricStructureWrapper.createArrayStructure(dimensions.getX(), dimensions.getY(), dimensions.getZ(), new KibuBlockPos());
        this.struct = new FabricStructureWrapper(structure);
        this.diameter = diameter;
        this.spawn = spawn;
        this.bounds = bounds;
        this.fillMaterial = fillMaterial;
    }

    public BlockStructure structure() {
        return struct.getStructure();
    }

    public void placeVertical(BlockPos pos, int depth) {
        int d = diameter - 1;

        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                tmpPos.set(pos, x, 0, z);

                if (!struct.getBlockState(tmpPos).isAir()) continue;

                if (depth == 0 || x == 0 || x == d || z == 0 || z == d) {
                    struct.setBlockState(tmpPos, WALL_MATERIAL);
                } else {
                    BlockState material = this.fillMaterial.apply(tmpPos);
                    struct.setBlockState(tmpPos, material);
                }
            }
        }
    }

    public void placeHorizontal(BlockPos.Mutable pos, BlockPos.Mutable pos2) {
        if (pos.getY() != pos2.getY()) {
            throw new IllegalStateException("Positions are on different heights");
        }

        clearWall(pos, pos2);

        int dx = Math.abs(pos.getX() - pos2.getX()) + diameter;
        int dz = Math.abs(pos.getZ() - pos2.getZ()) + diameter;

        int minX = Math.min(pos.getX(), pos2.getX());
        int minZ = Math.min(pos.getZ(), pos2.getZ());
        int minY = pos.getY();

        for (int x = 0; x < dx; x++) {
            for (int z = 0; z < dz; z++) {
                for (int y = 0; y < diameter; y++) {
                    tmpPos.set(minX + x, minY + y, minZ + z);

                    if (!struct.getBlockState(tmpPos).isAir()) continue;

                    if (x == 0 || x == dx - 1 || z == 0 || z == dz - 1 || y == 0 || y == diameter - 1) {
                        struct.setBlockState(tmpPos, WALL_MATERIAL);
                    } else {
                        BlockState material = this.fillMaterial.apply(tmpPos);
                        struct.setBlockState(tmpPos, material);
                    }
                }
            }
        }

        fixFloor(pos2);
    }

    private void clearWall(BlockPos.Mutable pos, BlockPos.Mutable pos2) {
        int dx = (int) Math.signum(pos2.getX() - pos.getX());
        int dz = (int) Math.signum(pos2.getZ() - pos.getZ());

        if (dx != 0 && dz != 0 || dx == dz) {
            throw new IllegalStateException("Invalid direction");
        }

        int ox = dx > 0 ? (dx * diameter) - 1 : 0;
        int oz = dz > 0 ? (dz * diameter) - 1 : 0;

        int ax = dx == 0 ? 1 : 0;
        int az = dz == 0 ? 1 : 0;

        BlockState air = Blocks.AIR.getDefaultState();

        tmpPos.set(pos, ox + ax, 1, oz + az);
        struct.setBlockState(tmpPos, air);
        tmpPos.set(pos, ox + 2 * ax, 1, oz + 2 * az);
        struct.setBlockState(tmpPos, air);
        tmpPos.set(pos, ox + ax, 2, oz + az);
        struct.setBlockState(tmpPos, air);
        tmpPos.set(pos, ox + 2 * ax, 2, oz + 2 * az);
        struct.setBlockState(tmpPos, air);
    }

    private void fixFloor(BlockPos.Mutable pos) {
        tmpPos.set(pos, 1, 0, 1);
        BlockState material = this.fillMaterial.apply(tmpPos);
        struct.setBlockState(tmpPos, material);

        tmpPos.set(pos, 2, 0, 1);
        material = this.fillMaterial.apply(tmpPos);
        struct.setBlockState(tmpPos, material);

        tmpPos.set(pos, 1, 0, 2);
        material = this.fillMaterial.apply(tmpPos);
        struct.setBlockState(tmpPos, material);

        tmpPos.set(pos, 2, 0, 2);
        material = this.fillMaterial.apply(tmpPos);
        struct.setBlockState(tmpPos, material);
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        struct.setBlockState(pos, state);
    }

    public Vec3d getSpawn() {
        return spawn;
    }

    public BlockBox getBounds() {
        return bounds;
    }
}

package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Falling;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.StructureMask;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;
import java.util.function.Predicate;

public record MSPieceDebugger(ServerWorld world, BlockStructure struct, String name, BlockPos offset) {

    public void debugStructure() {
        var origin = offset(0);

        submit(() -> {
            addText(origin, "Structure");

            StructureUtil.placeStructureFast(struct, world, offset);
        });
    }

    public void debugInsideMask(StructureMask mask) {
        submit(() -> placeMask(1, mask, "Final mask", Blocks.LAPIS_BLOCK.getDefaultState()));
    }

    public void debugClosedCorridorMask(StructureMask mask) {
        submit(() -> placeMask(2, mask, "Corridors closed", Blocks.EMERALD_BLOCK.getDefaultState()));
    }

    public void debugBvhBoxes(List<BlockBox> boxes) {
        var origin = offset(3);

        submit(() -> {
            addText(origin, "BVH boxes");

            placeBoxes(boxes, origin);
        });
    }

    private void placeMask(int index, StructureMask mask, String detail, BlockState state) {
        var origin = offset(index);
        addText(origin, detail);

        var pos = new BlockPos.Mutable();

        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                for (int z = 0; z < mask.length(); z++) {
                    if (!mask.isVoxelAt(x, y, z)) continue;

                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    world.setBlockState(pos, state, Block.FORCE_STATE | Block.SKIP_DROPS);
                }
            }
        }
    }

    private void placeBoxes(List<BlockBox> boxes, BlockPos origin) {
        List<BlockState> states = Registries.BLOCK.streamEntries()
                .map(RegistryEntry.Reference::value)
                .filter(Predicate.not(block -> block instanceof Falling))
                .map(Block::getDefaultState)
                .filter(Predicate.not(BlockState::isAir))
                .filter(Predicate.not(BlockState::isTransparent))
                .filter(state -> state.getFluidState().isOf(Fluids.EMPTY))
                .limit(boxes.size())
                .toList();

        var mut = new BlockPos.Mutable();

        for (int i = 0, len = boxes.size(); i < len; i++) {
            BlockState state = states.get(i % states.size());
            BlockBox box = boxes.get(i);

            for (BlockPos pos : box) {
                mut.set(origin.getX() + pos.getX(), origin.getY() + pos.getY(), origin.getZ() + pos.getZ());
                world.setBlockState(mut, state, Block.FORCE_STATE | Block.SKIP_DROPS);
            }
        }
    }

    private void addText(BlockPos origin, String detail) {
        var display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);

        display.setPos(origin.getX(), origin.getY() + struct.getHeight() + 1, origin.getZ());
        display.setText(Text.literal(name + " - " + detail));
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);

        world.spawnEntity(display);
    }

    private void submit(Runnable task) {
        world.getServer().execute(task);
    }

    private BlockPos offset(int index) {
        return this.offset.add(index * (struct.getWidth() + 5), 0, 0);
    }
}

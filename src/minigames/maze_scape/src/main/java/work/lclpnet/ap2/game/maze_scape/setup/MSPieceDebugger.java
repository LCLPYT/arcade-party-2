package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.StructureMask;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;
import java.util.function.Predicate;

public record MSPieceDebugger(ServerLevel world, BlockStructure struct, String name, BlockPos offset) {

    public void debugStructure() {
        var origin = offset(0);

        submit(() -> {
            addText(origin, "Structure");

            StructureUtil.placeStructureFast(struct, world, offset);
        });
    }

    public void debugInsideMask(StructureMask mask) {
        submit(() -> placeMask(1, mask, "Final mask", Blocks.LAPIS_BLOCK.defaultBlockState()));
    }

    public void debugClosedCorridorMask(StructureMask mask) {
        submit(() -> placeMask(2, mask, "Corridors closed", Blocks.EMERALD_BLOCK.defaultBlockState()));
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

        var pos = new BlockPos.MutableBlockPos();

        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                for (int z = 0; z < mask.length(); z++) {
                    if (!mask.isVoxelAt(x, y, z)) continue;

                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                    world.setBlock(pos, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    private void placeBoxes(List<BlockBox> boxes, BlockPos origin) {
        List<BlockState> states = BuiltInRegistries.BLOCK.listElements()
                .map(Holder.Reference::value)
                .filter(Predicate.not(block -> block instanceof Fallable))
                .map(Block::defaultBlockState)
                .filter(Predicate.not(BlockState::isAir))
                .filter(Predicate.not(BlockState::propagatesSkylightDown))
                .filter(state -> state.getFluidState().is(Fluids.EMPTY))
                .limit(boxes.size())
                .toList();

        var mut = new BlockPos.MutableBlockPos();

        for (int i = 0, len = boxes.size(); i < len; i++) {
            BlockState state = states.get(i % states.size());
            BlockBox box = boxes.get(i);

            for (BlockPos pos : box) {
                mut.set(origin.getX() + pos.getX(), origin.getY() + pos.getY(), origin.getZ() + pos.getZ());
                world.setBlock(mut, state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    private void addText(BlockPos origin, String detail) {
        var display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, world);

        display.setPosRaw(origin.getX(), origin.getY() + struct.getHeight() + 1, origin.getZ());
        display.setText(Component.literal(name + " - " + detail));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);

        world.addFreshEntity(display);
    }

    private void submit(Runnable task) {
        world.getServer().execute(task);
    }

    private BlockPos offset(int index) {
        return this.offset.offset(index * (struct.getWidth() + 5), 0, 0);
    }
}

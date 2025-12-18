package work.lclpnet.ap2.game.mimicry.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.Objects;
import java.util.Set;

public final class MimicryRoom {

    private final BlockPos pos;
    private final BlockPos spawn;
    private final float yaw;
    private final BlockBox buttons;
    private BlockPos activeButtonPos = null;
    private BlockState prevButtonBase = null;

    public MimicryRoom(BlockPos pos, BlockPos spawn, float yaw, BlockBox buttons) {
        this.pos = pos;
        this.spawn = spawn;
        this.yaw = yaw;
        this.buttons = buttons;
    }

    public void teleport(ServerPlayer player, ServerLevel world) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleportTo(world, x, y, z, Set.of(), yaw, 0.0F, true);
    }

    public int buttonIndex(BlockPos pos) {
        return buttons.posToIndexYZX(pos);
    }

    public void setButtonActive(int i, ServerLevel world) {
        resetActiveButton(world);

        BlockPos buttonPos = buttonPos(i);

        BlockState state = world.getBlockState(buttonPos);

        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return;

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos base = buttonPos.relative(facing.getOpposite());

        activeButtonPos = base;
        prevButtonBase = world.getBlockState(base);

        world.setBlockAndUpdate(base, Blocks.LIME_CONCRETE.defaultBlockState());
    }

    public BlockPos buttonPos(int i) {
        return buttons.indexToPosYZX(i);
    }

    public void resetActiveButton(ServerLevel world) {
        if (activeButtonPos == null || prevButtonBase == null) return;

        world.setBlockAndUpdate(activeButtonPos, prevButtonBase);
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockPos spawn() {
        return spawn;
    }

    public float yaw() {
        return yaw;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MimicryRoom) obj;
        return Objects.equals(this.pos, that.pos) &&
               Objects.equals(this.spawn, that.spawn) &&
               Float.floatToIntBits(this.yaw) == Float.floatToIntBits(that.yaw) &&
               Objects.equals(this.buttons, that.buttons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, spawn, yaw, buttons);
    }

    @Override
    public String toString() {
        return "MimicryRoom[pos=%s, spawn=%s, yaw=%s, buttons=%s]".formatted(pos, spawn, yaw, buttons);
    }
}

package work.lclpnet.ap2.game.mimicry.data;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Objects;

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

    public void teleport(ServerPlayerEntity player, ServerWorld world) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleport(world, x, y, z, yaw, 0.0F);
    }

    public int buttonIndex(BlockPos pos) {
        return buttons.posToIndexYZX(pos);
    }

    public void setButtonActive(int i, ServerWorld world) {
        resetActiveButton(world);

        BlockPos buttonPos = buttonPos(i);

        BlockState state = world.getBlockState(buttonPos);

        if (!state.contains(Properties.HORIZONTAL_FACING)) return;

        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockPos base = buttonPos.offset(facing.getOpposite());

        activeButtonPos = base;
        prevButtonBase = world.getBlockState(base);

        world.setBlockState(base, Blocks.LIME_CONCRETE.getDefaultState());
    }

    public BlockPos buttonPos(int i) {
        return buttons.indexToPosYZX(i);
    }

    public void resetActiveButton(ServerWorld world) {
        if (activeButtonPos == null || prevButtonBase == null) return;

        world.setBlockState(activeButtonPos, prevButtonBase);
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

package work.lclpnet.ap2.game.red_light_green_light;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.CustomNbt;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.hook.util.PositionRotation;

import java.util.ArrayList;
import java.util.List;

public class EnderWatchers {

    private static final MapCodec<Boolean> NBT_CODEC = Codec.BOOL.fieldOf("ender_watcher");
    private static final boolean DEBUG_SPAWNS = true;

    private final BlockBox bounds;
    private final ServerWorld world;
    private final DebugController debugController;
    private final List<PositionRotation> spawns = new ArrayList<>();

    public EnderWatchers(BlockBox bounds, ServerWorld world, DebugController debugController) {
        this.bounds = bounds;
        this.world = world;
        this.debugController = debugController;
    }

    public void scan() {
        for (BlockPos pos : bounds) {
            BlockState state = world.getBlockState(pos);

            if (!isEnderWatcher(state, pos)) continue;

            int rotation = state.get(SkullBlock.ROTATION, 0);
            float yaw = MathHelper.wrapDegrees(180f + rotation * 90f / 4f);

            var spawn = new PositionRotation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
            spawns.add(spawn);

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.NOTIFY_LISTENERS);
        }

        if (DEBUG_SPAWNS) {
            debugController.renderer().ifPresent(renderer -> {
                for (PositionRotation spawn : spawns) {
                    renderer.arrow(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()), MathUtil.yaw2vec(spawn.getYaw()), Blocks.BLUE_CONCRETE.getDefaultState());
                }
            });
        }
    }

    public void warn() {

    }

    public void show() {

    }

    public void hide() {

    }

    private boolean isEnderWatcher(BlockState state, BlockPos pos) {
        if (!state.isOf(Blocks.PLAYER_HEAD) && !state.isOf(Blocks.PLAYER_WALL_HEAD)) return false;

        SkullBlockEntity skull = world.getBlockEntity(pos, BlockEntityType.SKULL).orElse(null);

        if (skull == null) return false;

        return CustomNbt.get(skull.getComponents(), NBT_CODEC).orElse(false);
    }
}

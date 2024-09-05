package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.lobby.util.RayCaster;

import java.util.Map;

import static net.minecraft.util.Formatting.GOLD;

public class AimMasterDomain {

    private final BlockPos spawn;
    private final ServerWorld world;
    private final float yaw;
    private BlockPos offsetTarget = null;

    public AimMasterDomain(BlockPos spawn, float yaw, ServerWorld world) {
        this.spawn = spawn;
        this.yaw = yaw;
        this.world = world;
    }

    @Nullable
    public BlockPos getCurrentTarget() {
        return offsetTarget;
    }

    public void teleport(ServerPlayerEntity player) {
        player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yaw, 0);
    }

    public void setBlocks(AimMasterSequence.Item item, ServerPlayerEntity player) {
        Map<BlockPos, Block> blockMap = item.blockMap();

        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.add(spawn);
            BlockPos target = item.target();
            offsetTarget = target.add(spawn);

            Block block = blockMap.get(pos);

            world.setBlockState(offsetPos, getState(block));

            // give target Item
            ItemStack stack = new ItemStack(blockMap.get(target));

            stack.set(DataComponentTypes.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                    .styled(style -> style.withItalic(false).withFormatting(GOLD)));

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
        }
    }

    private static BlockState getState(Block block) {
        if (block instanceof LeavesBlock) return block.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        if (block instanceof ObserverBlock) return block.getDefaultState().with(FacingBlock.FACING, Direction.NORTH);
        return block.getDefaultState();
    }

    public void removeBlocks(AimMasterSequence.Item item) {
        Map<BlockPos, Block> blockMap = item.blockMap();
        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.add(spawn);
            world.setBlockState(offsetPos, getState(Blocks.AIR));
            offsetTarget = null;
        }
    }

    public boolean rayCaster(ServerPlayerEntity player, int radius) {
        Vec3d start = player.getEyePos();
        Vec3d end = player.getEyePos().add(player.getRotationVector().multiply(radius * 2));

        BlockHitResult res = RayCaster.rayCast(start, end, pos -> pos.equals(offsetTarget));

        return res.getType() == HitResult.Type.BLOCK;
    }
}

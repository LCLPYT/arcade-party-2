package work.lclpnet.ap2.game.aim_master;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.game.util.RayCaster;

import java.util.Map;
import java.util.Set;

import static net.minecraft.ChatFormatting.GOLD;

public class AimMasterDomain {

    private final BlockPos spawn;
    private final ServerLevel world;
    private final float yaw;
    private BlockPos offsetTarget = null;

    public AimMasterDomain(BlockPos spawn, float yaw, ServerLevel world) {
        this.spawn = spawn;
        this.yaw = yaw;
        this.world = world;
    }

    @Nullable
    public BlockPos getCurrentTarget() {
        return offsetTarget;
    }

    public void teleport(ServerPlayer player) {
        player.teleportTo(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), yaw, 0, true);
    }

    public void setBlocks(AimMasterSequence.Item item, ServerPlayer player) {
        Map<BlockPos, Block> blockMap = item.blockMap();

        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.offset(spawn);
            BlockPos target = item.target();
            offsetTarget = target.offset(spawn);

            Block block = blockMap.get(pos);

            world.setBlockAndUpdate(offsetPos, getState(block));

            // give target Item
            ItemStack stack = new ItemStack(blockMap.get(target));

            stack.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                    .withStyle(style -> style.withItalic(false).applyFormat(GOLD)));

            Inventory inventory = player.getInventory();
            inventory.setItem(4, stack);
        }
    }

    private static BlockState getState(Block block) {
        if (block instanceof LeavesBlock) return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        if (block instanceof ObserverBlock) return block.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH);
        return block.defaultBlockState();
    }

    public void removeBlocks(AimMasterSequence.Item item) {
        Map<BlockPos, Block> blockMap = item.blockMap();
        for (BlockPos pos : blockMap.keySet()) {
            BlockPos offsetPos = pos.offset(spawn);
            world.setBlockAndUpdate(offsetPos, getState(Blocks.AIR));
            offsetTarget = null;
        }
    }

    public boolean rayCaster(ServerPlayer player, int radius) {
        Vec3 start = player.getEyePosition();
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(radius * 2));

        BlockHitResult res = RayCaster.rayCast(start, end, pos -> pos.equals(offsetTarget));

        return res.getType() == HitResult.Type.BLOCK;
    }
}

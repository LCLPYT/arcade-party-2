package work.lclpnet.ap2.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.lclpnet.ap2.core.hook.ChiseledBookshelfModifyCallback;

@Mixin(ChiseledBookshelfBlock.class)
public class ChiseledBookshelfBlockMixin {
    @Inject(
            method = "onUseWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/ChiseledBookshelfBlock;tryAddBook(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/block/entity/ChiseledBookshelfBlockEntity;Lnet/minecraft/item/ItemStack;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ap2$afterAddBook(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ChiseledBookshelfModifyCallback.ADD.invoker().onModifyBook(serverPlayer, pos);
        }
    }

    @Inject(
            method = "onUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/ChiseledBookshelfBlock;tryRemoveBook(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/block/entity/ChiseledBookshelfBlockEntity;I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ap2$afterRemoveBook(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ChiseledBookshelfModifyCallback.REMOVE.invoker().onModifyBook(serverPlayer, pos);
        }
    }
}

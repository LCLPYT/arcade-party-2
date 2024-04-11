package work.lclpnet.ap2.impl.util.checkpoint;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class CheckpointHelper {

    private CheckpointHelper() {}

    public static void notifyWhenReached(CheckpointManager manager, TranslationService translations) {
        manager.whenCheckpointReached((player, checkpoint) -> {
            var msg = translations.translateText(player, "game.ap2.reached_checkpoint").formatted(Formatting.GREEN);

            player.sendMessage(msg, true);
            player.playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 0.4f, 1f);
        });
    }

    public static Action<PlayerAction> setupResetItem(HookRegistrar hooks, BooleanSupplier disabled, Predicate<ServerPlayerEntity> eligible) {
        var hook = PlayerAction.createHook();

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world1, hand) -> {
            if (disabled.getAsBoolean() || !(player instanceof ServerPlayerEntity sp) || !eligible.test(sp)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(Items.PLAYER_HEAD)) {
                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            hook.invoker().act(sp);

            return TypedActionResult.success(ItemStack.EMPTY, true);
        });

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world1, hand, hitResult) -> {
            if (disabled.getAsBoolean() || !(player instanceof ServerPlayerEntity sp) || !eligible.test(sp)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(Items.PLAYER_HEAD)) {
                return ActionResult.PASS;
            }

            hook.invoker().act(sp);

            return ActionResult.SUCCESS;
        });

        return Action.create(hook);
    }

    public static Action<PlayerAction> whenFallingIntoLava(HookRegistrar hooks, Predicate<ServerPlayerEntity> predicate) {
        var hook = PlayerAction.createHook();

        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            if (!predicate.test(player)) return false;

            BlockState state = player.getServerWorld().getBlockState(player.getBlockPos());
            if (!state.isOf(Blocks.LAVA)) return false;

            hook.invoker().act(player);
            return false;
        });

        return Action.create(hook);
    }
}

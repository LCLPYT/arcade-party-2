package work.lclpnet.ap2.impl.util.checkpoint;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.gaco.collisions.util.PlayerAction;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.translate.Translations;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class CheckpointHelper {

    private CheckpointHelper() {}

    public static void notifyWhenReached(CheckpointManager manager, Translations translations) {
        manager.whenCheckpointReached((player, checkpoint) -> {
            var msg = translations.translateText(player, "game.ap2.reached_checkpoint").formatted(Formatting.GREEN);

            player.sendMessage(msg, true);
            player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 0.4f, 1f);
        });
    }

    public static Action<PlayerAction> setupResetItem(HookRegistrar hooks, BooleanSupplier disabled, Predicate<ServerPlayerEntity> eligible) {
        var hook = PlayerAction.createHook();

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world1, hand)
                -> handleUse(disabled, eligible, player, hand, hook));

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world1, hand, hitResult)
                -> handleUse(disabled, eligible, player, hand, hook));

        return Action.create(hook);
    }

    private static @NotNull ActionResult handleUse(BooleanSupplier disabled, Predicate<ServerPlayerEntity> eligible, PlayerEntity player, Hand hand, Hook<PlayerAction> hook) {
        if (disabled.getAsBoolean() || !(player instanceof ServerPlayerEntity sp) || !eligible.test(sp)) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);

        if (!stack.isOf(Items.PLAYER_HEAD)) {
            return ActionResult.PASS;
        }

        hook.invoker().act(sp);

        return ActionResult.SUCCESS_SERVER;
    }

    public static Action<PlayerAction> whenFallingIntoLava(HookRegistrar hooks, Predicate<ServerPlayerEntity> predicate) {
        var hook = PlayerAction.createHook();

        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            if (!predicate.test(player)) return false;

            BlockState state = player.getWorld().getBlockState(player.getBlockPos());
            if (!state.isOf(Blocks.LAVA)) return false;

            hook.invoker().act(player);
            return false;
        });

        return Action.create(hook);
    }

    public static Checkpoint fromJson(JSONObject json) {
        BlockPos pos = MapUtil.readBlockPos(json.getJSONArray("pos"));
        float yaw = json.has("yaw") ? MapUtil.readAngle(json.getNumber("yaw")) : 0f;
        BlockBox box = MapUtil.readBox(json.getJSONArray("bounds"));

        return new Checkpoint(pos.toBottomCenterPos(), yaw, 0f, box);
    }

    public static void giveResetItem(Iterable<? extends ServerPlayerEntity> players, ServerWorld world, Translations translations, int slot) {
        for (ServerPlayerEntity player : players) {
            giveResetItem(player, world, translations, slot);
        }
    }

    public static void giveResetItem(ServerPlayerEntity player, ServerWorld world, Translations translations, int slot) {
        PlayerHead head = world.getRegistryManager()
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptionalValue(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        ItemStack reset = head.createStack();

        reset.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "ap2.game.reset").formatted(Formatting.RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setStack(slot, reset);
    }
}

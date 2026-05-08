package work.lclpnet.ap2.impl.util.checkpoint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
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
            var msg = translations.translateText(player, "game.ap2.reached_checkpoint").formatted(ChatFormatting.GREEN);

            player.sendOverlayMessage(msg);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.4f, 1f);
        });
    }

    public static Action<PlayerAction> setupResetItem(HookRegistrar hooks, BooleanSupplier disabled, Predicate<ServerPlayer> eligible) {
        var hook = PlayerAction.createHook();

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, (player, world1, hand)
                -> handleUse(disabled, eligible, player, hand, hook));

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks, (player, world1, hand, hitResult)
                -> handleUse(disabled, eligible, player, hand, hook));

        return Action.create(hook);
    }

    private static @NotNull InteractionResult handleUse(BooleanSupplier disabled, Predicate<ServerPlayer> eligible, Player player, InteractionHand hand, Hook<PlayerAction> hook) {
        if (disabled.getAsBoolean() || !(player instanceof ServerPlayer sp) || !eligible.test(sp)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        if (!stack.is(Items.PLAYER_HEAD)) {
            return InteractionResult.PASS;
        }

        hook.invoker().act(sp);

        return InteractionResult.SUCCESS_SERVER;
    }

    public static Action<PlayerAction> whenFallingIntoLava(HookRegistrar hooks, Predicate<ServerPlayer> predicate) {
        var hook = PlayerAction.createHook();

        PlayerMoveCallback.HOOK.registerWith(hooks, (player, from, to) -> {
            if (!predicate.test(player)) return false;

            BlockState state = player.level().getBlockState(player.blockPosition());
            if (!state.is(Blocks.LAVA)) return false;

            hook.invoker().act(player);
            return false;
        });

        return Action.create(hook);
    }

    public static Checkpoint fromJson(JSONObject json) {
        BlockPos pos = MapUtil.readBlockPos(json.getJSONArray("pos"));
        float yaw = json.has("yaw") ? MapUtil.readAngle(json.getNumber("yaw")) : 0f;
        BlockBox box = MapUtil.readBox(json.getJSONArray("bounds"));

        return new Checkpoint(pos.getBottomCenter(), yaw, 0f, box);
    }

    public static void giveResetItem(Iterable<? extends ServerPlayer> players, ServerLevel world, Translations translations, int slot) {
        for (ServerPlayer player : players) {
            giveResetItem(player, world, translations, slot);
        }
    }

    public static void giveResetItem(ServerPlayer player, ServerLevel world, Translations translations, int slot) {
        PlayerHead head = world.registryAccess()
                .lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptional(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        ItemStack reset = head.createStack();

        reset.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "ap2.game.reset").formatted(ChatFormatting.RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setItem(slot, reset);
    }
}

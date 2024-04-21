package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SpeedBuildersInstance extends EliminationGameInstance implements MapBootstrap {

    private final Random random;
    private final SpeedBuilderSetup setup;
    private SpeedBuildersManager manager = null;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SpeedBuilderSetup(random, gameHandle.getLogger());

        useSurvivalMode();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            manager = new SpeedBuildersManager(islands, gameHandle);
        });
    }

    @Override
    protected void prepare() {
        manager.eachIsland(SbIsland::teleport);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerAbilities abilities = player.getAbilities();
            abilities.allowFlying = true;
            abilities.flying = true;
            player.sendAbilitiesUpdate();
        }
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow((entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos),
                    ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.BREAK_BLOCKS);

            config.allow(ProtectionTypes.USE_ITEM_ON_BLOCK,
                    (entity, pos) -> entity instanceof ServerPlayerEntity player && canModify(player, pos.up()));
        });

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canModify(serverPlayer, pos)) {
                destroyBlock(world, pos, serverPlayer);
            }

            return ActionResult.PASS;
        });
    }

    private void destroyBlock(World world, BlockPos pos, ServerPlayerEntity player) {
        gameHandle.getGameScheduler().timeout(() -> {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) return;

            world.syncWorldEvent(null, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));

            if (!world.removeBlock(pos, false)) return;

            Item item = state.getBlock().asItem();

            player.getInventory().insertStack(new ItemStack(item));
        }, 1);
    }

    private boolean canModify(ServerPlayerEntity player, BlockPos pos) {
        return manager.isBuildingPhase() &&
               gameHandle.getParticipants().isParticipating(player) &&
               manager.isWithinBuildingArea(player, pos);
    }
}

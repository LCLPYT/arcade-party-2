package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator;
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.world.WorldBorderUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.*;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class ManiacDiggerInstance extends FFAGameInstance implements MapBootstrapFunction {

    private final OrderedDataContainer<ServerPlayer, PlayerRef> reachedBottom = new OrderedDataContainer<>(PlayerRef::create);
    private final IntScoreDataContainer<ServerPlayer, PlayerRef> score = new IntScoreDataContainer<>(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away");
    private final CombinedDataContainer<ServerPlayer, PlayerRef> data = new CombinedDataContainer<>(List.of(reachedBottom, score));
    private final Map<UUID, MdPipe> pipes = new HashMap<>();
    private final Set<UUID> wrongTool = new HashSet<>();
    private int winHeight = 64;

    public ManiacDiggerInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread
        return new ServerThreadMapBootstrap(this);
    }


    @Override
    public void bootstrapWorld(@NotNull ServerLevel world, @NotNull GameMap map) {
        Number winHeight = map.requireProperty("goal-height");
        this.winHeight = winHeight.intValue();

        MdGenerator generator = new MdGenerator(world, map, gameHandle.getLogger(), new Random());
        Participants participants = gameHandle.getParticipants();

        var pipes = generator.generate(participants.count());

        int i = 0;

        for (ServerPlayer player : participants) {
            MdPipe pipe = pipes.get(i++);
            this.pipes.put(player.getUUID(), pipe);
        }
    }

    @Override
    protected void prepare() {
        ServerLevel world = getWorld();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            MdPipe pipe = pipes.get(player.getUUID());

            if (pipe == null) continue;

            Vec3 spawn = pipe.spawn();
            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), 0f, 0f, true);
            PlayerReset.setAttribute(player, Attributes.SCALE, 0.5);

            giveItems(player);
        }

        useTaskDisplay();

        // set initial score
        data.clear();
        gradePlayers(null);
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.MODIFY_INVENTORY));

        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(BlockModificationHooks.BREAK_BLOCK, (world, pos, entity) ->
                !(entity instanceof ServerPlayer player) || !canBreak(player, pos));

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && canBreak(serverPlayer, pos)) {
                onHitBlock(serverPlayer, pos);
            }

            return InteractionResult.PASS;
        });

        gameHandle.getScheduler().interval(this::checkGoal, 1);
    }

    private boolean canBreak(ServerPlayer player, BlockPos pos) {
        if (!gameHandle.getParticipants().isParticipating(player) || winManager.isGameOver()) {
            return false;
        }

        MdPipe pipe = pipes.get(player.getUUID());

        if (pipe == null || !pipe.bounds().contains(pos)) {
            return false;
        }

        BlockState state = player.level().getBlockState(pos);

        return !(state.getBlock() instanceof StainedGlassBlock) && !state.is(Blocks.GLASS);
    }

    private void checkGoal() {
        if (winManager.isGameOver()) return;

        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (player.getBlockY() <= winHeight) {
                reachedBottom.add(player);
                gradePlayers(player.getUUID());
                winManager.complete();
                break;
            }
        }
    }

    private void giveItems(ServerPlayer player) {
        ItemStack pickaxe = unbreakable(new ItemStack(Items.IRON_PICKAXE));
        ItemStack shovel = unbreakable(new ItemStack(Items.IRON_SHOVEL));
        ItemStack axe = unbreakable(new ItemStack(Items.IRON_AXE));
        ItemStack hoe = unbreakable(new ItemStack(Items.IRON_HOE));

        player.getInventory().setItem(0, axe);
        player.getInventory().setItem(1, pickaxe);
        player.getInventory().setItem(2, shovel);
        player.getInventory().setItem(3, hoe);
    }

    private void onHitBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = player.getMainHandItem();

        if (isCorrectTool(state, stack)) {
            if (wrongTool.remove(player.getUUID())) {
                onCorrectTool(player);
            }
        } else {
            if (wrongTool.add(player.getUUID())) {
                onWrongTool(player);
            }
        }
    }

    private void onWrongTool(ServerPlayer player) {
        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.maniac_digger.wrong_tool")
                .styled(style -> style.withColor(0xff0000));

        player.displayClientMessage(msg, true);

        WorldBorderUtil.setWarning(player);
    }

    private void onCorrectTool(ServerPlayer player) {
        player.displayClientMessage(Component.empty(), true);

        WorldBorderUtil.resetWarningBlocks(player);
    }

    private void gradePlayers(@Nullable UUID winnerUuid) {
        // grade players who are not yet in the goal by their distance to the goal
        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (player.getUUID().equals(winnerUuid)) continue;

            int distance = Math.max(0, player.getBlockY() - winHeight - 1);

            score.setScore(player, distance);
        }
    }

    private static boolean isCorrectTool(BlockState state, ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);

        if (tool == null) return false;  // not a tool

        for (var rule : tool.rules()) {
            if (state.is(rule.blocks())) {
                return true;
            }
        }

        return false;
    }
}

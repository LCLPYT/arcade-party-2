package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.WorldBorderWarningBlocksChangedS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator;
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.kibu.access.network.packet.WorldBorderWarningBlocksChangedS2CPacketAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.util.PlayerReset;

import java.util.*;

public class ManiacDiggerInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> reachedBottom = new OrderedDataContainer<>(PlayerRef::create);
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> score = new ScoreDataContainer<>(PlayerRef::create, Ordering.ASCENDING, "game.ap2.maniac_digger.result");
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> data = new CombinedDataContainer<>(List.of(reachedBottom, score));
    private final Map<UUID, MdPipe> pipes = new HashMap<>();
    private final Set<UUID> wrongTool = new HashSet<>();
    private int winHeight = 64;

    public ManiacDiggerInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread
        return new ServerThreadMapBootstrap(this);
    }


    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        Number winHeight = map.requireProperty("goal-height");
        this.winHeight = winHeight.intValue();

        MdGenerator generator = new MdGenerator(world, map, gameHandle.getLogger(), new Random());
        Participants participants = gameHandle.getParticipants();

        var pipes = generator.generate(participants.count());

        int i = 0;

        for (ServerPlayerEntity player : participants) {
            MdPipe pipe = pipes.get(i++);
            this.pipes.put(player.getUuid(), pipe);
        }
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            MdPipe pipe = pipes.get(player.getUuid());

            if (pipe == null) continue;

            Vec3d spawn = pipe.spawn();
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), 0f, 0f);
            PlayerReset.setAttribute(player, EntityAttributes.GENERIC_SCALE, 0.5);

            giveItems(player);
        }

        useTaskDisplay();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.MODIFY_INVENTORY));

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(BlockModificationHooks.BREAK_BLOCK, (world, pos, entity) ->
                !(entity instanceof ServerPlayerEntity player) || !canBreak(player, pos));

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && canBreak(serverPlayer, pos)) {
                onHitBlock(serverPlayer, pos);
            }

            return ActionResult.PASS;
        });

        gameHandle.getGameScheduler().interval(this::checkGoal, 1);
    }

    private boolean canBreak(ServerPlayerEntity player, BlockPos pos) {
        if (!gameHandle.getParticipants().isParticipating(player) || winManager.isGameOver()) {
            return false;
        }

        MdPipe pipe = pipes.get(player.getUuid());

        if (pipe == null || !pipe.bounds().contains(pos)) {
            return false;
        }

        BlockState state = player.getServerWorld().getBlockState(pos);

        return !(state.getBlock() instanceof StainedGlassBlock) && !state.isOf(Blocks.GLASS);
    }

    private void checkGoal() {
        if (winManager.isGameOver()) return;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (player.getBlockY() <= winHeight) {
                reachedBottom.add(player);
                gradePlayers(player);
                winManager.win(player);
                break;
            }
        }
    }

    private void giveItems(ServerPlayerEntity player) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        shovel.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        hoe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        player.getInventory().setStack(0, axe);
        player.getInventory().setStack(1, pickaxe);
        player.getInventory().setStack(2, shovel);
        player.getInventory().setStack(3, hoe);
    }

    private void onHitBlock(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = player.getMainHandStack();

        if (isCorrectTool(state, stack)) {
            if (wrongTool.remove(player.getUuid())) {
                onCorrectTool(player);
            }
        } else {
            if (wrongTool.add(player.getUuid())) {
                onWrongTool(player);
            }
        }
    }

    private void onWrongTool(ServerPlayerEntity player) {
        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.maniac_digger.wrong_tool")
                .styled(style -> style.withColor(0xff0000));

        player.sendMessage(msg, true);

        var packet = WorldBorderWarningBlocksChangedS2CPacketAccess.withWarningBlocks(
                new WorldBorderWarningBlocksChangedS2CPacket(player.getServerWorld().getWorldBorder()),
                Integer.MAX_VALUE);

        player.networkHandler.sendPacket(packet);
    }

    private void onCorrectTool(ServerPlayerEntity player) {
        player.sendMessage(Text.empty(), true);

        var packet = new WorldBorderWarningBlocksChangedS2CPacket(player.getServerWorld().getWorldBorder());
        player.networkHandler.sendPacket(packet);
    }

    private void gradePlayers(ServerPlayerEntity winner) {
        UUID winnerUuid = winner.getUuid();

        // grade players who are not yet in the goal by their distance to the goal
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (player.getUuid().equals(winnerUuid)) continue;

            int distance = Math.max(0, player.getBlockY() - winHeight - 1);

            score.setScore(player, distance);
        }
    }

    private static boolean isCorrectTool(BlockState state, ItemStack stack) {
        ToolComponent tool = stack.get(DataComponentTypes.TOOL);

        if (tool == null) return false;  // not a tool

        for (var rule : tool.rules()) {
            if (state.isIn(rule.blocks())) {
                return true;
            }
        }

        return false;
    }
}

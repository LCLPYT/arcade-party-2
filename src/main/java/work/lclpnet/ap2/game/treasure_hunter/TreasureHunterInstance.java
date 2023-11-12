package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.json.JSONArray;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class TreasureHunterInstance extends DefaultGameInstance {
    Random rand = new Random();
    private final OrderedDataContainer data = new OrderedDataContainer();

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        setDefaultGameMode(GameMode.SURVIVAL);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {

        Participants participants = gameHandle.getParticipants();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!participants.isParticipating(serverPlayer)) return ActionResult.PASS;

            if (!world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.CHEST)) return ActionResult.PASS;

            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.2f, 1.8f);
            player.playSound(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.2f, 0.5f);

            data.add(serverPlayer);
            win(serverPlayer);
            return ActionResult.success(true);
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!participants.isParticipating(serverPlayer)) return ActionResult.PASS;

            if (!world.getBlockState(pos).isOf(Blocks.CHEST)) return ActionResult.PASS;

            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.2f, 1.8f);
            player.playSound(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.2f, 0.5f);

            data.add(serverPlayer);
            win(serverPlayer);
            return ActionResult.success(true);
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (!participants.isParticipating(serverPlayer)) return true;

            if (!state.isOf(Blocks.SAND)) return true;
            if (!(rand.nextFloat() < 0.005)) return true;
            spawnCoin(pos, world);
            return true;
        });

        placeChest();
    }

    private void spawnCoin(BlockPos pos, World world) {
        ItemEntity coin = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.SUNFLOWER));
        world.spawnEntity(coin);
        System.out.println("LCLPCoin spawned at: " + pos);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                World world = entity.getWorld();
                BlockState state = world.getBlockState(pos);

                return state.isOf(Blocks.SAND);
            });
            config.allow(ProtectionTypes.PICKUP_ITEM, (playerEntity, itemEntity) -> {
                if (itemEntity.getStack().isOf(Items.SUNFLOWER)) {
                    itemEntity.discard();
                    giveCoin(playerEntity);
                }
                return false;
            });
        });

        giveShovelsToPlayers();
    }

    private void giveCoin(PlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.BLOCKS, 0.7f, 1.55f);
    }

    private void giveShovelsToPlayers() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void placeChest() {

        ServerWorld world = getWorld();
        final JSONArray chestArea = Objects.requireNonNull(getMap().getProperty("chest-area"), "Chest area not defined.");
        final BlockPos corner1 = MapUtil.readBlockPos((JSONArray) chestArea.get(0));
        final BlockPos corner2 = MapUtil.readBlockPos((JSONArray) chestArea.get(1));
        List<BlockPos> chestAreaList = new ArrayList<>();

        for (BlockPos block : BlockPos.iterate(corner1, corner2)) {
            if (world.getBlockState(block).isOf(Blocks.SAND)) {
                chestAreaList.add(block.toImmutable());
            }
        }

        int randomIndex = rand.nextInt(1, chestAreaList.size() - 1);

        BlockPos chestPos = chestAreaList.get(randomIndex);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        System.out.print("chestPos: " + chestPos + "\n");
    }
}

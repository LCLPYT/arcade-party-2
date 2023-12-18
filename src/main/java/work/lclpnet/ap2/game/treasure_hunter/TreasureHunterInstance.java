package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
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
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.*;

public class TreasureHunterInstance extends DefaultGameInstance {

    private final Random rand = new Random();
    private final OrderedDataContainer data = new OrderedDataContainer();
    private final Set<BlockState> materials = new HashSet<>();

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.DO_TILE_DROPS).set(false, null);
        gameRules.get(GameRules.DO_ENTITY_DROPS).set(false, null);

        MapUtil.readBlockStates(getMap().requireProperty("materials"), materials, gameHandle.getLogger());

        Participants participants = gameHandle.getParticipants();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)
                || !world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.CHEST)) {
                return ActionResult.PASS;
            }

            if (isGameOver()) {
                return ActionResult.FAIL;
            }

            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.2f, 1.8f);
            player.playSound(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.2f, 0.5f);

            data.add(serverPlayer);
            win(serverPlayer);

            return ActionResult.success(true);
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return true;
            }

            if (!materials.contains(state) || !(rand.nextFloat() < 0.005)) return true;

            spawnCoin(pos, world);

            return true;
        });

        placeChest();

        useTaskDisplay();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                World world = entity.getWorld();
                BlockState state = world.getBlockState(pos);

                return materials.contains(state);
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

    private void spawnCoin(BlockPos pos, World world) {
        ItemEntity coin = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.SUNFLOWER));
        world.spawnEntity(coin);
    }

    private void giveCoin(PlayerEntity player) {
        // TODO actually give the player the coin
        player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.BLOCKS, 0.7f, 1.55f);
    }

    private void giveShovelsToPlayers() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            stack.addEnchantment(Enchantments.EFFICIENCY, 4);
            ItemStackUtil.setUnbreakable(stack, true);

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void placeChest() {
        ServerWorld world = getWorld();
        BlockBox box = MapUtil.readBox(getMap().requireProperty("chest-area"));
        List<BlockPos> chestAreaList = new ArrayList<>();

        for (BlockPos block : box) {
            BlockState state = world.getBlockState(block);

            if (materials.contains(state)) {
                chestAreaList.add(block.toImmutable());
            }
        }

        int randomIndex = rand.nextInt(chestAreaList.size());

        BlockPos chestPos = chestAreaList.get(randomIndex);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
    }
}

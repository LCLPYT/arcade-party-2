package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
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
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.*;

public class TreasureHunterInstance extends DefaultGameInstance {

    private static final float COIN_CHANCE = 0.025f;
    private final Random random = new Random();
    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> foundChest = new OrderedDataContainer<>(PlayerRef::create);
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> score = new ScoreDataContainer<>(PlayerRef::create);
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> data = new CombinedDataContainer<>(List.of(foundChest, score));
    private final Set<BlockState> materials = new HashSet<>();

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
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

            if (winManager.isGameOver()) {
                return ActionResult.FAIL;
            }

            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.2f, 1.8f);
            player.playSoundToPlayer(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.2f, 0.5f);

            foundChest.add(serverPlayer);
            winManager.win(serverPlayer);

            return ActionResult.success(true);
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
                return true;
            }

            if (!materials.contains(state) || !(random.nextFloat() < COIN_CHANCE)) return true;

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

            config.allow(ProtectionTypes.PICKUP_ITEM, (player, item) -> {
                if (player instanceof ServerPlayerEntity serverPlayer && item.getStack().isOf(Items.SUNFLOWER)) {
                    item.discard();
                    giveCoin(serverPlayer);
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

    private void giveCoin(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.BLOCKS, 0.7f, 1.55f);

        commons().addScore(player, 1, score);
    }

    private void giveShovelsToPlayers() {
        var efficiency = ItemStackHelper.getEnchantment(Enchantments.EFFICIENCY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = new ItemStack(Items.IRON_SHOVEL);

            stack.addEnchantment(efficiency, 4);
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

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

        int randomIndex = random.nextInt(chestAreaList.size());

        BlockPos chestPos = chestAreaList.get(randomIndex);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
    }
}

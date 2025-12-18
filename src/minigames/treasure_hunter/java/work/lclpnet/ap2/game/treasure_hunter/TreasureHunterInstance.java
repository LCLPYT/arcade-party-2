package work.lclpnet.ap2.game.treasure_hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.*;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class TreasureHunterInstance extends FFAGameInstance {

    private static final float COIN_CHANCE = 0.025f;
    private final Random random = new Random();
    private final OrderedDataContainer<ServerPlayer, PlayerRef> foundChest = new OrderedDataContainer<>(PlayerRef::create);
    private final IntScoreDataContainer<ServerPlayer, PlayerRef> score = new IntScoreDataContainer<>(PlayerRef::create);
    private final CombinedDataContainer<ServerPlayer, PlayerRef> data = new CombinedDataContainer<>(List.of(foundChest, score));
    private final Set<BlockState> materials = new HashSet<>();

    public TreasureHunterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.BLOCK_DROPS, false)
                .set(GameRules.ENTITY_DROPS, false);

        MapUtil.readBlockStates(getMap().requireProperty("materials"), materials, gameHandle.getLogger());

        Participants participants = gameHandle.getParticipants();
        Translations translations = gameHandle.getTranslations();
        HookRegistrar hooks = gameHandle.getHooks();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !participants.isParticipating(serverPlayer)
                || !world.getBlockState(hitResult.getBlockPos()).is(Blocks.CHEST)) {
                return InteractionResult.PASS;
            }

            if (winManager.isGameOver()) {
                return InteractionResult.FAIL;
            }

            ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.8f);
            ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.2f, 0.5f);

            var scoreEntry = score.getEntry(serverPlayer)
                    .<Object>map(entry -> entry.toText(translations))
                    .orElse(Component.literal("-"));

            var detail = translations.translateText("game.ap2.treasure_hunter.found_treasure", scoreEntry);
            foundChest.add(serverPlayer, detail);
            winManager.complete();

            return InteractionResult.SUCCESS_SERVER;
        });

        hooks.registerHook(PlayerInteractionHooks.BREAK_BLOCK, (world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !participants.isParticipating(serverPlayer)) {
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
    protected void go() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                Level world = entity.level();
                BlockState state = world.getBlockState(pos);

                return materials.contains(state);
            });

            config.allow(ProtectionTypes.PICKUP_ITEM, (player, item) -> {
                if (player instanceof ServerPlayer serverPlayer && item.getItem().is(Items.SUNFLOWER)) {
                    item.discard();
                    giveCoin(serverPlayer);
                }

                return false;
            });
        });

        giveShovelsToPlayers();
    }

    private void spawnCoin(BlockPos pos, Level world) {
        ItemEntity coin = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.SUNFLOWER));
        world.addFreshEntity(coin);
    }

    private void giveCoin(ServerPlayer player) {
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.7f, 1.55f);

        commons().addScore(player, 1, score);
    }

    private void giveShovelsToPlayers() {
        var efficiency = ItemHelper.getEnchantment(Enchantments.EFFICIENCY, getWorld().registryAccess());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            ItemStack stack = unbreakable(new ItemStack(Items.IRON_SHOVEL));

            stack.enchant(efficiency, 4);

            Inventory inventory = player.getInventory();
            inventory.setItem(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void placeChest() {
        ServerLevel world = getWorld();
        BlockBox box = MapUtil.readBox(getMap().requireProperty("chest-area"));
        List<BlockPos> chestAreaList = new ArrayList<>();

        for (BlockPos block : box) {
            BlockState state = world.getBlockState(block);

            if (materials.contains(state)) {
                chestAreaList.add(block.immutable());
            }
        }

        int randomIndex = random.nextInt(chestAreaList.size());

        BlockPos chestPos = chestAreaList.get(randomIndex);
        world.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    }
}

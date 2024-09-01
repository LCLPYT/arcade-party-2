package work.lclpnet.ap2.game.mining_battle;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MiningBattleInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private static final int DURATION_SECONDS = 60;
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final MiningBattleOre ore;
    private final Set<BlockState> material = new HashSet<>();
    private BlockBox box = null;

    public MiningBattleInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.ore = new MiningBattleOre(new Random(), gameHandle, this::onGainPoints, this::canBeMined);

        useSurvivalMode();
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread, because the scanWorld method of the ore generator will run faster
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        commons().gameRuleBuilder()
                .set(GameRules.DO_TILE_DROPS, false);

        placeOres(world, map);
    }

    @Override
    protected void prepare() {
        giveItems();
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> canBeMined(pos)));

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(BlockModificationHooks.BREAK_BLOCK, (world, pos, entity) -> {
            if (!(entity instanceof ServerPlayerEntity player) || !participants.isParticipating(player)
                || winManager.isGameOver() || isOutsideMiningArea(pos)) return false;

            BlockState state = world.getBlockState(pos);

            if (ore.isOre(state)) {
                ore.onOreBroken(player, pos, state);
            }

            return false;
        });

        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        commons().createTimer(subject, DURATION_SECONDS).whenDone(this::onTimerDone);
    }

    private void placeOres(ServerWorld world, GameMap map) {
        ore.init();

        box = MapUtil.readBox(map.requireProperty("mining-box"));

        material.clear();
        MapUtil.readBlockStates(map.requireProperty("material"), material, gameHandle.getLogger());

        new MiningBattleGenerator(ore, box, material).generateOre(world);
    }

    private void onGainPoints(ServerPlayerEntity player, int points) {
        commons().addScore(player, points, data);

        if (points <= 1) {
            player.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.5f, 2f);
        } else if (points == 2) {
            player.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 2f);
        } else if (points < 5) {
            player.playSoundToPlayer(SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.3f, 1f);
        } else {
            player.playSoundToPlayer(SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.BLOCKS, 0.5f, 1f);
            player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.325f, 1.2f);
            player.playSoundToPlayer(SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.225f, 0f);
        }
    }

    private void onTimerDone() {
        winManager.win(data.getBestSubject(resolver).orElse(null));
    }

    private void giveItems() {
        var efficiency = ItemStackHelper.getEnchantment(Enchantments.EFFICIENCY, getWorld().getRegistryManager());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.addEnchantment(efficiency, 3);

            pickaxe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

            pickaxe.set(DataComponentTypes.CUSTOM_NAME, TextUtil.getVanillaName(pickaxe).styled(style -> style
                    .withFormatting(Formatting.GOLD)
                    .withItalic(false)));

            player.getInventory().setStack(4, pickaxe);
        }
    }

    private boolean isOutsideMiningArea(BlockPos pos) {
        return box == null || !box.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean canBeMined(BlockPos pos) {
        if (isOutsideMiningArea(pos)) return false;

        ServerWorld world = getWorld();
        BlockState state = world.getBlockState(pos);

        return material.contains(state) || ore.isOre(state);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }
}

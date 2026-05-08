package work.lclpnet.ap2.game.mining_battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.level.BlockModificationHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class MiningBattleInstance extends FFAGameInstance implements MapBootstrapFunction {

    private static final int DURATION_SECONDS = 60;
    private final IntDataContainer<ServerPlayer, PlayerRef> data;
    private final MiningBattleOre ore;
    private final Set<BlockState> material = new HashSet<>();
    private BlockBox box = null;

    public MiningBattleInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.ore = new MiningBattleOre(new Random(), gameHandle, this::onGainPoints, this::canBeMined);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);

        useSurvivalMode();
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread, because the scanWorld method of the ore generator will run faster
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(@NotNull ServerLevel world, @NotNull GameMap map) {
        GameRules gameRules = world.getGameRules();
        MinecraftServer server = gameHandle.getServer();

        gameRules.set(GameRules.BLOCK_DROPS, false, server);

        placeOres(world, map);
    }

    @Override
    protected void prepare() {
        giveItems();
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.BREAK_BLOCKS, (_, pos) -> canBeMined(pos)));

        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks, (world, pos, entity) -> {
            if (!(entity instanceof ServerPlayer player) || !participants.isParticipating(player)
                || winManager.isGameOver() || isOutsideMiningArea(pos)) return false;

            BlockState state = world.getBlockState(pos);

            if (ore.isOre(state)) {
                ore.onOreBroken(player, pos, state);
            }

            return false;
        });

        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        commons().createTimer(subject, DURATION_SECONDS).whenDone(winManager::complete);
    }

    private void placeOres(ServerLevel world, GameMap map) {
        ore.init();

        box = MapUtil.readBox(map.requireProperty("mining-box"));

        material.clear();
        MapUtil.readBlockStates(map.requireProperty("material"), material, gameHandle.getLogger());

        new MiningBattleGenerator(ore, box, material).generateOre(world);
    }

    private void onGainPoints(ServerPlayer player, int points) {
        commons().addScore(player, points, data);

        if (points <= 1) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.5f, 2f);
        } else if (points == 2) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 2f);
        } else if (points < 5) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.3f, 1f);
        } else {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 0.5f, 1f);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.325f, 1.2f);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.225f, 0f);
        }
    }

    private void giveItems() {
        var efficiency = ItemHelper.getEnchantment(Enchantments.EFFICIENCY, getWorld().registryAccess());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            ItemStack pickaxe = unbreakable(new ItemStack(Items.DIAMOND_PICKAXE));
            pickaxe.enchant(efficiency, 3);

            pickaxe.set(DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(pickaxe).withStyle(style -> style
                    .applyFormat(ChatFormatting.GOLD)
                    .withItalic(false)));

            player.getInventory().setItem(4, pickaxe);
        }
    }

    private boolean isOutsideMiningArea(BlockPos pos) {
        return box == null || !box.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean canBeMined(BlockPos pos) {
        if (isOutsideMiningArea(pos)) return false;

        ServerLevel world = getWorld();
        BlockState state = world.getBlockState(pos);

        return material.contains(state) || ore.isOre(state);
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }
}

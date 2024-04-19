package work.lclpnet.ap2.game.mining_battle;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
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
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.hook.world.BlockModificationHooks;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

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
        GameRules gameRules = world.getGameRules();
        MinecraftServer server = gameHandle.getServer();

        gameRules.get(GameRules.DO_TILE_DROPS).set(false, server);

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

        TranslationService translations = gameHandle.getTranslations();

        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        BossBarTimer timer = BossBarTimer.builder(translations, subject)
                .withAlertSound(false)
                .withColor(BossBar.Color.RED)
                .withDurationTicks(Ticks.seconds(DURATION_SECONDS))
                .build();

        timer.addPlayers(PlayerLookup.all(gameHandle.getServer()));
        timer.start(gameHandle.getBossBarProvider(), gameHandle.getGameScheduler());

        timer.whenDone(this::onTimerDone);
    }

    private void placeOres(ServerWorld world, GameMap map) {
        ore.init();

        box = MapUtil.readBox(map.requireProperty("mining-box"));

        material.clear();
        MapUtil.readBlockStates(map.requireProperty("material"), material, gameHandle.getLogger());

        new MiningBattleGenerator(ore, box, material).generateOre(world);
    }

    private void onGainPoints(ServerPlayerEntity player, int points) {
        data.addScore(player, points);

        String key = points == 1 ? "ap2.gain_point" : "ap2.gain_points";

        var msg = gameHandle.getTranslations().translateText(player, key,
                        styled(points, Formatting.YELLOW),
                        styled(data.getScore(player), Formatting.AQUA))
                .formatted(Formatting.GREEN);

        player.sendMessage(msg, true);

        if (points <= 1) {
            player.playSound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.5f, 2f);
        } else if (points == 2) {
            player.playSound(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 2f);
        } else if (points < 5) {
            player.playSound(SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.3f, 1f);
        } else {
            player.playSound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.BLOCKS, 0.5f, 1f);
            player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.325f, 1.2f);
            player.playSound(SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.225f, 0f);
        }
    }

    private void onTimerDone() {
        winManager.win(data.getBestSubject(resolver).orElse(null));
    }

    private void giveItems() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            pickaxe.addEnchantment(Enchantments.EFFICIENCY, 3);

            ItemStackUtil.setUnbreakable(pickaxe, true);

            pickaxe.setCustomName(TextUtil.getVanillaName(pickaxe).styled(style -> style
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

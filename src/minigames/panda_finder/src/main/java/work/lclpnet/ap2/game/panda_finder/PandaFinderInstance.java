package work.lclpnet.ap2.game.panda_finder;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.api.util.world.WorldScanner;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.TranslatedScoreboardObjective;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.NotOccupiedBlockPredicate;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;

import java.util.List;
import java.util.Random;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PandaFinderInstance extends FFAGameInstance {

    public static final int WIN_SCORE = 3;
    private final IntScoreDataContainer<ServerPlayer, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final SpamManager spamManager = new SpamManager();
    private PandaManager pandaManager;
    private DynamicTranslatedPlayerBossBar bossBar;

    public PandaFinderInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        scanWorld();
        readImages();

        bossBar = usePlayerDynamicTaskDisplay(styled(0, ChatFormatting.YELLOW), styled(3, ChatFormatting.YELLOW));
    }

    @Override
    protected void go() {
        HookRegistrar hooks = gameHandle.getHooks();

        PlayerInteractionHooks.USE_ENTITY.registerWith(hooks, (player, _, hand, entity, _) -> {
            onUseEntity(player, hand, entity);
            return InteractionResult.PASS;
        });

        PlayerInteractionHooks.ATTACK_ENTITY.registerWith(hooks, (player, _, hand, entity, _) -> {
            onUseEntity(player, hand, entity);
            return InteractionResult.PASS;
        });

        setupScoreboard();

        nextRound();
    }

    private void setupScoreboard() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        TranslatedScoreboardObjective objective = scoreboardManager.translateObjective("score",
                        ObjectiveCriteria.RenderType.INTEGER, "ap2.score")
                .formatted(ChatFormatting.YELLOW, ChatFormatting.BOLD);

        objective.setSlot(DisplaySlot.SIDEBAR);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            objective.add(player);
        }

        useScoreboardStatsSync(data, objective);
    }

    private void nextRound() {
        pandaManager.next();

        var players = PlayerLookup.all(gameHandle.getServer());

        Translations translations = gameHandle.getTranslations();

        pandaManager.getLocalizedPandaGene().ifPresent(key -> translations.translateText("game.ap2.panda_finder.find",
                        styled(translations.translateText(key), ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GREEN).sendTo(players));
    }

    private void onRoundOver() {
        int maxScore = data.getBestScore().orElse(0);

        if (maxScore >= WIN_SCORE) {
            winManager.complete();
            return;
        }

        gameHandle.getScheduler().timeout(this::nextRound, Ticks.seconds(3));
    }

    private void scanWorld() {
        GameMap map = getMap();
        BlockPos start = MapUtil.readBlockPos(map.requireProperty("search-start"));
        BlockBox bounds = MapUtil.readBox(map.requireProperty("bounds"));
        BlockBox exclude = MapUtil.readBox(map.requireProperty("search-exclude"));

        ServerLevel world = getWorld();

        BlockPredicate predicate = new NotOccupiedBlockPredicate(world).and(pos -> {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            return bounds.contains(x, y, z) && !exclude.contains(x, y, z);
        });

        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate, 1);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, EntityType.PANDA);
        var spaces = spaceFinder.findSpaces(scanner.scan(start));

        pandaManager = new PandaManager(gameHandle.getLogger(), spaces, random, world, gameHandle.getParticipants());
    }

    private void readImages() {
        JSONObject images = getMap().requireProperty("images");

        pandaManager.readImages(images);
    }

    private void onUseEntity(Player player, InteractionHand hand, Entity entity) {
        if (!(entity instanceof Panda panda) || !(player instanceof ServerPlayer serverPlayer)
            || player.hasEffect(MobEffects.BLINDNESS) || hand != InteractionHand.MAIN_HAND) return;

        if (spamManager.interact(serverPlayer)) {
            onCooldownReached(serverPlayer);
            return;
        }

        if (!pandaManager.isSearchedPanda(panda)) return;

        pandaFound(serverPlayer, panda);
    }

    private void onCooldownReached(ServerPlayer player) {
        player.sendSystemMessage(gameHandle.getTranslations().translateText(player, "game.ap2.panda_finder.cooldown")
                .formatted(ChatFormatting.RED));

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 0.5f, 1.5f);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Ticks.seconds(3), 1, false, false));
    }

    private void pandaFound(ServerPlayer player, Panda panda) {
        pandaManager.setFound();

        data.addScore(player, 1);
        bossBar.setArgument(player, 0, styled(data.getScore(player), ChatFormatting.YELLOW));

        Translations translations = gameHandle.getTranslations();
        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.panda_finder.panda_found",
                        styled(player.getScoreboardName(), ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GRAY).sendTo(players);

        Participants participants = gameHandle.getParticipants();

        for (ServerPlayer serverPlayer : players) {
            if (participants.isParticipating(serverPlayer) && serverPlayer != player) {
                ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.5f, 1f);
            } else {
                ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1f);
            }
        }

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));

        ServerLevel world = getWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, panda.getX(), panda.getY(), panda.getZ(), rocket);
        world.addFreshEntity(firework);
        FireworkEntityAccess.explode(firework);

        onRoundOver();
    }
}

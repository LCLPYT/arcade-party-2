package work.lclpnet.ap2.game.panda_finder;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.api.util.world.WorldScanner;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.NotOccupiedBlockPredicate;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.item.FireworkExplosion;
import work.lclpnet.kibu.inv.item.FireworkUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;

public class PandaFinderInstance extends DefaultGameInstance {

    public static final int MAX_ROUNDS = 5;
    public static final int WIN_SCORE = (int) Math.ceil(MAX_ROUNDS * 0.5f);
    private static final int MAX_ROUND_LENGTH_SECONDS = 45;
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private final SpamManager spamManager = new SpamManager();
    private PandaManager pandaManager;
    private BossBarTimer timer = null;
    private int round = 0;
    private boolean foundManually = false;

    public PandaFinderInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        scanWorld();
        readImages();
    }

    @Override
    protected void ready() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        hooks.registerHook(PlayerInteractionHooks.USE_ENTITY, this::onUseEntity);
        hooks.registerHook(PlayerInteractionHooks.ATTACK_ENTITY, this::onUseEntity);

        nextRound();
    }

    private void nextRound() {
        pandaManager.next();

        var players = PlayerLookup.all(gameHandle.getServer());

        TranslationService translations = gameHandle.getTranslations();

        pandaManager.getLocalizedPandaGene().ifPresent(key -> translations.translateText("game.ap2.panda_finder.find",
                        FormatWrapper.styled(translations.translateText(key), Formatting.YELLOW))
                .formatted(Formatting.GREEN).sendTo(players));

        timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.panda_finder.find_the_panda"))
                .withDurationTicks(Ticks.seconds(MAX_ROUND_LENGTH_SECONDS))
                .build();

        timer.addPlayers(players);
        timer.whenDone(this::onRoundOver);
        timer.start(gameHandle.getBossBarProvider(), gameHandle.getGameScheduler());
    }

    private void onRoundOver() {
        round++;

        int maxScore = data.getBestScore().orElse(0);

        if (maxScore >= WIN_SCORE || round >= MAX_ROUNDS) {
            var winners = data.getBestPlayers(gameHandle.getServer());
            win(winners);
            return;
        }

        if (!foundManually) {
            // timer ran out
            var players = PlayerLookup.all(gameHandle.getServer());

            gameHandle.getTranslations().translateText("game.ap2.panda_finder.panda_not_found")
                    .formatted(Formatting.GRAY).sendTo(players);

            for (ServerPlayerEntity player : players) {
                player.playSound(SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.5f, 1f);
            }

            nextRound();
            return;
        }

        foundManually = false;

        gameHandle.getGameScheduler().timeout(this::nextRound, Ticks.seconds(3));
    }

    private void scanWorld() {
        GameMap map = getMap();
        BlockPos start = MapUtil.readBlockPos(map.requireProperty("search-start"));
        BlockBox bounds = MapUtil.readBox(map.requireProperty("bounds"));
        BlockBox exclude = MapUtil.readBox(map.requireProperty("search-exclude"));

        ServerWorld world = getWorld();

        BlockPredicate predicate = BlockPredicate.and(new NotOccupiedBlockPredicate(world), pos -> {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            return bounds.contains(x, y, z) && !exclude.contains(x, y, z);
        });

        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, EntityType.PANDA);
        var spaces = spaceFinder.findSpaces(scanner.scan(start));

        pandaManager = new PandaManager(gameHandle.getLogger(), spaces, random, world, gameHandle.getParticipants());
    }

    private void readImages() {
        JSONObject images = getMap().requireProperty("images");

        pandaManager.readImages(images);
    }

    private ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (!(entity instanceof PandaEntity panda) || !(player instanceof ServerPlayerEntity serverPlayer)
            || player.hasStatusEffect(StatusEffects.BLINDNESS) || hand != Hand.MAIN_HAND
            || hitResult != null) return ActionResult.PASS;

        if (spamManager.interact(serverPlayer)) {
            onCooldownReached(serverPlayer);
            return ActionResult.PASS;
        }

        if (pandaManager.isSearchedPanda(panda)) {
            pandaFound(serverPlayer, panda);
        }

        return ActionResult.PASS;
    }

    private void onCooldownReached(ServerPlayerEntity player) {
        player.sendMessage(gameHandle.getTranslations().translateText(player, "game.ap2.panda_finder.cooldown")
                .formatted(Formatting.RED));

        player.playSound(SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 0.5f, 1.5f);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, Ticks.seconds(3), 1, false, false));
    }

    private void pandaFound(ServerPlayerEntity player, PandaEntity panda) {
        foundManually = true;
        pandaManager.setFound();

        data.addScore(player, 1);

        TranslationService translations = gameHandle.getTranslations();
        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.panda_finder.panda_found",
                        FormatWrapper.styled(player.getEntityName(), Formatting.YELLOW))
                .formatted(Formatting.GRAY).sendTo(players);

        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity serverPlayer : players) {
            if (participants.isParticipating(serverPlayer) && serverPlayer != player) {
                serverPlayer.playSound(SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.5f, 1f);
            } else {
                serverPlayer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1f);
            }
        }

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkUtil.setExplosions(rocket, new FireworkExplosion(FireworkRocketItem.Type.SMALL_BALL, false, false,
                new int[] {0xff0000}, new int[0]));

        ServerWorld world = getWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, panda.getX(), panda.getY(), panda.getZ(), rocket);
        world.spawnEntity(firework);
        FireworkEntityAccess.explode(firework);

        if (timer != null) {
            timer.stop();
        }
    }
}

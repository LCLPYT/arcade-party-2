package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.Pair;
import kotlin.time.Clock;
import kotlin.time.Instant;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.api.game.EliminationController;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback;
import work.lclpnet.ap2.core.mixin.entity.LivingEntityAccessor;
import work.lclpnet.ap2.game.GameInfo;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.base.FFAGameInstance;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.game.api.WorldFacade;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashSet;
import java.util.Set;

import static work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived;

public abstract class EliminationGameInstance extends FFAGameInstance implements EliminationController {

    private final EliminationDataContainer<ServerPlayer, PlayerRef> data = new EliminationDataContainer<>(PlayerRef::create);
    private DynamicTranslatedBossBar remainingDisplay = null;
    private boolean eliminatedMessages = true;
    private boolean teleportEliminated = true;
    private @Nullable Instant survivalStart = null;
    private @Nullable FFAStatsManager survivalStats = null;

    public EliminationGameInstance(MiniGameHandle gameHandle, ServerLevel world, GameMap map) {
        super(gameHandle, world, map);
    }

    @Override
    protected void afterInitialDelay() {
        survivalStart = Clock.System.INSTANCE.now();

        super.afterInitialDelay();
    }

    @Override
    public void participantRemoved(@NonNull ServerPlayer player) {
        // record survival time before super, which may end the game and freeze the stats
        recordSurvivalTime(player);

        // make sure the player is tracked as eliminated
        data.add(player);

        if (remainingDisplay != null) {
            var title = remainingTitle();
            remainingDisplay.setTranslationKey(title.left());
            remainingDisplay.setArguments(title.right());
        }

        super.participantRemoved(player);
    }

    @Override
    protected @NonNull EliminationDataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    protected final DynamicTranslatedBossBar useRemainingPlayersDisplay() {
        GameInfo gameInfo = getGameHandle().getGameInfo();
        Translations translations = getGameHandle().getTranslations();
        Identifier id = gameInfo.identifier("remaining");

        var title = remainingTitle();

        TranslatedBossBar bossBar = translations.translateBossBar(id, title.left(), title.right())
                .with(getGameHandle().getBossBarProvider())
                .formatted(ChatFormatting.GREEN);

        remainingDisplay = new DynamicTranslatedBossBar(bossBar, title.left(), title.right());

        bossBar.setColor(BossEvent.BossBarColor.GREEN);

        bossBar.addPlayers(PlayerLookup.all(getGameHandle().getServer()));

        getGameHandle().getBossBarHandler().showOnJoin(bossBar);

        return remainingDisplay;
    }

    private Pair<String, Object[]> remainingTitle() {
        int remaining = getGameHandle().getParticipants().count();

        String key = remaining != 1 ? "ap2.game.remaining" : "ap2.game.remaining_single";
        Object[] args = new Object[] {FormatWrapper.styled(remaining, ChatFormatting.YELLOW)};

        return Pair.of(key, args);
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected final void useSmoothDeath() {
        HookRegistrar hooks = getGameHandle().getHooks();

        EntityHealthCallback.HOOK.registerWith(hooks, (entity, health) -> {
            if (!(entity instanceof ServerPlayer p)) return false;

            return GameCommons.handleCustomDeath(p, health, (player, source) -> {
                onDeath(player, source.getEntity());
                eliminate(player, source);
            });
        });
    }

    protected void onDeath(@NotNull ServerPlayer player, @Nullable Entity attacker) {
        var accessor = (LivingEntityAccessor) player;

        ServerLevel level = getLevel();
        accessor.invokeDropEquipment(level);
        accessor.invokeDropExperience(level, attacker);
    }

    protected final void disableEliminationMessages() {
        this.eliminatedMessages = false;
    }

    protected final void disableTeleportEliminated() {
        this.teleportEliminated = false;
    }

    protected final void eliminateBelowCriticalHeight() {
        commons().whenBelowCriticalHeight().then(this::eliminate);
    }

    @Override
    public synchronized void eliminateAll(Iterable<? extends ServerPlayer> players) {
        Participants participants = getGameHandle().getParticipants();
        DeathMessages deathMessages = getGameHandle().getDeathMessages();
        MinecraftServer server = getGameHandle().getServer();

        Set<ServerPlayer> toEliminate = new HashSet<>();

        for (ServerPlayer player : players) {
            if (!participants.isParticipating(player)) continue;

            toEliminate.add(player);
            onEliminated(player);

            if (eliminatedMessages) {
                deathMessages.eliminated(player).sendTo(PlayerLookup.all(server));
            }
        }

        // mark all players as eliminated at the same moment
        data.addAll(toEliminate);

        WorldFacade worldFacade = getGameHandle().getWorldFacade();
        PlayerUtil playerUtil = getGameHandle().getPlayerUtil();

        for (ServerPlayer player : toEliminate) {
            participants.remove(player);

            playerUtil.resetPlayer(player);

            if (teleportEliminated) {
                worldFacade.teleport(player);
            }
        }
    }

    @Override
    public void eliminate(ServerPlayer player, @Nullable DamageSource source, @Nullable TranslatedText customMsg) {
        Participants participants = getGameHandle().getParticipants();

        if (participants.isParticipating(player)) {
            if (eliminatedMessages) {
                DeathMessages deathMessages = getGameHandle().getDeathMessages();
                MinecraftServer server = getGameHandle().getServer();

                var msg = customMsg != null ? customMsg : deathMessages.getDeathMessage(player, source);

                msg.sendTo(PlayerLookup.all(server));
            }

            participants.remove(player);
            onEliminated(player);
        }

        WorldFacade worldFacade = getGameHandle().getWorldFacade();
        PlayerUtil playerUtil = getGameHandle().getPlayerUtil();

        playerUtil.resetPlayer(player);

        if (teleportEliminated) {
            worldFacade.teleport(player);
        }
    }

    protected void onEliminated(@NotNull ServerPlayer player) {
        PlayerEliminatedCallback.HOOK.invoker().onEliminated(player);
    }

    /**
     * Enables tracking of the time each player survives, in whole seconds.
     * <p>
     * The timer starts when the game begins (right before {@link #go()} is called) and stops for a player
     * the moment they are eliminated. Players that are still alive when the game ends are credited with the
     * full duration they survived.
     *
     * @param stats The stats manager that holds the given stat, as returned by {@link #createStats(Stat[])}.
     */
    protected final void trackSurvivalTime(FFAStatsManager stats) {
        this.survivalStats = stats;

        winManager.addListener(this::recordRemainingSurvivalTime);
    }

    private void recordRemainingSurvivalTime() {
        getGameHandle().getParticipants().forEach(this::recordSurvivalTime);
    }

    private void recordSurvivalTime(ServerPlayer player) {
        if (survivalStats == null || survivalStart == null) return;

        long elapsedMillis = Clock.System.INSTANCE.now().toEpochMilliseconds() - survivalStart.toEpochMilliseconds();

        survivalStats.set(player, TimeSurvived, (int) (elapsedMillis / 1000L));
    }
}

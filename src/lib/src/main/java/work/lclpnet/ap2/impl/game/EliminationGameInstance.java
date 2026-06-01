package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.EliminationController;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback;
import work.lclpnet.ap2.core.mixin.entity.LivingEntityAccessor;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.game.api.WorldFacade;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class EliminationGameInstance extends FFAGameInstance implements EliminationController {

    private final EliminationDataContainer<ServerPlayer, PlayerRef> data = new EliminationDataContainer<>(PlayerRef::create);
    private DynamicTranslatedBossBar remainingDisplay = null;
    private boolean eliminatedMessages = true;
    private boolean teleportEliminated = true;

    public EliminationGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public void participantRemoved(@NonNull ServerPlayer player) {
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
    protected EliminationDataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    protected final DynamicTranslatedBossBar useRemainingPlayersDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Translations translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("remaining");

        var title = remainingTitle();

        TranslatedBossBar bossBar = translations.translateBossBar(id, title.left(), title.right())
                .with(gameHandle.getBossBarProvider())
                .formatted(ChatFormatting.GREEN);

        remainingDisplay = new DynamicTranslatedBossBar(bossBar, title.left(), title.right());

        bossBar.setColor(BossEvent.BossBarColor.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);

        return remainingDisplay;
    }

    private Pair<String, Object[]> remainingTitle() {
        int remaining = gameHandle.getParticipants().count();

        String key = remaining != 1 ? "ap2.game.remaining" : "ap2.game.remaining_single";
        Object[] args = new Object[] {FormatWrapper.styled(remaining, ChatFormatting.YELLOW)};

        return Pair.of(key, args);
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected final void useSmoothDeath() {
        HookRegistrar hooks = gameHandle.getHooks();

        EntityHealthCallback.HOOK.registerWith(hooks, (entity, health) -> {
            if (!(entity instanceof ServerPlayer p)) return false;

            return GameCommons.handleCustomDeath(p, health, (player, source) -> {
                onDeath(player, source.getEntity());
                eliminate(player, source);
            });
        });
    }

    protected void onDeath(ServerPlayer player, @Nullable Entity attacker) {
        var accessor = (LivingEntityAccessor) player;

        ServerLevel world = getWorld();
        accessor.invokeDropEquipment(world);
        accessor.invokeDropExperience(world, attacker);
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
        Participants participants = gameHandle.getParticipants();
        DeathMessages deathMessages = gameHandle.getDeathMessages();
        MinecraftServer server = gameHandle.getServer();

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

        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

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
        Participants participants = gameHandle.getParticipants();

        if (participants.isParticipating(player)) {
            if (eliminatedMessages) {
                DeathMessages deathMessages = gameHandle.getDeathMessages();
                MinecraftServer server = gameHandle.getServer();

                var msg = customMsg != null ? customMsg : deathMessages.getDeathMessage(player, source);

                msg.sendTo(PlayerLookup.all(server));
            }

            participants.remove(player);
            onEliminated(player);
        }

        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.resetPlayer(player);

        if (teleportEliminated) {
            worldFacade.teleport(player);
        }
    }

    protected void onEliminated(ServerPlayer player) {
        PlayerEliminatedCallback.HOOK.invoker().onEliminated(player);
    }

    protected final FFAStatsManager createStats(Stat<?>... stats) {
        var set = Arrays.stream(stats).collect(Collectors.toCollection(LinkedHashSet::new));
        var manager = new FFAStatsManager(set);

        winManager.setStatsManager(manager);

        return manager;
    }
}

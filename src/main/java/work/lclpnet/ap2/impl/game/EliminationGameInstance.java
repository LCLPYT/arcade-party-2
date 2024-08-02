package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageRecord;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.kibu.access.misc.DamageTrackerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.lobby.game.api.WorldFacade;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class EliminationGameInstance extends DefaultGameInstance {

    private final EliminationDataContainer<ServerPlayerEntity, PlayerRef> data = new EliminationDataContainer<>(PlayerRef::create);
    private DynamicTranslatedBossBar remainingDisplay = null;
    private boolean eliminatedMessages = true;
    private boolean teleportEliminated = true;

    public EliminationGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        // make sure the player is tracked as eliminated
        data.eliminated(player);

        if (remainingDisplay != null) {
            var title = remainingTitle();
            remainingDisplay.setTranslationKey(title.left());
            remainingDisplay.setArguments(title.right());
        }

        super.participantRemoved(player);
    }

    @Override
    protected EliminationDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    protected final DynamicTranslatedBossBar useRemainingPlayersDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Translations translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("remaining");

        var title = remainingTitle();

        TranslatedBossBar bossBar = translations.translateBossBar(id, title.left(), title.right())
                .with(gameHandle.getBossBarProvider())
                .formatted(Formatting.GREEN);

        remainingDisplay = new DynamicTranslatedBossBar(bossBar, title.left(), title.right());

        bossBar.setColor(BossBar.Color.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);

        return remainingDisplay;
    }

    private Pair<String, Object[]> remainingTitle() {
        int remaining = gameHandle.getParticipants().count();

        String key = remaining != 1 ? "ap2.game.remaining" : "ap2.game.remaining_single";
        Object[] args = new Object[] {FormatWrapper.styled(remaining, Formatting.YELLOW)};

        return Pair.of(key, args);
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected final void useSmoothDeath() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            if (!(entity instanceof ServerPlayerEntity player) || health > 0) return false;

            List<DamageRecord> recentDamage = DamageTrackerAccess.getRecentDamage(entity);

            int size = recentDamage.size();

            if (size == 0) {
                eliminate(player);
            } else {
                DamageRecord damageRecord = recentDamage.get(size - 1);
                eliminate(player, damageRecord.damageSource());
            }

            return true;
        });
    }

    protected final void disableEliminationMessages() {
        this.eliminatedMessages = false;
    }

    protected final void disableTeleportEliminated() {
        this.teleportEliminated = false;
    }

    protected synchronized void eliminateAll(Iterable<? extends ServerPlayerEntity> players) {
        Participants participants = gameHandle.getParticipants();
        DeathMessages deathMessages = gameHandle.getDeathMessages();
        MinecraftServer server = gameHandle.getServer();

        Set<ServerPlayerEntity> toEliminate = new HashSet<>();

        for (ServerPlayerEntity player : players) {
            if (!participants.isParticipating(player)) continue;

            toEliminate.add(player);
            onEliminated(player);

            if (eliminatedMessages) {
                deathMessages.eliminated(player).sendTo(PlayerLookup.all(server));
            }
        }

        // mark all players as eliminated at the same moment
        data.allEliminated(toEliminate);

        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        for (ServerPlayerEntity player : toEliminate) {
            participants.remove(player);

            playerUtil.resetPlayer(player);
            worldFacade.teleport(player);
        }
    }

    protected void eliminate(ServerPlayerEntity player) {
        eliminate(player, null);
    }

    protected void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
        Participants participants = gameHandle.getParticipants();

        if (participants.isParticipating(player)) {
            onEliminated(player);

            if (eliminatedMessages) {
                DeathMessages deathMessages = gameHandle.getDeathMessages();
                MinecraftServer server = gameHandle.getServer();

                deathMessages.getDeathMessage(player, source).sendTo(PlayerLookup.all(server));
            }

            participants.remove(player);
        }

        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.resetPlayer(player);

        if (teleportEliminated) {
            worldFacade.teleport(player);
        }
    }

    protected void onEliminated(ServerPlayerEntity player) {}
}

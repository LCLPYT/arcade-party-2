package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.data.EliminationDataContainer;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.FormatWrapper;

public abstract class EliminationGameInstance extends DefaultGameInstance {

    private final EliminationDataContainer data = new EliminationDataContainer();
    private DynamicTranslatedBossBar remainingDisplay = null;

    public EliminationGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        data.eliminated(player);

        if (remainingDisplay != null) {
            var title = remainingTitle();
            remainingDisplay.setTranslationKey(title.left());
            remainingDisplay.setArguments(title.right());
        }

        super.participantRemoved(player);
    }

    @Override
    protected EliminationDataContainer getData() {
        return data;
    }

    protected final DynamicTranslatedBossBar useRemainingPlayersDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        TranslationService translations = gameHandle.getTranslations();
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
}

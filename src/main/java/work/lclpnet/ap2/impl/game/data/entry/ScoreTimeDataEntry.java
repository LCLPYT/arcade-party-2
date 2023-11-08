package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record ScoreTimeDataEntry(PlayerRef player, int score, int ranking) implements DataEntry, ScoreView {

    @Override
    public PlayerRef getPlayer() {
        return player;
    }

    @Override
    public @Nullable TranslatedText toText(TranslationService translationService) {
        return translationService.translateText("ap2.score.points_timed", score, ranking);
    }
}

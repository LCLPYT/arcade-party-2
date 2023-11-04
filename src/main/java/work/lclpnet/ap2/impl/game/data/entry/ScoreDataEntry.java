package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record ScoreDataEntry(PlayerRef player, int score) implements DataEntry {

    @Override
    public PlayerRef getPlayer() {
        return player;
    }

    @Override
    public @Nullable TranslatedText toText(TranslationService translationService) {
        return translationService.translateText("ap2.score.points",score);
    }
}

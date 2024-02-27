package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record ScoreTimeDataEntry<Ref extends SubjectRef>(Ref subject, int score, int ranking) implements DataEntry<Ref>, ScoreView {

    @Override
    public @Nullable TranslatedText toText(TranslationService translationService) {
        return translationService.translateText("ap2.score.points_timed", score, ranking);
    }
}

package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record ScoreDataEntry<Ref extends SubjectRef>(Ref subject, int score) implements DataEntry<Ref>, ScoreView {

    @Override
    public @Nullable TranslatedText toText(TranslationService translationService) {
        return translationService.translateText("ap2.score.points",score);
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> _other) {
        if ((_other instanceof ScoreDataEntry<Ref> other)) {
            return score == other.score;
        }

        return false;
    }
}

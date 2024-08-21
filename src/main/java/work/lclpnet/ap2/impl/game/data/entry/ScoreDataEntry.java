package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record ScoreDataEntry<Ref extends SubjectRef>(Ref subject, int score, @Nullable String textOverride) implements DataEntry<Ref>, ScoreView {

    public ScoreDataEntry(Ref subject, int score) {
        this(subject, score, null);
    }

    @Override
    public @Nullable TranslatedText toText(Translations translationService) {
        String key = textOverride != null ? textOverride : "ap2.score.points";
        return translationService.translateText(key, score);
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> _other) {
        if ((_other instanceof ScoreDataEntry<Ref> other)) {
            return score == other.score;
        }

        return false;
    }
}

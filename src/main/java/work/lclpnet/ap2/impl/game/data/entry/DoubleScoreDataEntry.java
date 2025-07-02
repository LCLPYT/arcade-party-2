package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static java.lang.Math.abs;

public record DoubleScoreDataEntry<Ref extends SubjectRef>(Ref subject, double score, String format, @Nullable String keyOverride) implements DataEntry<Ref> {

    @Override
    public @Nullable TranslatedText toText(Translations translationService) {
        String key = keyOverride != null ? keyOverride : "ap2.score.points";
        return translationService.translateText(key, LocalizedFormat.format(format, score));
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> _other) {
        if ((_other instanceof DoubleScoreDataEntry<Ref> other)) {
            return abs(score - other.score) < 1e-10;
        }

        return false;
    }
}

package work.lclpnet.ap2.api.game.data;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface DataEntry<Ref extends SubjectRef> {

    Ref subject();

    @Nullable
    TranslatedText toText(Translations translationService);

    boolean scoreEquals(DataEntry<Ref> other);
}

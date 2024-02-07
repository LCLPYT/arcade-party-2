package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record SimpleDataEntry<Ref extends SubjectRef>(Ref subject) implements DataEntry<Ref> {

    @Nullable
    @Override
    public TranslatedText toText(TranslationService translationService) {
        return null;
    }
}

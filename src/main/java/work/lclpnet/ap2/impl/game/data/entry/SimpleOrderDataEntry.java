package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record SimpleOrderDataEntry<Ref extends SubjectRef>(Ref subject, int order) implements DataEntry<Ref> {

    @Nullable
    @Override
    public TranslatedText toText(TranslationService translationService) {
        return null;
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> _other) {
        if (_other instanceof SimpleOrderDataEntry<Ref> other) {
            return order == other.order;
        }

        return false;
    }
}

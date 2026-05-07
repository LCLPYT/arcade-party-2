package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record SimpleOrderDataEntry<Ref extends SubjectRef>(Ref subject, int order, @Nullable TranslatedText data) implements DataEntry<Ref> {

    public SimpleOrderDataEntry(Ref subject, int order) {
        this(subject, order, null);
    }

    @Nullable
    @Override
    public TranslatedText toText(Translations translationService) {
        return data;
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> _other) {
        if (_other instanceof SimpleOrderDataEntry<Ref> other) {
            return order == other.order;
        }

        return false;
    }
}

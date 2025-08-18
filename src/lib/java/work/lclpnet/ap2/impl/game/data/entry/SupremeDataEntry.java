package work.lclpnet.ap2.impl.game.data.entry;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public record SupremeDataEntry<Ref extends SubjectRef>(Ref subject) implements DataEntry<Ref> {

    @Override
    public @Nullable TranslatedText toText(Translations translationService) {
        return null;
    }

    @Override
    public boolean scoreEquals(DataEntry<Ref> other) {
        return other.getClass() == this.getClass();
    }
}

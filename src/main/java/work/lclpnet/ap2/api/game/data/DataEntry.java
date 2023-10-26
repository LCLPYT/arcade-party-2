package work.lclpnet.ap2.api.game.data;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface DataEntry {

    PlayerRef getPlayer();

    @Nullable
    TranslatedText toText(TranslationService translationService);
}

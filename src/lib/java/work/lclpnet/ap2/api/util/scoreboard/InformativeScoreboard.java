package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.network.chat.Component;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreHandle;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface InformativeScoreboard {

    ScoreHandle createText(Component text, int position);

    ScoreHandle createText(TranslatedText text, int position);

    default ScoreHandle createText(Component text) {
        return createText(text, ScoreboardLayout.TOP);
    }

    default ScoreHandle createText(TranslatedText text) {
        return createText(text, ScoreboardLayout.TOP);
    }

    default void createNewline(int position) {
        createText(Component.empty(), position);
    }
}

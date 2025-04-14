package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.text.Text;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreHandle;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface InformativeScoreboard {

    ScoreHandle createText(Text text, int position);

    ScoreHandle createText(TranslatedText text, int position);

    default ScoreHandle createText(Text text) {
        return createText(text, ScoreboardLayout.TOP);
    }

    default ScoreHandle createText(TranslatedText text) {
        return createText(text, ScoreboardLayout.TOP);
    }

    default void createNewline(int position) {
        createText(Text.empty(), position);
    }
}

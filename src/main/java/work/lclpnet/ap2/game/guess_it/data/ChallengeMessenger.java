package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.text.Text;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface ChallengeMessenger {

    void task(TranslatedText task);

    void options(Text[] options);
}

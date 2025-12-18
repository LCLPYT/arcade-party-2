package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.network.chat.Component;
import work.lclpnet.kibu.translate.text.TranslatedText;

public interface ChallengeMessenger {

    void task(TranslatedText task);

    void options(Component[] options);
}

package work.lclpnet.ap2.impl.util;

import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public class TimeHelper {

    private TimeHelper() {}

    public static TranslatedText formatTime(Translations translations, int seconds) {
        int minutes = seconds / 60;
        seconds %= 60;

        if (minutes > 0) {
            return translations.translateText("ap2.time.minutes_seconds", minutes, seconds);
        }

        return translations.translateText("ap2.time.seconds", seconds);
    }
}

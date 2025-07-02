package work.lclpnet.ap2.impl.util;

import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static java.lang.Math.floor;

public class TimeHelper {

    private TimeHelper() {}

    public static TranslatedText formatTime(Translations translations, int seconds) {
        int minutes = seconds / 60;
        seconds %= 60;

        if (minutes > 0) {
            return translations.translateText("ap2.time.minutes_seconds", new Object[]{
                    String.format("%02d", minutes), String.format("%02d", seconds)
            });
        }

        return translations.translateText("ap2.time.seconds", seconds);
    }

    public static TranslatedText formatTime(Translations translations, double seconds, String minuteFormat, String secondFormat) {
        int minutes = (int) floor(seconds / 60d);
        seconds %= 60d;

        if (minutes > 0) {
            return translations.translateText(
                    "ap2.time.minutes_seconds",
                    LocalizedFormat.format(minuteFormat, minutes),
                    LocalizedFormat.format(secondFormat, seconds)
            );
        }

        return translations.translateText("ap2.time.seconds", LocalizedFormat.format(secondFormat, seconds));
    }
}

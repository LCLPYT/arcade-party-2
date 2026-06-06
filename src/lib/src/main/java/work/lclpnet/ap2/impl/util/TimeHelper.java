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

    public static TranslatedText formatTime(Translations translations, double seconds) {
        return formatTime(translations, seconds, "%02d", "%04.1f", "%.1f");
    }

    /**
     * Formats a time in seconds to a {@link TranslatedText}.
     * @param translations The translations.
     * @param seconds The time in seconds.
     * @param minuteFormat The string format of the minutes, if seconds >= 60. Will be localized automatically.
     * @param secondCompositeFormat The string format of the seconds, if seconds >= 60. Will be localized automatically.
     * @param secondOnlyFormat The string format of the seconds, if seconds < 60 (i.e. if no minute is written).
     * @return The {@link TranslatedText} with the formatted time.
     */
    public static TranslatedText formatTime(Translations translations, double seconds, String minuteFormat, String secondCompositeFormat, String secondOnlyFormat) {
        int minutes = (int) floor(seconds / 60d);
        seconds %= 60d;

        if (minutes > 0) {
            return translations.translateText(
                    "ap2.time.minutes_seconds",
                    LocalizedFormat.format(minuteFormat, minutes),
                    LocalizedFormat.format(secondCompositeFormat, seconds)
            );
        }

        return translations.translateText("ap2.time.seconds", LocalizedFormat.format(secondOnlyFormat, seconds));
    }
}

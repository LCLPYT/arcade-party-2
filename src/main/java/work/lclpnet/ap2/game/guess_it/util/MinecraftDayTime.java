package work.lclpnet.ap2.game.guess_it.util;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecraftDayTime {
    private static final Pattern
            HH_MM_SS = Pattern.compile("([-+]?\\d+)(?::([-+]?\\d+))?(?::([-+]?\\d+))?");
    private static final Pattern
            HH_MM_SS_12H = Pattern.compile("([-+]?\\d+)(?::([-+]?\\d+))?(?::([-+]?\\d+))?\\s*(am|pm)");

    private MinecraftDayTime() {}

    public static String stringifyDayTime(int time) {
        time %= 24000;

        int hours = (time / 1000 + 6) % 24;
        int minutes = Math.round((time % 1000 * 60) / 1000f) % 60;

        return "%02d:%02d".formatted(hours, minutes);
    }

    public static Optional<String> dayTimeValue(String str) {
        var time = parseDayTime(str);

        if (time.isEmpty()) return Optional.empty();

        return Optional.of(stringifyDayTime(time.getAsInt()));
    }

    public static OptionalInt parseDayTime(String str) {
        str = str.toLowerCase(Locale.ROOT);

        Matcher matcher = HH_MM_SS_12H.matcher(str);

        if (matcher.find()) {
            String modifier = matcher.group(4);

            return parseHourMinuteSecond(matcher)
                    .filter(res -> res.hour >= 0 && res.hour <= 12 && res.minute >= 0 && res.minute <= 60 && res.second >= 0 && res.second <= 60)
                    .map(res -> {
                        int hour = res.hour % 12;

                        if ("pm".equals(modifier)) {
                            hour = hour + 12;
                        }

                        return OptionalInt.of(toDayTime(hour, res.minute, res.second));
                    })
                    .orElse(OptionalInt.empty());
        }

        matcher = HH_MM_SS.matcher(str);

        if (matcher.find()) {
            return parseHourMinuteSecond(matcher)
                    .filter(res -> res.hour >= 0 && res.hour <= 24 && res.minute >= 0 && res.minute <= 60 && res.second >= 0 && res.second <= 60)
                    .map(res -> OptionalInt.of(toDayTime(res.hour, res.minute, res.second)))
                    .orElse(OptionalInt.empty());
        }

        return OptionalInt.empty();
    }

    public static int toDayTime(int hour, int minute, int second) {
        return Math.floorMod(hour - 6, 24) * 1000
               + Math.round(Math.floorMod(minute, 60) * 1000 / 60f)
               + Math.round(Math.floorMod(second, 60) * 1000 / 3600f);
    }

    private static Optional<Result> parseHourMinuteSecond(Matcher matcher) {
        String hourStr = matcher.group(1);
        String minuteStr = matcher.group(2);
        String secondStr = matcher.group(3);

        int hour;
        int minute = 0, second = 0;

        try {
            hour = Integer.parseInt(hourStr, 10);

            if (minuteStr != null) {
                minute = Integer.parseInt(minuteStr, 10);
            }

            if (secondStr != null) {
                second = Integer.parseInt(secondStr, 10);
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        return Optional.of(new Result(hour, minute, second));
    }

    private record Result(int hour, int minute, int second) {}
}

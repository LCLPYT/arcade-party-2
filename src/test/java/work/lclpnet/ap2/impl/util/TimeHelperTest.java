package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.translations.Translator;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.of;

class TimeHelperTest {

    @MethodSource("doubles")
    @ParameterizedTest
    void doubleTime(double seconds, String expected) {
        var translations = new Translations(new TestTranslator());

        assertEquals(expected, TimeHelper.formatTime(translations, seconds, "%02d", "%06.3f")
                .translateTo("en_us").getString());
    }

    private static Stream<Arguments> doubles() {
        return Stream.of(
                of(5.5, "05.500"),
                of(16.332, "16.332"),
                of(62.2, "01:02.200")
        );
    }

    private static class TestTranslator implements Translator {

        @Override
        public @NotNull String translate(String locale, String key) {
            return switch (key) {
                case "ap2.time.minutes_seconds" -> "%s:%s";
                case "ap2.time.seconds" -> "%s";
                default -> key;
            };
        }

        @Override
        public boolean hasTranslation(String locale, String key) {
            return !translate(locale, key).equals(key);
        }

        @Override
        public @NotNull SimpleDateFormat getDateFormat(String locale) {
            return new SimpleDateFormat();
        }

        @Override
        public Iterable<String> getLanguages() {
            return List.of("en_us");
        }
    }
}
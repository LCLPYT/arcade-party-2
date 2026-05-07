package work.lclpnet.ap2.game.guess_it.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftDayTimeTest {

    @ParameterizedTest
    @CsvSource({
            "6,0,0,0", "6,10,1,167", "7,0,0,1000", "8,0,0,2000", "11,43,22,5723", "12,0,0,6000",
            "15,0,0,9000", "17,50,02,11834", "18,0,0,12000", "18,0,36,12010", "18,2,24,12040",
            "18,32,31,12542", "18,47,9,12786", "18,58,8,12969", "19,0,0,13000", "19,11,16,13187",
            "19,40,12,13670", "19,42,7,13702", "23,50,34,17842", "0,0,0,18000", "4,18,0,22300",
            "4,19,51,22331", "4,48,43,22812", "4,48,46,22813", "05,0,0,23000", "5,1,51,23031",
            "5,2,27,23041", "5,12,57,23216", "5,27,36,23460", "5,57,39,23961", "5,59,31,23992",
            "5,59,59,23999"
    })
    void toDayTime_minecraftCycle(int hour, int minute, int second, int expected) {
        int time = MinecraftDayTime.toDayTime(hour, minute, second);
        assertEquals(expected, time);
    }

    @ParameterizedTest
    @CsvSource({"30,0,0,0", "-12,0,0,6000", "-18,0,0,0", "-9,0,0,9000", "24,00,00,18000", "6,-50,-59,167"})
    void toDayTime_outOfBounds(int hour, int minute, int second, int expected) {
        int time = MinecraftDayTime.toDayTime(hour, minute, second);
        assertEquals(expected, time);
    }

    @ParameterizedTest
    @CsvSource({
            "06:00:00,0", "06:00,0", "06,0", "6,0", "6:0,0", "12:30,6500", "6am,0", "6 am, 0", "6:0 am,0",
            "6:0:0 am,0", "12:0:0 pm,6000", "12:30pm,6500", "1 pm,7000", "1 AM,19000", "12:0:0am,18000",
            "0am,18000", "0pm,6000", "14:00 uhr,8000"
    })
    void parseDayTime_validStrings(String str, int expected) {
        int time = MinecraftDayTime.parseDayTime(str).orElseThrow();
        assertEquals(expected, time);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", ":", "-1", "25", "70:00", "17:61"})
    void parseDayTime_invalidStrings(String str) {
        assertTrue(MinecraftDayTime.parseDayTime(str).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "0,06:00", "3000,09:00", "6000,12:00", "6500,12:30", "9000,15:00", "12000,18:00", "18000,00:00",
            "23983,05:59", "13702,19:42"
    })
    void stringifyDayTime(int time, String expected) {
        String str = MinecraftDayTime.stringifyDayTime(time);
        assertEquals(expected, str);
    }

    @ParameterizedTest
    @ValueSource(strings = {"21:50", "21:51", "19:43", "00:29", "23:59", "23:01", "04:17", "08:37", "16:07"})
    void parseStringify_bijective(String str) {
        int time = MinecraftDayTime.parseDayTime(str).orElseThrow();
        String reverted = MinecraftDayTime.stringifyDayTime(time);
        assertEquals(str, reverted);
    }
}
package work.lclpnet.ap2.game.guess_it.math;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.ap2.game.guess_it.math.Term.*;

class TermTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 99, -90})
    void num_eval(int n) {
        var t = num(n);
        assertEquals(n, t.evaluate());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 99, -90})
    void num_str(int n) {
        var t = num(n);
        assertEquals("" + n, t.stringify());
    }

    @ParameterizedTest
    @CsvSource({"1,2,3", "2,3,5", "3,4,7", "10,921,931"})
    void add_eval(int a, int b, int c) {
        var t = add(num(a), num(b));
        assertEquals(c, t.evaluate());
    }

    @ParameterizedTest
    @CsvSource({"1,2,1 + 2", "2,3,2 + 3", "3,4,3 + 4", "10,921,10 + 921"})
    void add_str(int a, int b, String s) {
        var t = add(num(a), num(b));
        assertEquals(s, t.stringify());
    }

    @ParameterizedTest
    @CsvSource({"3,2,1", "5,3,2", "7,4,3", "931,921,10"})
    void sub_eval(int a, int b, int c) {
        var t = sub(num(a), num(b));
        assertEquals(c, t.evaluate());
    }

    @ParameterizedTest
    @CsvSource({"3,2,3 - 2", "5,3,5 - 3", "7,4,7 - 4", "931,921,931 - 921"})
    void sub_str(int a, int b, String s) {
        var t = sub(num(a), num(b));
        assertEquals(s, t.stringify());
    }

    @ParameterizedTest
    @CsvSource({"3,2,6", "5,3,15", "7,4,28", "931,921,857451"})
    void mul_eval(int a, int b, int c) {
        var t = mul(num(a), num(b));
        assertEquals(c, t.evaluate());
    }

    @ParameterizedTest
    @CsvSource({"3,2,3 × 2", "5,3,5 × 3", "7,4,7 × 4", "931,921,931 × 921"})
    void mul_str(int a, int b, String s) {
        var t = mul(num(a), num(b));
        assertEquals(s, t.stringify());
    }

    @ParameterizedTest
    @CsvSource({"6,3,2", "15,5,3", "28,4,7", "857451,931,921"})
    void div_eval(int a, int b, int c) {
        var t = div(num(a), num(b));
        assertEquals(c, t.evaluate());
    }

    @ParameterizedTest
    @CsvSource({"6,3,6 ÷ 3", "15,5,15 ÷ 5", "28,4,28 ÷ 4", "857451,931,857451 ÷ 931"})
    void div_eval(int a, int b, String s) {
        var t = div(num(a), num(b));
        assertEquals(s, t.stringify());
    }

    @Test
    void add_mul() {
        var t = add(num(5), mul(num(2), num(3)));
        assertEquals(11, t.evaluate());
        assertEquals("5 + 2 × 3", t.stringify());
    }

    @Test
    void mul_add() {
        var t = mul(num(5), add(num(2), num(3)));
        assertEquals(25, t.evaluate());
        assertEquals("5 × (2 + 3)", t.stringify());
    }

    @Test
    void add_sub() {
        var t = add(num(5), sub(num(6), num(1)));
        assertEquals(10, t.evaluate());
        assertEquals("5 + 6 - 1", t.stringify());
    }

    @Test
    void sub_add() {
        var t = sub(num(5), add(num(6), num(1)));
        assertEquals(-2, t.evaluate());
        assertEquals("5 - (6 + 1)", t.stringify());
    }

    @Test
    void add_add() {
        var t = add(num(5), add(num(6), num(1)));
        assertEquals(12, t.evaluate());
        assertEquals("5 + 6 + 1", t.stringify());
    }

    @Test
    void sub_sub() {
        var t = sub(num(5), sub(num(6), num(1)));
        assertEquals(0, t.evaluate());
        assertEquals("5 - (6 - 1)", t.stringify());
    }

    @Test
    void sub_sub2() {
        var t = sub(sub(num(5), num(6)), num(1));
        assertEquals(-2, t.evaluate());
        assertEquals("5 - 6 - 1", t.stringify());
    }

    @Test
    void mul_mul() {
        var t = mul(num(5), mul(num(6), num(1)));
        assertEquals(30, t.evaluate());
        assertEquals("5 × 6 × 1", t.stringify());
    }

    @Test
    void div_div() {
        var t = div(div(num(30), num(5)), num(2));
        assertEquals(3, t.evaluate());
        assertEquals("30 ÷ 5 ÷ 2", t.stringify());
    }

    @Test
    void add_negative() {
        var t = add(num(5), num(-2));
        assertEquals(3, t.evaluate());
        assertEquals("5 + (-2)", t.stringify());
    }
}
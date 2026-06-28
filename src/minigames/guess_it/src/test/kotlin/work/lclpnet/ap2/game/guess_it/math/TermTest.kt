package work.lclpnet.ap2.game.guess_it.math

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class TermTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 99, -90])
    fun num_eval(n: Int) {
        val t = Term.num(n)
        Assertions.assertEquals(n, t.evaluate())
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 99, -90])
    fun num_str(n: Int) {
        val t = Term.num(n)
        Assertions.assertEquals("" + n, t.stringify())
    }

    @ParameterizedTest
    @CsvSource("1,2,3", "2,3,5", "3,4,7", "10,921,931")
    fun add_eval(a: Int, b: Int, c: Int) {
        val t = Term.add(Term.num(a), Term.num(b))
        Assertions.assertEquals(c, t.evaluate())
    }

    @ParameterizedTest
    @CsvSource("1,2,1 + 2", "2,3,2 + 3", "3,4,3 + 4", "10,921,10 + 921")
    fun add_str(a: Int, b: Int, s: String?) {
        val t = Term.add(Term.num(a), Term.num(b))
        Assertions.assertEquals(s, t.stringify())
    }

    @ParameterizedTest
    @CsvSource("3,2,1", "5,3,2", "7,4,3", "931,921,10")
    fun sub_eval(a: Int, b: Int, c: Int) {
        val t = Term.sub(Term.num(a), Term.num(b))
        Assertions.assertEquals(c, t.evaluate())
    }

    @ParameterizedTest
    @CsvSource("3,2,3 - 2", "5,3,5 - 3", "7,4,7 - 4", "931,921,931 - 921")
    fun sub_str(a: Int, b: Int, s: String?) {
        val t = Term.sub(Term.num(a), Term.num(b))
        Assertions.assertEquals(s, t.stringify())
    }

    @ParameterizedTest
    @CsvSource("3,2,6", "5,3,15", "7,4,28", "931,921,857451")
    fun mul_eval(a: Int, b: Int, c: Int) {
        val t = Term.mul(Term.num(a), Term.num(b))
        Assertions.assertEquals(c, t.evaluate())
    }

    @ParameterizedTest
    @CsvSource("3,2,3 × 2", "5,3,5 × 3", "7,4,7 × 4", "931,921,931 × 921")
    fun mul_str(a: Int, b: Int, s: String?) {
        val t = Term.mul(Term.num(a), Term.num(b))
        Assertions.assertEquals(s, t.stringify())
    }

    @ParameterizedTest
    @CsvSource("6,3,2", "15,5,3", "28,4,7", "857451,931,921")
    fun div_eval(a: Int, b: Int, c: Int) {
        val t = Term.div(Term.num(a), Term.num(b))
        Assertions.assertEquals(c, t.evaluate())
    }

    @ParameterizedTest
    @CsvSource("6,3,6 ÷ 3", "15,5,15 ÷ 5", "28,4,28 ÷ 4", "857451,931,857451 ÷ 931")
    fun div_eval(a: Int, b: Int, s: String?) {
        val t = Term.div(Term.num(a), Term.num(b))
        Assertions.assertEquals(s, t.stringify())
    }

    @Test
    fun add_mul() {
        val t = Term.add(Term.num(5), Term.mul(Term.num(2), Term.num(3)))
        Assertions.assertEquals(11, t.evaluate())
        Assertions.assertEquals("5 + 2 × 3", t.stringify())
    }

    @Test
    fun mul_add() {
        val t = Term.mul(Term.num(5), Term.add(Term.num(2), Term.num(3)))
        Assertions.assertEquals(25, t.evaluate())
        Assertions.assertEquals("5 × (2 + 3)", t.stringify())
    }

    @Test
    fun add_sub() {
        val t = Term.add(Term.num(5), Term.sub(Term.num(6), Term.num(1)))
        Assertions.assertEquals(10, t.evaluate())
        Assertions.assertEquals("5 + 6 - 1", t.stringify())
    }

    @Test
    fun sub_add() {
        val t = Term.sub(Term.num(5), Term.add(Term.num(6), Term.num(1)))
        Assertions.assertEquals(-2, t.evaluate())
        Assertions.assertEquals("5 - (6 + 1)", t.stringify())
    }

    @Test
    fun add_add() {
        val t = Term.add(Term.num(5), Term.add(Term.num(6), Term.num(1)))
        Assertions.assertEquals(12, t.evaluate())
        Assertions.assertEquals("5 + 6 + 1", t.stringify())
    }

    @Test
    fun sub_sub() {
        val t = Term.sub(Term.num(5), Term.sub(Term.num(6), Term.num(1)))
        Assertions.assertEquals(0, t.evaluate())
        Assertions.assertEquals("5 - (6 - 1)", t.stringify())
    }

    @Test
    fun sub_sub2() {
        val t = Term.sub(Term.sub(Term.num(5), Term.num(6)), Term.num(1))
        Assertions.assertEquals(-2, t.evaluate())
        Assertions.assertEquals("5 - 6 - 1", t.stringify())
    }

    @Test
    fun mul_mul() {
        val t = Term.mul(Term.num(5), Term.mul(Term.num(6), Term.num(1)))
        Assertions.assertEquals(30, t.evaluate())
        Assertions.assertEquals("5 × 6 × 1", t.stringify())
    }

    @Test
    fun div_div() {
        val t = Term.div(Term.div(Term.num(30), Term.num(5)), Term.num(2))
        Assertions.assertEquals(3, t.evaluate())
        Assertions.assertEquals("30 ÷ 5 ÷ 2", t.stringify())
    }

    @Test
    fun add_negative() {
        val t = Term.add(Term.num(5), Term.num(-2))
        Assertions.assertEquals(3, t.evaluate())
        Assertions.assertEquals("5 + (-2)", t.stringify())
    }
}
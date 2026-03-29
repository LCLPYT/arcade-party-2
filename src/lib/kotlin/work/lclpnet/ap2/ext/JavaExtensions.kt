package work.lclpnet.ap2.ext

import java.util.*

fun UUID.toUndashedString(): String = this.toString().replace("-", "")

fun uuidFromUndashedString(str: String): UUID {
    if (str.length != 32) error("Expected hex string of length 32")

    val sb = buildString {
        append(str.substring(0, 8))
        append('-')
        append(str.substring(8, 12))
        append('-')
        append(str.substring(12, 16))
        append('-')
        append(str.substring(16, 20))
        append('-')
        append(str.substring(20, 32))
    }

    return UUID.fromString(sb)
}
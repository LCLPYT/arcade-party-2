package work.lclpnet.ap2.impl.data

fun interface DynamicData {
    operator fun get(key: String): Any?
}

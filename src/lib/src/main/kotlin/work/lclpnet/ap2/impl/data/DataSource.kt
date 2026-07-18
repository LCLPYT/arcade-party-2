package work.lclpnet.ap2.impl.data

fun interface DataSource {
    fun load(consumer: (String, Any) -> Unit)
}

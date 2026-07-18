package work.lclpnet.ap2.impl.data

import com.google.common.collect.ImmutableMap
import java.util.*

class MapDynamicData(
    private val entries: Map<String, Any>
) : DynamicData {

    override operator fun get(key: String): Any? =
        entries[key]

    class Builder {

        private val sources = ArrayList<DataSource>()

        fun addSource(source: DataSource): Builder {
            sources.add(source)
            return this
        }

        fun build(): MapDynamicData {
            val builder = ImmutableMap.builder<String, Any>()

            for (i in sources.indices.reversed()) {
                val source = sources[i]

                source.load(builder::put)
            }

            return MapDynamicData(builder.build())
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}

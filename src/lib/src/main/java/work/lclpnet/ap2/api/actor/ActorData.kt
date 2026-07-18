package work.lclpnet.ap2.api.actor

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps

@JvmRecord
data class ActorData<D>(val data: D, val codec: Codec<D>) {
    fun <T> encode(ops: DynamicOps<T>, prefix: T): DataResult<T> =
        codec.encode<T>(data, ops, prefix)
}

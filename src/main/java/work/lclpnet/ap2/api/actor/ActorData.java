package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

public record ActorData<D>(D data, Codec<D> codec) {

    public <T> DataResult<T> encode(DynamicOps<T> ops, T prefix) {
        return codec.encode(data, ops, prefix);
    }
}

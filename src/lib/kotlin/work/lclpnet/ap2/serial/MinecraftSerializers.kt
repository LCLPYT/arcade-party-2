package work.lclpnet.ap2.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.world.phys.Vec3

object Vec3Serializer : KSerializer<Vec3> {
    private val delegateSerializer = DoubleArraySerializer()

    override val descriptor: SerialDescriptor = SerialDescriptor(
        "net.minecraft.world.phys.Vec3",
        delegateSerializer.descriptor
    )

    override fun serialize(encoder: Encoder, value: Vec3) {
        encoder.encodeSerializableValue(delegateSerializer, doubleArrayOf(value.x, value.y, value.z))
    }

    override fun deserialize(decoder: Decoder): Vec3 {
        val array = decoder.decodeSerializableValue(delegateSerializer)

        require(array.size == 3)

        return Vec3(array[0], array[1], array[2])
    }
}

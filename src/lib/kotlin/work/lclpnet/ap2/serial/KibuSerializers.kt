package work.lclpnet.ap2.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.minecraft.world.phys.Vec3
import work.lclpnet.kibu.hook.util.PositionRotation

object PositionRotationSerializer : KSerializer<PositionRotation> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("work.lclpnet.kibu.hook.util.PositionRotation") {
            element("pos", Vec3Serializer.descriptor)
            element<Float>("yaw")
            element<Float>("pitch")
        }

    override fun serialize(
        encoder: Encoder,
        value: PositionRotation
    ) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                Vec3Serializer.descriptor,
                0,
                Vec3Serializer,
                Vec3(value.x(), value.y(), value.z())
            )
            encodeFloatElement(descriptor, 1, value.yaw)
            encodeFloatElement(descriptor, 2, value.pitch)
        }
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var pos: Vec3? = null
        var yaw = 0f
        var pitch = 0f

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> pos = decodeSerializableElement(Vec3Serializer.descriptor, 0, Vec3Serializer)
                1 -> yaw = decodeFloatElement(descriptor, 1)
                2 -> pitch = decodeFloatElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }

        requireNotNull(pos) { "pos is required" }
        require(!yaw.isNaN() && yaw.isFinite()) { "Invalid yaw" }
        require(!pitch.isNaN() && pitch.isFinite()) { "Invalid pitch" }

        PositionRotation(pos.x, pos.y, pos.z, yaw, pitch)
    }
}

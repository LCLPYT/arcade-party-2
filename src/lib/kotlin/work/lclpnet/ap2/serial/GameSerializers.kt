package work.lclpnet.ap2.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.centered
import work.lclpnet.kibu.hook.util.PositionRotation

object CenteredVec3Serializer : KSerializer<Vec3> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("work.lclpnet.ap2.serial.CenteredVec3", Vec3Serializer.descriptor)

    override fun serialize(encoder: Encoder, value: Vec3) {
        Vec3Serializer.serialize(encoder, value.centered())
    }

    override fun deserialize(decoder: Decoder) =
        Vec3Serializer.deserialize(decoder).centered()
}

object CenteredPositionRotationSerializer : KSerializer<PositionRotation> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor(
            "work.lclpnet.ap2.serial.CenteredPositionRotation",
            PositionRotationSerializer.descriptor
        )

    override fun serialize(encoder: Encoder, value: PositionRotation) {
        PositionRotationSerializer.serialize(encoder, value.centered())
    }

    override fun deserialize(decoder: Decoder) =
        PositionRotationSerializer.deserialize(decoder).centered()
}

typealias CenteredVec3 = @Serializable(with = CenteredVec3Serializer::class) Vec3
typealias CenteredPositionRotation = @Serializable(with = CenteredPositionRotationSerializer::class) PositionRotation
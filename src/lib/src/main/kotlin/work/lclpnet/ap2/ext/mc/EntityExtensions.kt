package work.lclpnet.ap2.ext.mc

import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.ActivityData
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.Attribute
import work.lclpnet.kibu.access.entity.EntityUtil
import work.lclpnet.kibu.hook.util.PositionRotation

fun <T : LivingEntity> Brain<T>.addActivity(customFight: ActivityData<in T>) {
    addActivity(
        customFight.activityType(),
        customFight.behaviorPriorityPairs(),
        customFight.conditions(),
        customFight.memoriesToEraseWhenStopped()
    )
}

fun LivingEntity.setAttribute(attribute: Holder<Attribute>, value: Double) =
    EntityUtil.setAttribute(this, attribute, value)

fun LivingEntity.resetAttribute(attribute: Holder<Attribute>) =
    EntityUtil.resetAttribute(this, attribute)

fun Entity.teleport(level: ServerLevel, pos: PositionRotation) = teleportTo(
    level,
    pos.x(),
    pos.y(),
    pos.z(),
    emptySet(),
    pos.yaw,
    pos.pitch,
    true
)

fun Entity.teleportTo(other: Entity) {
    val level = other.level() as? ServerLevel ?: return

    teleportTo(
        level,
        other.x,
        other.y,
        other.z,
        emptySet(),
        other.xRot,
        other.yRot,
        true
    )
}
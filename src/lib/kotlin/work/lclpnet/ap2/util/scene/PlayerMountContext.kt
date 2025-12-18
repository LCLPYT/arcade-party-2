package work.lclpnet.ap2.util.scene

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import work.lclpnet.gaco.core.api.Resolvable
import work.lclpnet.gaco.dynamic_entities.DynamicEntity
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.dynamic_entities.PlayerSpecificDynamicEntity
import work.lclpnet.gaco.scene.MountContext
import work.lclpnet.gaco.scene.Object3d
import java.util.*

class PlayerMountContext(
    val world: ServerLevel,
    val dynamicEntityManager: DynamicEntityManager,
    val viewerUuid: UUID
) : MountContext {

    val mapping = mutableMapOf<Entity, DynamicEntity>()

    override fun world() = world

    @Synchronized
    override fun <T : Entity> spawn(entity: T?, origin: Object3d?): Resolvable<T?> {
        if (entity == null) return Resolvable.none()

        val dynamicEntity = PlayerSpecificDynamicEntity(entity, viewerUuid)

        mapping[entity] = dynamicEntity

        dynamicEntityManager.add(dynamicEntity)

        return Resolvable.constant(entity)
    }

    @Synchronized
    override fun <T : Entity?> remove(entity: T?, origin: Object3d?) {
        if (entity == null) return

        val dynamicEntity = mapping.remove(entity) ?: return

        dynamicEntityManager.remove(dynamicEntity)
    }
}
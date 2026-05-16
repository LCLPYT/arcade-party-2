package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import work.lclpnet.gaco.collisions.UnionCollider
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.Collider
import work.lclpnet.kibu.structure.BlockStructure
import java.util.UUID

data class CCBase(
    val bounds: Collider,
    val campfirePos: BlockPos,
    val entityUuid: UUID,
    val doorSchematic: BlockStructure?,
    val doorPos: BlockPos?
) {
    constructor(
        bounds: List<BlockBox>,
        campfirePos: BlockPos,
        entityUuid: UUID,
        doorSchematic: BlockStructure?,
        doorPos: BlockPos?
    ) : this(
        UnionCollider(bounds.toTypedArray()),
        campfirePos,
        entityUuid,
        doorSchematic,
        doorPos
    )

    fun isEntity(entity: Entity) = entityUuid == entity.uuid
    fun isInside(x: Double, y: Double, z: Double) = bounds.collidesWith(x, y, z)
}

package work.lclpnet.ap2.game.guess_it.util

import work.lclpnet.gaco.dynamic_entities.DynamicEntity
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager

class DynamicEntityModifier(private val dynamicEntityManager: DynamicEntityManager) {
    private val dynamicEntities = HashSet<DynamicEntity>()

    @Synchronized
    fun spawn(entity: DynamicEntity) {
        dynamicEntities.add(entity)
        dynamicEntityManager.add(entity)
    }

    @Synchronized
    fun reset() {
        for (entity in dynamicEntities) {
            dynamicEntityManager.remove(entity)
        }

        dynamicEntities.clear()
    }
}

package work.lclpnet.ap2.game.guess_it.util;

import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;

import java.util.HashSet;
import java.util.Set;

public class DynamicEntityModifier {

    private final DynamicEntityManager dynamicEntityManager;
    private final Set<DynamicEntity> dynamicEntities = new HashSet<>();

    public DynamicEntityModifier(DynamicEntityManager dynamicEntityManager) {
        this.dynamicEntityManager = dynamicEntityManager;
    }

    public synchronized void spawn(DynamicEntity entity) {
        dynamicEntities.add(entity);
        dynamicEntityManager.add(entity);
    }

    public synchronized void reset() {
        dynamicEntities.forEach(dynamicEntityManager::remove);
        dynamicEntities.clear();
    }
}

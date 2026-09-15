package work.lclpnet.ap2.core.type;

import work.lclpnet.ap2.impl.ai.PathFindingPredicate;

public interface ApLandPathNodeMaker {

    void ap2$addCustomBlockedPredicate(PathFindingPredicate predicate);

    void ap2$addCustomInvalidPredicate(PathFindingPredicate predicate);
}

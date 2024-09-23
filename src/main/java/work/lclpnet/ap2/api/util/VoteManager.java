package work.lclpnet.ap2.api.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

public interface VoteManager<T> {

    Object2IntMap<T> votes();
}

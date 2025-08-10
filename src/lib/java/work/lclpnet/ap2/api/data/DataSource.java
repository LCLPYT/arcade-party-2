package work.lclpnet.ap2.api.data;

import java.util.function.BiConsumer;

public interface DataSource {

    void load(BiConsumer<String, Object> consumer);
}

package work.lclpnet.ap2.impl.data;

import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.data.DataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class JsonDataSource implements DataSource {

    private final Supplier<InputStream> inputProvider;
    private final Logger logger;

    public JsonDataSource(Supplier<InputStream> inputProvider, Logger logger) {
        this.inputProvider = inputProvider;
        this.logger = logger;
    }

    @Override
    public void load(BiConsumer<String, Object> consumer) {
        JSONObject json;

        try (var in = inputProvider.get()) {
            byte[] bytes = in.readAllBytes();
            String str = new String(bytes, StandardCharsets.UTF_8);

            json = new JSONObject(str);
        } catch (Exception e) {
            logger.error("Failed to read input stream", e);
            return;
        }

        var it = json.keys();

        while (it.hasNext()) {
            String key = it.next();
            Object val = json.get(key);

            consumer.accept(key, val);
        }
    }
}

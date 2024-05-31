package work.lclpnet.ap2.impl.data;

import com.google.common.collect.ImmutableMap;
import work.lclpnet.ap2.api.data.DataSource;
import work.lclpnet.ap2.api.data.DynamicData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapDynamicData implements DynamicData {

    private final Map<String, Object> entries;

    public MapDynamicData(Map<String, Object> entries) {
        this.entries = entries;
    }

    @Override
    public Object get(String key) {
        return entries.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() {}

        private final List<DataSource> sources = new ArrayList<>();

        public Builder addSource(DataSource source) {
            Objects.requireNonNull(source, "Source cannot be null");
            sources.add(source);
            return this;
        }

        public MapDynamicData build() {
            var builder = ImmutableMap.<String, Object>builder();

            for (int i = sources.size() - 1; i >= 0; i--) {
                DataSource source = sources.get(i);

                source.load(builder::put);
            }

            return new MapDynamicData(builder.build());
        }
    }
}

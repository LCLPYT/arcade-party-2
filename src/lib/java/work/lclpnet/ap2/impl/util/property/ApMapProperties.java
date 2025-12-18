package work.lclpnet.ap2.impl.util.property;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ApMapProperties {

    public static final ResourceLocation ALLOW_BLOCK_INTERACTION = ApConstants.identifier("allow-block-interaction");

    private final Map<ResourceLocation, Object> map = new HashMap<>();

    public void set(ResourceLocation id, Object value) {
        Objects.requireNonNull(id, "Id must not be null");

        map.put(id, value);
    }

    public boolean has(ResourceLocation id) {
        return map.containsKey(id);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(ResourceLocation id, Class<T> type) {
        Object o = map.get(id);

        if (o == null || !type.isAssignableFrom(o.getClass())) return null;

        return (T) o;
    }

    public boolean getBoolean(ResourceLocation id, boolean defaultValue) {
        Object o = map.get(id);

        if (o instanceof Boolean bool) {
            return bool;
        }

        if (o instanceof String str) {
            if (str.equalsIgnoreCase("false")) return false;
            if (str.equalsIgnoreCase("true")) return true;
        }

        return defaultValue;
    }
}

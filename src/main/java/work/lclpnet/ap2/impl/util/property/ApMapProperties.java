package work.lclpnet.ap2.impl.util.property;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.base.ArcadeParty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ApMapProperties {

    public static final Identifier ALLOW_BLOCK_INTERACTION = ArcadeParty.identifier("allow-block-interaction");

    private final Map<Identifier, Object> map = new HashMap<>();

    public void set(Identifier id, Object value) {
        Objects.requireNonNull(id, "Id must not be null");

        map.put(id, value);
    }

    public boolean has(Identifier id) {
        return map.containsKey(id);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(Identifier id, Class<T> type) {
        Object o = map.get(id);

        if (o == null || !type.isAssignableFrom(o.getClass())) return null;

        return (T) o;
    }

    public boolean getBoolean(Identifier id, boolean defaultValue) {
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

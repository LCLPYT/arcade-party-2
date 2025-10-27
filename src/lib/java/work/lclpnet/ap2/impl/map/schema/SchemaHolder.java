package work.lclpnet.ap2.impl.map.schema;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SchemaHolder<T> {

    @Getter
    private final Class<T> schemaClass;
    private @Nullable T instance = null;

    public SchemaHolder(Class<T> schemaClass) {
        this.schemaClass = schemaClass;
    }

    public void set(T instance) {
        this.instance = instance;
    }

    public T get() {
        return Objects.requireNonNull(instance, "Instance not loaded yet");
    }
}

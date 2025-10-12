package work.lclpnet.ap2.impl.map.schema;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.ReflectionUtil;
import work.lclpnet.map_api.GameMapApi;
import work.lclpnet.map_api.data.Data;
import work.lclpnet.map_api.data.DataInstance;
import work.lclpnet.map_api.data.DataManagerKt;
import work.lclpnet.map_api.data.WorldData;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class MapSchemaLoader {

    private final Logger logger;
    private final Map<Class<?>, Data<?>> dataByClass = new HashMap<>();

    public MapSchemaLoader(Logger logger) {
        this.logger = logger;

        for (var data : DataManagerKt.getDATA_TYPES().values()) {
            this.dataByClass.put(data.type(), data);
        }
    }

    @Nullable
    public <T> T load(ServerWorld world, Class<T> type) {
        T instance = makeInstance(type);

        if (instance == null) {
            return null;
        }

        loadHierarchy(world, instance);

        return instance;
    }

    @Nullable
    private <T> T makeInstance(Class<T> type) {
        try {
            var ctor = type.getConstructor();
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to make instance of {}", type.getName());
            return null;
        }
    }

    public void loadHierarchy(ServerWorld world, Object instance) {
        var api = GameMapApi.Companion.get(world.getServer());
        WorldData worldData = api.getDataManager().getWorldData(world);

        for (var type : ReflectionUtil.iterateHierarchy(instance.getClass())) {
            loadClass(type, instance, worldData);
        }
    }

    private void loadClass(Class<?> classType, Object instance, WorldData worldData) {
        for (Field field : classType.getDeclaredFields()) {
            Property property = field.getAnnotation(Property.class);

            if (property == null) continue;

            Class<?> fieldType = field.getType();

            if (List.class.isAssignableFrom(fieldType)) {
                loadListField(field, property, instance, worldData);
                continue;
            }

            loadSingleField(field, property, instance, worldData);
        }
    }

    private void loadSingleField(Field field, Property property, Object instance, WorldData worldData) {
        String propertyId = field.getName();

        DataInstance<?> dataInstance = worldData.get(propertyId);
        Object value = dataInstance != null ? dataInstance.getValue() : null;

        if (dataInstance == null || value == null) {
            if (property.optional()) return;

            throw new NoSuchElementException("Missing required property \"%s\"".formatted(propertyId));
        }

        if (!field.getType().isInstance(value)) {
            throw new ClassCastException("Value of type %s is not assignable to property \"%s\" of type %s"
                    .formatted(value.getClass().getName(), propertyId, field.getType().getName()));
        }

        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set property \"%s\"".formatted(propertyId), e);
        }
    }

    private void loadListField(Field field, Property property, Object instance, WorldData worldData) {
        String role = getRole(field);

        if (role == null) {
            role = field.getName();
        }

        var type = ReflectionUtil.getGenericType(field, logger);

        if (type == null) {
            if (property.optional()) return;
            throw new RuntimeException("Failed to determine list item type of property \"%s\"".formatted(field.getName()));
        }

        var data = dataByClass.get(type);

        if (data == null) {
            String err = "No data registered for " + type.getName() + " (field \"%s\")".formatted(field.getName());

            if (property.optional()) {
                logger.error(err);
                return;
            }

            throw new RuntimeException(err);
        }

        var list = worldData.byRole(role, data).stream()
                .map(DataInstance::getValue)
                .toList();

        try {
            field.setAccessible(true);
            field.set(instance, list);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set property \"%s\"".formatted(field.getName()), e);
        }
    }

    @Nullable
    private String getRole(Field field) {
        Role role = field.getAnnotation(Role.class);

        if (role == null) return null;

        return role.value();
    }
}

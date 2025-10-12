package work.lclpnet.ap2.impl.map.schema;

import com.google.common.collect.AbstractIterator;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.ReflectionUtil;
import work.lclpnet.map_api.data.Data;
import work.lclpnet.map_api.data.DataManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MapSchemaGenerator {

    private final String packagePrefix;
    private final Path outputDir;
    private final ClassLoader classLoader;
    private final Logger logger;
    private final Map<Class<?>, Data<?>> dataByClass = new HashMap<>();

    public MapSchemaGenerator(String packagePrefix, Path outputDir, ClassLoader classLoader, Logger logger) {
        this.packagePrefix = packagePrefix;
        this.outputDir = outputDir;
        this.classLoader = classLoader;
        this.logger = logger;

        for (var data : DataManager.Companion.getDATA_TYPES().values()) {
            this.dataByClass.put(data.type(), data);
        }
    }

    @Nullable
    private String dataId(Class<?> type) {
        Data<?> data = dataByClass.get(type);

        if (data == null) return null;

        return data.id();
    }

    @Nullable
    private Codec<?> codec(Class<?> type) {
        Data<?> data = dataByClass.get(type);

        if (data == null) return null;

        return data.codec();
    }

    public void generate() {
        for (Class<?> c : findClasses()) {
            MapSchema mapSchema = c.getAnnotation(MapSchema.class);

            if (mapSchema == null) continue;

            processSchema(c, mapSchema);
        }
    }

    public Iterable<Class<?>> findClasses() {
        return () -> {
            List<ClassInfo> classes;

            try {
                classes = ClassPath.from(classLoader).getAllClasses()
                        .parallelStream()
                        .filter(classInfo -> classInfo.getName().startsWith(packagePrefix))
                        .toList();
            } catch (IOException e) {
                logger.error("Failed to find all classes", e);
                classes = List.of();
            }

            var it = classes.stream().iterator();

            return new AbstractIterator<>() {

                @Override
                protected Class<?> computeNext() {
                    while (it.hasNext()) {
                        ClassInfo info = it.next();

                        try {
                            return classLoader.loadClass(info.getName());
                        } catch (Throwable t) {
                            logger.debug("Failed to load class {}", info.getName(), t);
                        }
                    }

                    endOfData();
                    return null;
                }
            };
        };
    }

    private void processSchema(@NotNull Class<?> rootType, MapSchema info) {
        logger.info("Processing schema \"{}\" ...", schemaId(info));

        JSONObject schema = new JSONObject();
        schema.put("name", info.name());

        JSONObject properties = new JSONObject();

        List<Class<?>> hierarchy = new ArrayList<>();

        for (var type : ReflectionUtil.iterateHierarchy(rootType)) {
            hierarchy.add(type);
        }

        for (var type : hierarchy.reversed()) {
            visit(type, properties);
        }

        schema.put("properties", properties);

        writeSchema(schema, info);
    }

    private void visit(Class<?> type, JSONObject schema) {
        Object instance = makeInstance(type);

        record PropField(Field field, Property property) {}

        List<PropField> fields = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            Property property = field.getAnnotation(Property.class);

            if (property == null) continue;

            fields.add(new PropField(field, property));
        }

        fields.sort(Comparator.comparingInt(p -> p.property.ordinal()));

        for (PropField propField : fields) {
            Field field = propField.field;
            String name = field.getName();

            if (schema.has(name)) continue;  // overridden by child class

            Object value = instance != null ? getValue(field, instance) : null;

            JSONObject json = toSchema(field, propField.property, value);

            if (json == null) continue;

            schema.put(name, json);
        }
    }

    @Nullable
    private Object getValue(Field field, Object instance){
        try {
            field.setAccessible(true);

            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to get default value of field {}", field, e);
            return null;
        }
    }

    private Object makeInstance(Class<?> type) {
        try {
            var ctor = type.getConstructor();

            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to create instance of {}", type, e);
            return null;
        }
    }

    @Nullable
    private JSONObject toSchema(Field field, Property property, @Nullable Object value) {
        JSONObject json = new JSONObject();
        json.put("name", property.name());

        if (property.optional()) {
            json.put("optional", true);
        }

        Role role = field.getAnnotation(Role.class);

        if (role != null) {
            json.put("role", role.value());
        }

        Class<?> type = field.getType();
        Class<?> dataType = getDataType(field, type);

        if (dataType == null) return null;

        String dataId = dataId(dataType);

        if (dataId == null) {
            logger.error("Unsupported data type: {}", dataType.getName());
            return null;
        }

        if (List.class.isAssignableFrom(type)) {
            json.put("type", "list");
            json.put("items", dataId);
        } else {
            json.put("type", dataId);
        }

        Codec<?> codec = codec(dataType);

        if (value != null && codec != null) {
            if (List.class.isAssignableFrom(type)) {
                codec = codec.listOf();
            }

            if (codec == null) {
                logger.error("Cannot encode default value for field {}: No codec registered for data type {}", field, dataType.getName());
            } else {
                Object valueJson = encodeDefaultValue(codec, value);

                if (valueJson != null) {
                    json.put("default", valueJson);
                }
            }
        }

        return json;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> Object encodeDefaultValue(Codec<T> codec, Object value) {
        return codec.encodeStart(JsonOps.INSTANCE, (T) value)
                .resultOrPartial(err -> logger.error("Failed to encode default value: {}", err))
                .map(this::convertJson)
                .orElse(null);
    }

    @Nullable
    private Object convertJson(JsonElement elem) {
        return switch (elem) {
            case JsonObject json -> new JSONObject(json);
            case JsonArray json -> new JSONArray(json);
            case JsonPrimitive json -> {
                if (json.isBoolean()) yield json.getAsBoolean();
                if (json.isNumber()) yield json.getAsNumber();
                if (json.isString()) yield json.getAsString();

                yield null;
            }
            case null, default -> null;
        };
    }

    @Nullable
    private Class<?> getDataType(Field field, Class<?> type) {
        if (List.class.isAssignableFrom(type)) {
            return ReflectionUtil.getGenericType(field, logger);
        }

        return type;
    }

    private void writeSchema(JSONObject schema, MapSchema info) {
        Path path = outputDir.resolve(schemaId(info) + ".json");

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, schema.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to write schema json to {}", path, e);
            return;
        }

        logger.info("Wrote schema to \"{}\"", path);
    }

    private String schemaId(MapSchema schema) {
        return schema.namespace() + "/" + schema.id();
    }
}

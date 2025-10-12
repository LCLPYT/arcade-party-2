package work.lclpnet.ap2.impl.util;

import com.google.common.collect.AbstractIterator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

public class ReflectionUtil {

    @Nullable
    public static Class<?> getGenericType(Field field, Logger logger) {
        Type generic = field.getGenericType();

        if (!(generic instanceof ParameterizedType pt)) {
            logger.error("Failed to get generic type for field {} (unexpected generic type {})", field, generic);
            return null;
        }

        Type ga = pt.getActualTypeArguments()[0];

        if (ga instanceof Class<?> gac) {
            return gac;
        }

        logger.error("Failed to get generic argument class for field {} (unexpected generic type {})", field, ga);

        return null;
    }

    public static Iterable<Class<?>> iterateHierarchy(Class<?> type) {
        return () -> new AbstractIterator<>() {

            final Set<Class<?>> visited = new HashSet<>();
            Class<?> current = type;

            @Override
            protected Class<?> computeNext() {
                if (current == null || current == Object.class || !visited.add(current)) {
                    endOfData();
                    return null;
                }

                var ret = current;

                current = current.getSuperclass();

                return ret;
            }
        };
    }
}

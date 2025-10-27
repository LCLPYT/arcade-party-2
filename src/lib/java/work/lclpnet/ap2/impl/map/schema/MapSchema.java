package work.lclpnet.ap2.impl.map.schema;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MapSchema {

    String namespace();

    String id();

    String name();
}


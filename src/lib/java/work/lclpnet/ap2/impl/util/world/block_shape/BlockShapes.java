package work.lclpnet.ap2.impl.util.world.block_shape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;

import java.util.HashMap;
import java.util.Map;

public class BlockShapes {

    public static final Codec<BlockShape> CODEC = Type.CODEC.dispatch("type", BlockShape::type, Type::codec);

    public static final Type<BoxBlockShape>
            TYPE_BOX = register("box", BoxBlockShape.BOX_CODEC),
            TYPE_CUBE = register("cube", BoxBlockShape.CUBE_CODEC);

    public static final Type<CylinderBlockShape>
            TYPE_CIRCLE = register("circle", CylinderBlockShape.CODEC),
            TYPE_CYLINDER = register("cylinder", CylinderBlockShape.CODEC);

    public static <T extends BlockShape> Type<T> register(String id, MapCodec<T> shape) {
        var type = new Type<>(id, shape);

        Type.REGISTRY.put(id, type);

        return type;
    }

    private BlockShapes() {}

    public record Type<T extends BlockShape>(String id, MapCodec<T> codec) {

        private static final Map<String, Type<?>> REGISTRY = new HashMap<>();
        private static final Codec<Type<?>> CODEC = Codec.STRING.comapFlatMap(id -> {
            var type = REGISTRY.get(id);

            if (type == null) {
                return DataResult.error(() -> "Unknown shape type: " + id);
            }

            return DataResult.success(type, Lifecycle.stable());
        }, Type::id);
    }
}

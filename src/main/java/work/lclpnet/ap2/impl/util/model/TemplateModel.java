package work.lclpnet.ap2.impl.util.model;

import net.minecraft.block.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;

import java.util.Optional;

public record TemplateModel(Object3d template) implements Model {

    @Override
    public Object3d createInstance() {
        var wrapper = new Object3d();
        wrapper.addChild(template.deepCopy());
        return wrapper;
    }

    public TemplateModel copy() {
        return new TemplateModel(template.deepCopy());
    }

    public TemplateModel replace(BlockState from, BlockState to) {
        for (Object3d obj : template.traverse()) {
            if (obj instanceof BlockDisplayObject display && display.getBlockState() == from) {
                display.setBlockState(to);
            }
        }

        return this;
    }

    public static @NotNull TemplateModel replace(@Nullable Model model, BlockState from, BlockState to) {
        return Optional.ofNullable(model)
                .map(m -> m instanceof TemplateModel tm ? tm : null)
                .map(m -> m.copy().replace(from, to))
                .orElseThrow();
    }
}

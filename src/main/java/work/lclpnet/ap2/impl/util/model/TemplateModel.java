package work.lclpnet.ap2.impl.util.model;

import net.minecraft.block.BlockState;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.impl.scene.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.Object3d;

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
}

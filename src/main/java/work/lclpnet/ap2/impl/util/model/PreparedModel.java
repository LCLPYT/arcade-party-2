package work.lclpnet.ap2.impl.util.model;

import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.impl.scene.Object3d;

public class PreparedModel implements Model {

    private final Object3d model;

    public PreparedModel(Object3d model) {
        this.model = model;
    }

    @Override
    public Object3d createInstance() {
        var wrapper = new Object3d();
        wrapper.addChild(model.deepCopy());
        return wrapper;
    }
}

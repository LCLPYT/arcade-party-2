package work.lclpnet.ap2.api.util.model;

import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;

public interface Model {

    Object3d createInstance(Scene scene);
}

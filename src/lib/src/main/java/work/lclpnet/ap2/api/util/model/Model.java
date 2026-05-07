package work.lclpnet.ap2.api.util.model;

import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.gaco.scene.Scene;

public interface Model {

    Object3d createInstance(Scene scene);
}

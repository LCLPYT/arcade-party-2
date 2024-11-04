package work.lclpnet.ap2.api.util.model;

import net.minecraft.util.Identifier;

import java.util.Optional;

public interface ModelManager {

    Optional<Model> getModel(Identifier id);
}

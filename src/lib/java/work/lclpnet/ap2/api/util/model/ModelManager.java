package work.lclpnet.ap2.api.util.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface ModelManager {

    Optional<Model> getModel(ResourceLocation id);
}

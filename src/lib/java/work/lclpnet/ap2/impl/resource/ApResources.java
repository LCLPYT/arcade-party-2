package work.lclpnet.ap2.impl.resource;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.impl.util.model.TemplateModel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static work.lclpnet.ap2.ApConstants.logger;

public class ApResources implements ModelManager {

    private final FileToIdConverter MODEL_FINDER = new FileToIdConverter("function/model", ".mcfunction");
    private Map<ResourceLocation, TemplateModel> models = Map.of();

    public void reload(ResourceManager manager, HolderLookup.Provider lookup) {
        findModels(manager, lookup);
    }

    private void findModels(ResourceManager manager, HolderLookup.Provider lookup) {
        var resources = MODEL_FINDER.listMatchingResources(manager);
        var builder = ImmutableMap.<ResourceLocation, TemplateModel>builder();
        var modelLoader = new ModelLoader(lookup);

        for (var entry : resources.entrySet()) {
            ResourceLocation id = MODEL_FINDER.fileToId(entry.getKey());
            Resource res = entry.getValue();

            TemplateModel model;

            try (var in = res.open()) {
                model = modelLoader.load(in);
            } catch (IOException e) {
                logger.error("Failed to load model {} from data pack {}", id, res.sourcePackId());
                continue;
            }

            if (model == null) {
                logger.error("No objects found in model {} from data pack {}", id, res.sourcePackId());
                continue;
            }

            builder.put(id, model);
        }

        this.models = builder.build();
    }

    @Override
    public Optional<Model> getModel(ResourceLocation id) {
        return Optional.ofNullable(models.get(id));
    }

    public static ApResources getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final ApResources instance = new ApResources();
    }
}

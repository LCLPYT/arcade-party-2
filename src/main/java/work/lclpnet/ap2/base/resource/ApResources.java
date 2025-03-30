package work.lclpnet.ap2.base.resource;

import com.google.common.collect.ImmutableMap;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.impl.util.model.TemplateModel;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static work.lclpnet.ap2.base.ArcadeParty.logger;

public class ApResources implements ModelManager {

    private final ResourceFinder MODEL_FINDER = new ResourceFinder("function/model", ".mcfunction");
    private Map<Identifier, TemplateModel> models = Map.of();

    public void reload(ResourceManager manager, RegistryWrapper.WrapperLookup lookup) {
        findModels(manager, lookup);
//        findPlayerHeads(manager);
    }

    private void findModels(ResourceManager manager, RegistryWrapper.WrapperLookup lookup) {
        var resources = MODEL_FINDER.findResources(manager);
        var builder = ImmutableMap.<Identifier, TemplateModel>builder();
        var modelLoader = new ModelLoader(lookup);

        for (var entry : resources.entrySet()) {
            Identifier id = MODEL_FINDER.toResourceId(entry.getKey());
            Resource res = entry.getValue();

            TemplateModel model;

            try (var in = res.getInputStream()) {
                model = modelLoader.load(in);
            } catch (IOException e) {
                logger.error("Failed to load model {} from data pack {}", id, res.getPackId());
                continue;
            }

            if (model == null) {
                logger.error("No objects found in model {} from data pack {}", id, res.getPackId());
                continue;
            }

            builder.put(id, model);
        }

        this.models = builder.build();
    }

//    private void findPlayerHeads(ResourceManager manager) {
//        for (String namespace : manager.getAllNamespaces()) {
//            Resource res = manager.getResource(Identifier.of(namespace, PLAYER_HEADS_JSON)).orElse(null);
//
//            if (res == null) continue;
//
//            try (var in = res.getInputStream()) {
//                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
//                JSONObject json = new JSONObject(content);
//
//                for (String path : json.keySet()) {
//                    try {
//                        JSONObject headJson = json.getJSONObject(path);
//                        PlayerHead head = PlayerHead.fromJson(headJson);
//                        Identifier id = Identifier.of(namespace, path);
//
//                        Registry.register(playerHeads, id, head);
//                    } catch (JSONException e) {
//                        logger.error("Failed to parse player head {} from data pack {}", path, res.getPackId());
//                    }
//                }
//            } catch (IOException | JSONException e) {
//                logger.error("Failed to load {} from data pack {}", PLAYER_HEADS_JSON, res.getPackId());
//            }
//        }
//    }

    @Override
    public Optional<Model> getModel(Identifier id) {
        return Optional.ofNullable(models.get(id));
    }

    public static ApResources getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final ApResources instance = new ApResources();
    }
}

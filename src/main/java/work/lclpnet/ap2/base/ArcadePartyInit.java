package work.lclpnet.ap2.base;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.base.resource.ApResources;

public class ArcadePartyInit implements ModInitializer {

    public static final Identifier RESOURCES_ID = ArcadeParty.identifier("resources");

    @Override
    public void onInitialize() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(RESOURCES_ID, lookup -> new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return ArcadeParty.identifier("resources");
            }

            @Override
            public void reload(ResourceManager manager) {
                ApResources.getInstance().reload(manager, lookup);
            }
        });
    }
}

package work.lclpnet.ap2.base;

import com.mojang.serialization.Dynamic;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.actor.*;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.core.type.ActorManagerAccess;
import work.lclpnet.ap2.core.type.ApMarkerEntity;
import work.lclpnet.kibu.access.entity.MarkerEntityAccess;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;

import static work.lclpnet.ap2.base.ArcadeParty.logger;

public class ArcadePartyInit implements ModInitializer {

    public static final Identifier RESOURCES_ID = ArcadeParty.identifier("resources");
    public static final String ACTOR_NBT_KEY = "gca:actor";

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

        var actorRegistry = new ActorRegistry();

        FabricLoader.getInstance().invokeEntrypoints("actor_provider", ActorProvider.class,
                provider -> provider.provideActors(actorRegistry::register));

        ServerEntityHooks.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof MarkerEntity marker)) return;

            NbtCompound data = MarkerEntityAccess.getData(marker);
            NbtCompound actorData = data.getCompound(ACTOR_NBT_KEY);

            String type = actorData.getString("type");

            if (type.isEmpty()) return;

            Identifier actorId = Identifier.tryParse(type);

            if (actorId == null) return;

            actorRegistry.get(actorId).ifPresentOrElse(
                    factory -> createActor(world, marker, factory, actorData),
                    () -> logger.warn("Unknown actor type {} in world {} at {}", actorId, world.getRegistryKey().getValue(), marker.getBlockPos())
            );
        });

        ServerEntityHooks.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof MarkerEntity marker)) return;

            Actor actor = ((ApMarkerEntity) marker).ap2$getActor();

            if (actor == null) return;

            ActorManagerAccess.get(world).discard(actor, marker);
        });
    }

    private void createActor(ServerWorld world, MarkerEntity marker, ActorFactory<?> factory, NbtCompound data) {
        record Init(ServerWorld world, Dynamic<?> dataSource) implements ActorInit {}

        var dataSource = new Dynamic<>(NbtOps.INSTANCE, data);
        var init = new Init(world, dataSource);

        factory.create(init).ifPresent(actor -> ActorManagerAccess.get(world).spawn(actor, marker));
    }
}

package work.lclpnet.ap2;

import com.mojang.serialization.Dynamic;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Marker;
import work.lclpnet.ap2.api.actor.*;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.core.type.ActorManagerAccess;
import work.lclpnet.ap2.core.type.ApMarkerEntity;
import work.lclpnet.ap2.impl.resource.ApResources;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;

import static work.lclpnet.ap2.ApConstants.logger;

public class ArcadePartyInit implements ModInitializer {

    public static final Identifier RESOURCES_ID = ApConstants.identifier("resources");

    @Override
    public void onInitialize() {
        registerDynamicRegistries();

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(RESOURCES_ID, lookup -> new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RESOURCES_ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ApResources.getInstance().reload(manager, lookup);
            }
        });

        var actorRegistry = new ActorRegistry();

        FabricLoader.getInstance().invokeEntrypoints("actor_provider", ActorProvider.class,
                provider -> provider.provideActors(actorRegistry::register));

        ServerEntityHooks.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Marker marker)) return;

            ActorManager.ActorInfo data = ActorManager.getActorNbt(marker).orElse(null);

            if (data == null) return;

            actorRegistry.getType(data.type()).ifPresentOrElse(
                    type -> createActor(world, marker, type, data.nbt()),
                    () -> logger.warn("Unknown actor type {} in world {} at {}", data.type(), world.dimension().identifier(), marker.blockPosition())
            );
        });

        ServerEntityHooks.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof Marker marker)) return;

            Actor actor = ((ApMarkerEntity) marker).ap2$getActor();

            if (actor == null) return;

            ActorManagerAccess.get(world).discard(actor, marker);
        });
    }

    private void createActor(ServerLevel world, Marker marker, ActorType<?> type, CompoundTag data) {
        var dataSource = new Dynamic<>(NbtOps.INSTANCE, data);
        var init = new ActorInit(world, type, dataSource);

        type.factory().create(init).ifPresent(actor -> ActorManagerAccess.get(world).spawn(actor, marker));
    }

    private void registerDynamicRegistries() {
        DynamicRegistries.register(ApRegistries.PLAYER_HEAD, PlayerHead.CODEC);
    }
}

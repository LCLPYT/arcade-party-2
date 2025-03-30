package work.lclpnet.ap2.base;

import com.mojang.serialization.Dynamic;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
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
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.core.type.ActorManagerAccess;
import work.lclpnet.ap2.core.type.ApMarkerEntity;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.kibu.hook.entity.ServerEntityHooks;

import static work.lclpnet.ap2.base.ArcadeParty.logger;

public class ArcadePartyInit implements ModInitializer {

    public static final Identifier RESOURCES_ID = ArcadeParty.identifier("resources");

    @Override
    public void onInitialize() {
        registerDynamicRegistries();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(RESOURCES_ID, lookup -> new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RESOURCES_ID;
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

            NbtCompound actorNbt = ActorManager.getActorNbt(marker);

            if (actorNbt == null) return;

            String typeStr = actorNbt.getString(ActorManager.ACTOR_TYPE_NBT_KEY);

            if (typeStr.isEmpty()) return;

            Identifier actorId = Identifier.tryParse(typeStr);

            if (actorId == null) return;

            actorRegistry.getType(actorId).ifPresentOrElse(
                    type -> createActor(world, marker, type, actorNbt),
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

    private void createActor(ServerWorld world, MarkerEntity marker, ActorType<?> type, NbtCompound data) {
        var dataSource = new Dynamic<>(NbtOps.INSTANCE, data);
        var init = new ActorInit(world, type, dataSource);

        type.factory().create(init).ifPresent(actor -> ActorManagerAccess.get(world).spawn(actor, marker));
    }

    private void registerDynamicRegistries() {
        DynamicRegistries.register(ApRegistries.PLAYER_HEAD, PlayerHead.CODEC);
    }
}

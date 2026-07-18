package work.lclpnet.ap2

import com.mojang.serialization.Dynamic
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.registry.DynamicRegistries
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.entity.Marker
import work.lclpnet.ap2.ApConstants.identifier
import work.lclpnet.ap2.api.actor.*
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.core.type.ActorManagerAccess
import work.lclpnet.ap2.core.type.ApMarkerEntity
import work.lclpnet.ap2.impl.resource.ApResources
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.kibu.hook.entity.ServerEntityHooks
import java.util.function.Consumer

class ArcadePartyInit : ModInitializer {

    override fun onInitialize() {
        registerDynamicRegistries()

        DataResourceLoader.get().registerReloadListener(
            RESOURCES_ID
        ) { lookup ->
            ResourceManagerReloadListener { manager ->
                ApResources.getInstance().reload(manager, lookup)
            }
        }

        val actorRegistry = ActorRegistry()

        FabricLoader.getInstance().invokeEntrypoints(
            "actor_provider", ActorProvider::class.java,
            Consumer { provider ->
                provider.provideActors { type ->
                    actorRegistry.register(type)
                }
            }
        )

        ServerEntityHooks.ENTITY_LOAD.register(ServerEntityEvents.Load { entity, level ->
            if (entity !is Marker) return@Load

            val data = ActorManager.getActorNbt(entity) ?: return@Load
            val type = actorRegistry.getType(data.type)

            if (type != null) {
                createActor(level, entity, type, data.nbt)
            } else {
                ApConstants.logger.warn(
                    "Unknown actor type {} in level {} at {}",
                    data.type,
                    level.dimension().identifier(),
                    entity.blockPosition()
                )
            }
        })

        ServerEntityHooks.ENTITY_UNLOAD.register(ServerEntityEvents.Unload { entity, world ->
            if (entity !is Marker) return@Unload

            val actor = (entity as ApMarkerEntity).`ap2$getActor`() ?: return@Unload

            ActorManagerAccess.get(world).discard(actor, entity)
        })
    }

    private fun createActor(world: ServerLevel, marker: Marker, type: ActorType<*>, data: CompoundTag) {
        val dataSource = Dynamic(NbtOps.INSTANCE, data)
        val init = ActorInit(world, type, dataSource)
        val actor = type.factory.create(init) ?: return

        ActorManagerAccess.get(world).spawn(actor, marker)
    }

    private fun registerDynamicRegistries() {
        DynamicRegistries.register(ApRegistries.PLAYER_HEAD, PlayerHead.CODEC)
    }

    companion object {
        val RESOURCES_ID: Identifier = identifier("resources")
    }
}

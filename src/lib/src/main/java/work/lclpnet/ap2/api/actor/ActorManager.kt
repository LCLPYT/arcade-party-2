package work.lclpnet.ap2.api.actor

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.MapLike
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.entity.Marker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.lclpnet.ap2.core.type.ApMarkerEntity
import work.lclpnet.kibu.access.misc.CustomNbt
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

class ActorManager : Tickable {
    private val actors = LinkedHashSet<Actor>()
    private val tickables = LinkedHashSet<Tickable>()

    fun spawn(actor: Actor, marker: Marker) {
        actor.position = marker.position()

        (marker as ApMarkerEntity).`ap2$setActor`(actor)

        if (!add(actor)) return

        actor.onSpawn()

        ActorSpawnedCallback.HOOK.invoker().onSpawned(actor)
    }

    fun discard(actor: Actor, marker: Marker) {
        (marker as ApMarkerEntity).`ap2$setActor`(null)

        if (!remove(actor)) return

        actor.onRemove()

        ActorRemovedCallback.HOOK.invoker().onRemoved(actor)
    }

    @Synchronized
    private fun add(actor: Actor): Boolean {
        if (!actors.add(actor)) {
            return false
        }

        if (actor is Tickable) {
            tickables.add(actor)
        }

        return true
    }

    @Synchronized
    private fun remove(actor: Actor): Boolean {
        if (!actors.remove(actor)) {
            return false
        }

        if (actor is Tickable) {
            tickables.remove(actor)
        }

        return true
    }

    @Synchronized
    override fun tick() {
        for (tickable in tickables) {
            tickable.tick()
        }
    }

    data class ActorInfo(val type: Identifier, val nbt: CompoundTag) {
        companion object {
            val TYPE_CODEC: MapCodec<Identifier> = Identifier.CODEC.fieldOf(ACTOR_TYPE_NBT_KEY)

            val CODEC: Codec<ActorInfo> = CompoundTag.CODEC.flatXmap(
                Function { nbt ->
                    NbtOps.INSTANCE.getMap(nbt)
                        .flatMap { mapLike ->
                            TYPE_CODEC.decode(NbtOps.INSTANCE, mapLike)
                        }
                        .map { id ->
                            ActorInfo(id, nbt)
                        }
                },
                Function { actorInfo: ActorInfo ->
                    NbtOps.INSTANCE.getMap(actorInfo.nbt).flatMap { _ ->
                        TYPE_CODEC.encode(
                            actorInfo.type,
                            NbtOps.INSTANCE,
                            NbtOps.INSTANCE.mapBuilder()
                        )
                            .build(actorInfo.nbt)
                            .map { it as? CompoundTag }
                    }
                }
            )
        }
    }

    companion object {
        const val ACTOR_NBT_KEY = "gca:actor"
        const val ACTOR_TYPE_NBT_KEY = "type"

        val ACTOR_INFO_CODEC: MapCodec<ActorInfo> = ActorInfo.CODEC.fieldOf(ACTOR_NBT_KEY)

        private val logger: Logger = LoggerFactory.getLogger(ActorManager::class.java)

        fun getActorNbt(marker: Marker): ActorInfo? =
            CustomNbt.get(marker, ACTOR_INFO_CODEC)
                .orElse(null)

        @JvmStatic
        fun writeActorNbt(marker: Marker, actor: Actor) {
            val actorCompound = CompoundTag()
            var actorNbt = actorCompound

            val data = actor.createData()
            val id = actor.type.id

            if (data != null) {
                actorNbt = data.encode<Tag>(NbtOps.INSTANCE, actorCompound)
                    .map { elem ->
                        elem as? CompoundTag
                    }
                    .resultOrPartial { logger.error("Encoding actor data for $id: $it") }
                    .orElse(actorCompound)!!
            }

            val info = ActorInfo(id, actorNbt)

            CustomNbt.set(marker, ACTOR_INFO_CODEC, info)
        }
    }
}

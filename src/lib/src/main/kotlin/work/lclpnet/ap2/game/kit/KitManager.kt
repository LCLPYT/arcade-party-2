package work.lclpnet.ap2.game.kit

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableBiMap
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.function.UnaryOperator

class KitManager(kits: List<Kit>) : KitReadView {
    val kits: List<Kit>
    private val kitById: BiMap<String, Kit>
    private val playerKits: MutableMap<UUID, Kit> = HashMap<UUID, Kit>()

    var options: KitOptions = KitOptions.DEFAULT
        private set

    init {
        require(!kits.isEmpty()) { "At least one kit is required" }

        val kitById = HashBiMap.create<String, Kit>(kits.size)

        for (kit in kits) {
            require(!kitById.containsKey(kit.id())) { "Kit with id '${kit.id()}' already exists." }

            kitById[kit.id()] = kit
        }

        this.kitById = ImmutableBiMap.copyOf(kitById)
        this.kits = kits.toList()
    }

    fun init() {
        for (kit in kits) {
            kit.init(options)
        }
    }

    fun modifyOptions(modifier: UnaryOperator<KitOptions>) {
        options = Objects.requireNonNull(modifier.apply(options))
    }

    fun setupPlayerKits(players: Iterable<ServerPlayer>) {
        for (player in players) {
            changeKit(player, defaultKit())
        }
    }

    fun defaultKit(): Kit = kits.first()

    @Synchronized
    override fun getKit(player: ServerPlayer): Kit =
        playerKits.getOrDefault(player.getUUID(), defaultKit())

    @Synchronized
    fun changeKit(player: ServerPlayer, kit: Kit) {
        validateKit(kit)

        getKit(player).unequip(player, options)

        playerKits[player.getUUID()] = kit

        kit.equip(player, options)
    }

    private fun validateKit(kit: Kit) {
        require(kitById.containsValue(kit)) { "Invalid kit" }
    }

    @Synchronized
    fun byId(id: String): Kit? = kitById[id]
}

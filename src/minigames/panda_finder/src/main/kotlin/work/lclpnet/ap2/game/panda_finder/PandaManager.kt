package work.lclpnet.ap2.game.panda_finder

import com.mojang.serialization.JavaOps
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.phys.Vec3
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.api.base.Participants
import java.util.*

private const val PANDA_COUNT = 100
private const val SEARCHED_PANDA_COUNT = 5

class PandaManager(
    private val logger: Logger,
    private val spawns: List<Vec3>,
    private val random: Random,
    private val world: ServerLevel,
    private val participants: Participants
) {
    private val pandas = HashSet<Panda>()
    private var imagesByGene: Map<Panda.Gene, List<Int>>? = null
    private var current: Panda.Gene? = null
    private var currentMapId = -1

    init {
        require(spawns.isNotEmpty()) { "Spawn list is empty" }
    }

    fun next() {
        val genes = Panda.Gene.entries.toTypedArray()
        current = genes[random.nextInt(genes.size)]

        randomizeImage()

        for (player in participants) {
            giveImageTo(player)
        }

        clear()
        populate()
    }

    private fun randomizeImage() {
        val images = imagesByGene?.get(current)

        if (images.isNullOrEmpty()) {
            logger.error("There are no images for panda gene {}", current)
            currentMapId = -1
        } else {
            currentMapId = images[random.nextInt(images.size)]
        }
    }

    private fun populate() {
        val otherGenes = Panda.Gene.entries.filter { it != current }.toTypedArray()

        var remain = SEARCHED_PANDA_COUNT
        val chance = SEARCHED_PANDA_COUNT / PANDA_COUNT.toFloat()

        for (i in 0 until PANDA_COUNT) {
            val pos = randomPosition()
            val panda = Panda(EntityType.PANDA, world)
            pandas.add(panda)

            val searched = i > PANDA_COUNT - remain - 1 || (remain > 0 && random.nextFloat() < chance)
            val gene = if (searched) {
                remain--
                current!!
            } else {
                otherGenes[random.nextInt(otherGenes.size)]
            }

            panda.mainGene = gene
            panda.hiddenGene = gene
            panda.isBaby = random.nextFloat() < 0.05f
            panda.setPos(pos)
            world.addFreshEntity(panda)
        }
    }

    private fun randomPosition(): Vec3 = spawns[random.nextInt(spawns.size)]

    private fun clear() {
        for (panda in pandas) {
            panda.discard()
        }
    }

    fun isSearchedPanda(panda: Panda): Boolean = panda.mainGene == current

    fun getLocalizedPandaGene(): String? =
        current?.let { "game.ap2.panda_finder.find.".plus(it.serializedName) }

    @Synchronized
    fun setFound() {
        for (panda in pandas) {
            if (isSearchedPanda(panda)) {
                panda.setGlowingTag(true)
            }
        }

        current = null
    }

    fun readImages(images: JSONObject) {
        val result = HashMap<Panda.Gene, List<Int>>()

        for (key in images.keySet()) {
            val gene = Panda.Gene.CODEC.parse(JavaOps.INSTANCE, key).result().orElse(null)

            if (gene == null) {
                logger.warn("Invalid panda gene named '{}'", key)
                continue
            }

            val array = images.getJSONArray(key)
            val ids = ArrayList<Int>(array.length())

            for (o in array) {
                if (o !is Number) {
                    logger.warn("Invalid integer value '{}'", o)
                    continue
                }
                ids.add(o.toInt())
            }

            result[gene] = ids
        }

        imagesByGene = result
    }

    fun getSearchedPandaPositions(): List<Vec3> = pandas
        .filter { isSearchedPanda(it) }
        .map { it.position() }

    fun giveImageTo(player: ServerPlayer) {
        if (currentMapId == -1) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
            return
        }

        val filledMap = ItemStack(Items.FILLED_MAP)
        filledMap.set(DataComponents.MAP_ID, MapId(currentMapId))
        player.setItemInHand(InteractionHand.OFF_HAND, filledMap)
    }
}

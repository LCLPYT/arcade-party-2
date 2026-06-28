package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.core.Holder
import net.minecraft.core.IdMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.equipment.ArmorMaterial
import net.minecraft.world.item.equipment.ArmorMaterials
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterial
import net.minecraft.world.item.equipment.trim.TrimPattern
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.OptionMaker
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class ArmorTrimChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier
) : Challenge {

    private var correct: Holder<TrimPattern>? = null
    private var material: Holder<TrimMaterial>? = null
    private var armorMaterial: ArmorMaterial? = null
    private var correctOption = -1

    override fun id() = "armor_trim"

    override val preparationKey = PREPARE_GUESS

    override val durationTicks = Ticks.seconds(14)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("armor_trim"))

        val patterns = getTrimPatterns()
        val opts = OptionMaker.createOptions(patterns, 4, random)

        correctOption = random.nextInt(4)

        correct = opts[correctOption]

        material = ItemHelper.getRandomTrimMaterial(world.registryAccess(), random)

        armorMaterial = when (random.nextInt(6)) {
            0 -> ArmorMaterials.LEATHER
            1 -> ArmorMaterials.CHAINMAIL
            2 -> ArmorMaterials.IRON
            3 -> ArmorMaterials.GOLD
            4 -> ArmorMaterials.DIAMOND
            5 -> ArmorMaterials.NETHERITE
            else -> throw IllegalStateException()
        }

        spawnGiants()

        val names: List<Component> = opts.map { TextUtil.getVanillaName(it) }
        input.expectSelection(*names.toTypedArray())
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = TextUtil.getVanillaName(correct!!)
        result.grantIfCorrect(gameHandle.participants, correctOption, choices::getOption)
    }

    private fun spawnGiants() {
        val pos = Vec3.atBottomCenterOf(blockShape.origin())

        val spacing = 7.0
        spawnGiant(pos.add(spacing, 0.0, 0.0), -90f)
        spawnGiant(pos.add(-spacing, 0.0, 0.0), 90f)
        spawnGiant(pos.add(0.0, 0.0, spacing), 0f)
        spawnGiant(pos.add(0.0, 0.0, -spacing), 180f)
    }

    private fun spawnGiant(pos: Vec3, yaw: Float) {
        val armorMaterial = this.armorMaterial!!
        val trim = ArmorTrim(material!!, correct!!)

        val giant = Giant(EntityTypes.GIANT, world)
        giant.setPersistenceRequired()
        giant.isNoAi = true
        giant.setYHeadRot(yaw)
        giant.setPosRaw(pos.x, pos.y, pos.z)

        val helmet = ItemStack(requireNotNull(ItemHelper.getHelmet(armorMaterial)))
        helmet.set(DataComponents.TRIM, trim)
        helmet.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
        giant.setItemSlot(EquipmentSlot.HEAD, helmet)

        val chestPlate = ItemStack(requireNotNull(ItemHelper.getChestPlate(armorMaterial)))
        chestPlate.set(DataComponents.TRIM, trim)
        chestPlate.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
        giant.setItemSlot(EquipmentSlot.CHEST, chestPlate)

        val leggings = ItemStack(requireNotNull(ItemHelper.getLeggings(armorMaterial)))
        leggings.set(DataComponents.TRIM, trim)
        leggings.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
        giant.setItemSlot(EquipmentSlot.LEGS, leggings)

        val boots = ItemStack(requireNotNull(ItemHelper.getBoots(armorMaterial)))
        boots.set(DataComponents.TRIM, trim)
        boots.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
        giant.setItemSlot(EquipmentSlot.FEET, boots)

        modifier.spawnEntity(giant)
    }

    private fun getTrimPatterns(): IdMap<Holder<TrimPattern>> =
        world.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).asHolderIdMap()
}

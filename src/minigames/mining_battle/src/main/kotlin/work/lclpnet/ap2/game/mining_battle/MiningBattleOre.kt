package work.lclpnet.ap2.game.mining_battle

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.ExplosionUtil
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*

class MiningBattleOre(
    private val random: Random,
    private val gameHandle: MiniGameHandle,
) {
    private val lookup = HashMap<Block, Ore>()
    private val ores = WeightedList<Ore>()
    lateinit var scoreConsumer: (ServerPlayer, Int) -> Unit
    lateinit var valid: (BlockPos) -> Boolean

    fun init() {
        registerOre(null, 0, 0.9f)
        registerOre(COAL_ORE, 1, 0.02f)
        registerOre(IRON_ORE, 1, 0.02f)
        registerOre(LAPIS_ORE, 1, 0.02f)
        registerOre(REDSTONE_ORE, 2, 0.01f)
        registerOre(GOLD_ORE, 2, 0.01f)
        registerOre(DIAMOND_ORE, 3, 0.0075f)
        registerOre(EMERALD_ORE, 3, 0.0075f)
        registerOre(RAW_COPPER_BLOCK, 4, 0.008f)
        registerOre(RAW_IRON_BLOCK, 4, 0.008f)
        registerOre(DIAMOND_BLOCK, 5, 0.005f)
        registerOre(RAW_GOLD_BLOCK, 5, 0.005f)
        registerOre(AMETHYST_BLOCK, 0, 0.0025f)
        registerOre(POLISHED_GRANITE, 0, 0.009f)
        registerOre(TNT, 0, 0.005f)
    }

    private fun registerOre(block: Block?, value: Int, probability: Float) {
        val ore = Ore(block, value)
        ores.add(ore, probability)

        if (block != null) {
            lookup[block] = ore
        }
    }

    private fun getValue(state: BlockState): Int = lookup[state.block]?.value ?: 0

    fun isOre(state: BlockState): Boolean = lookup.containsKey(state.block)

    fun getRandomState(): BlockState? {
        val ore = ores.getRandomElement(random) ?: return null
        return ore.block?.defaultBlockState()
    }

    fun onOreBroken(player: ServerPlayer, pos: BlockPos, broken: BlockState) {
        if (broken.isOf(AMETHYST_BLOCK)) {
            weakenOthers(player)
            return
        }

        if (broken.isOf(POLISHED_GRANITE)) {
            giveHaste(player)
            return
        }

        if (broken.isOf(TNT)) {
            explode(player, pos)
            return
        }

        val value = getValue(broken)

        if (value > 0) {
            scoreConsumer(player, value)
        }
    }

    private fun explode(player: ServerPlayer, pos: BlockPos) {
        val world = player.level()

        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5
        val vec = Vec3(x, y, z)
        val power = 2.1f

        val explosion = ServerExplosion(world, null, null, null, vec, power, false, Explosion.BlockInteraction.KEEP)

        @Suppress("KotlinConstantConditions")
        val access = explosion as ServerExplosionAccessor

        world.gameEvent(null, GameEvent.EXPLODE, vec)
        world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 1.0, 0.0, 0.0, 1.0)

        val air = AIR.defaultBlockState()
        var totalValue = 0

        for (exPos in access.invokeCalculateExplodedPositions()) {
            if (!valid(exPos)) continue

            val exState = world.getBlockState(exPos)
            if (exState.isAir) continue

            totalValue += getValue(exState)
            world.setBlockAndUpdate(exPos, air)
        }

        if (totalValue > 0) {
            scoreConsumer(player, totalValue)
        }

        access.invokeHurtEntities()
        ExplosionUtil.sendExplosion(world, explosion, ParticleTypes.EXPLOSION)
    }

    private fun giveHaste(player: ServerPlayer) {
        player.removeEffect(MobEffects.MINING_FATIGUE)

        val statusEffect = player.getEffect(MobEffects.HASTE)
        val remainingTicks = statusEffect?.duration ?: 0

        player.removeEffect(MobEffects.HASTE)
        player.addEffect(MobEffectInstance(MobEffects.HASTE, remainingTicks + 100, 0))
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.5f, 2f)

        val msg = gameHandle.translations.translateText(player, "haste")
            .withStyle(ChatFormatting.GREEN)

        player.sendSystemMessage(msg)
    }

    private fun weakenOthers(player: ServerPlayer) {
        SoundHelper.playSound(gameHandle.server, SoundEvents.RAVAGER_CELEBRATE, SoundSource.HOSTILE, 0.5f, 1f)

        val translations = gameHandle.translations

        val playerMsg = translations.translateText(player, "weakened")
            .withStyle(ChatFormatting.GREEN)

        player.sendSystemMessage(playerMsg)

        val otherMsg = translations.translateText(
            "weakened_by",
            styled(player.scoreboardName, ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.RED)

        for (other in gameHandle.participants) {
            if (other === player || other.hasEffect(MobEffects.HASTE)) continue

            other.removeEffect(MobEffects.MINING_FATIGUE)
            other.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 0), player)

            other.sendSystemMessage(otherMsg.translateFor(other))
        }
    }

    private data class Ore(val block: Block?, val value: Int)
}

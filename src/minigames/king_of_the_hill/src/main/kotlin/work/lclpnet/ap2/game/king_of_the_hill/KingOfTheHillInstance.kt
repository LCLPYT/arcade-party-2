package work.lclpnet.ap2.game.king_of_the_hill

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.util.finaleCompatibleIntScoreContainer
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.useTaskTimer
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val DURATION = 2.minutes + 40.seconds

class KingOfTheHillInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {
    
    override val data = useDataContainer(::finaleCompatibleIntScoreContainer)
    var goalShape: BlockShape? = null

    init {
        useOldCombat()
    }

    override fun prepare() {
        commons().teleportToRandomSpawns(Random.asJavaRandom())
        goalShape = MapUtil.readShape(map, "goal-shape")

        setupSidebarScoreboard(data)

        commons().gameRuleBuilder()
            .set(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT, 0)
            .set(GameRules.ADVANCE_WEATHER, false)
            .set(GameRules.FALL_DAMAGE, false)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)

        commons().addWaypoint(goalShape!!.center().center, 0xffd700)
    }

    override fun go() {
        val readShape = MapUtil.readOptShape(map, "spawn-remove-shape")

        readShape?.forEach { pos ->
            level.setBlock(pos, Blocks.AIR)
        }

        val name = translate("game.ap2.king_of_the_hill.knockback_stick").formatted(ChatFormatting.GOLD)
        val knockback = ItemHelper.getEnchantment(Enchantments.KNOCKBACK, level.registryAccess())

        for (player in players()) {
            val stack = ItemStack(Items.STICK)
            stack.set(DataComponents.ITEM_NAME, name.translateFor(player))
            stack.enchant(knockback, 1)

            player.inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)

            player.addEffect(
                MobEffectInstance(
                    MobEffects.RESISTANCE,
                Integer.MAX_VALUE, 255, false, false, false))
        }

        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                entity is ServerPlayer
                        && players().isParticipating(entity)
                        && (source.isOf(DamageTypes.PLAYER_ATTACK) || source.entity is Goat)
            }
        }

        interval(20) { ->
            val inGoal = players().stream().filter { goalShape!!.contains(it.position()) }.toList()

            if (inGoal.size == 1) {
                commons().addScore(inGoal[0], 1, data)
                inGoal[0].playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f)
            }
        }

        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks) { player, i ->
            if (players().isParticipating(player) && i != 4) {
                PlayerInventoryAccess.setSelectedSlot(player, 4)
            }
        }

        useTaskTimer(DURATION).whenDone { winManager.complete() }
    }
}

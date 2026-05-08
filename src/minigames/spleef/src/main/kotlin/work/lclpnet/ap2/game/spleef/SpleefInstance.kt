package work.lclpnet.ap2.game.spleef

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.lobby.game.api.prot.scope.EntityBlockScope
import work.lclpnet.lobby.game.api.prot.scope.EntityDamageSourceScope
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes

private val WORLD_BORDER_DELAY = Ticks.seconds(40)
private val WORLD_BORDER_TIME = Ticks.seconds(30)

class SpleefInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    init {
        useSurvivalMode()
    }

    override fun prepare() {
        useSmoothDeath()
        useNoHealing()
        useRemainingPlayersDisplay()
    }

    override fun go() {
        gameHandle.protect { config ->
            config.allow(ProtectionTypes.BREAK_BLOCKS, EntityBlockScope { entity, pos ->
                entity.level().getBlockState(pos).`is`(Blocks.SNOW_BLOCK)
            })

            config.allow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope { _, damageSource ->
                damageSource.`is`(DamageTypes.LAVA) || damageSource.`is`(DamageTypes.OUTSIDE_BORDER)
            })
        }

        giveShovelsToPlayers()

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5))
            .then(this::removeBlocks)
    }

    private fun removeBlocks() {
        val air = Blocks.AIR.defaultBlockState()
        val box = MapUtil.readBox(map.requireProperty("snow-area"))

        for (pos in BlockPos.betweenClosed(box.first(), box.second())) {
            if (world.getBlockState(pos).`is`(Blocks.SNOW_BLOCK)) {
                world.setBlockAndUpdate(pos, air)
            }
        }

        SoundHelper.playSound(gameHandle.server, SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f)
    }

    private fun giveShovelsToPlayers() {
        val translations = gameHandle.translations

        for (player in gameHandle.participants) {
            val stack = ItemStack(Items.IRON_SHOVEL).unbreakable()

            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.spleef.shovel")
                .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })

            player.inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
        }
    }
}

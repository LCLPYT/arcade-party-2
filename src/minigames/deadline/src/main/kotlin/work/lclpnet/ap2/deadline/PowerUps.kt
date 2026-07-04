package work.lclpnet.ap2.deadline

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.BossEvent
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.item.SpecialItemObject
import work.lclpnet.ap2.impl.game.item.SpecialItems
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.Random
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val ITEM_SLOT = 4

// the interval of power up refreshes
private val REFRESH_INTERVAL = 30.seconds

private val REFRESH_SOUND = GameSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f)

class PowerUps(
    private val gameHandle: MiniGameHandle,
    map: GameMap,
    level: ServerLevel,
    random: Random,
    debugController: DebugController,
    cycles: (UUID) -> LightCycle?,
) {

    private val specialItems = SpecialItems.create(gameHandle, map, level, random, debugController) { registrar ->
        registrar.register(SpeedBoost(cycles), 1f)
        registrar.register(Jump(cycles), 1f)
        registrar.register(Invisible(cycles), 1f)
    }

    private val points = HashMap<BlockPos, SpecialItemObject>()

    init {
        specialItems.itemSlot = ITEM_SLOT
        specialItems.itemSize = 0.4 // a little bigger than the default 0.25, so pickups read at driving speed
        specialItems.isMarkGlowing = true
        specialItems.despawnTicks = 0 // power-ups stay until they are collected
        specialItems.setup()
    }

    fun initHooks() {
        val participants = gameHandle.participants

        // riders always have the power-up slot selected
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(gameHandle.hooks) { player, slot ->
            if (slot != ITEM_SLOT && participants.isParticipating(player)) {
                PlayerInventoryAccess.setSelectedSlot(player, ITEM_SLOT)
            }
        }

        for (player in participants) {
            PlayerInventoryAccess.setSelectedSlot(player, ITEM_SLOT)
        }
    }

    fun startRefreshing(positions: List<BlockPos>) {
        val subject = gameHandle.translations.translateText("power_up.refresh")

        val timer = BossBarTimer.builder(gameHandle.translations, subject)
            .withAlertSound(false)
            .withColor(BossEvent.BossBarColor.YELLOW)
            .withDurationTicks(REFRESH_INTERVAL.inWholeTicks.toInt())
            .build()

        timer.addPlayers(PlayerLookup.all(gameHandle.server))
        timer.start(gameHandle.bossBarProvider, gameHandle.scheduler)

        timer.whenDone {
            spawn(positions)

            for (player in gameHandle.participants) {
                REFRESH_SOUND.playTo(player)
            }

            startRefreshing(positions)
        }
    }

    /** Spawns a random power-up at every spawn point that no longer has one. */
    fun spawn(positions: List<BlockPos>) {
        for (pos in positions) {
            val existing = points[pos]

            if (existing != null && specialItems.contains(existing)) continue

            specialItems.spawnRandomItemAt(pos)?.let { points[pos] = it }
        }
    }
}

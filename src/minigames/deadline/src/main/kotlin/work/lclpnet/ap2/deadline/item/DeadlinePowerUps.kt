package work.lclpnet.ap2.deadline.item

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.deadline.rider.Riders
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.item.SpecialItemObject
import work.lclpnet.ap2.game.item.SpecialItems
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val ITEM_SLOT = 4

private const val SPAWN_CHANCE = 0.7f // chance that a free spawn point actually gets a power-up

// the interval of power up refreshes
private val REFRESH_INTERVAL = 30.seconds

class DeadlinePowerUps(
    private val gameHandle: MiniGameHandle,
    map: GameMap,
    level: ServerLevel,
    private val random: Random,
    debugController: DebugController,
    riders: Riders,
    private val positions: List<BlockPos>,
    onUsed: (ServerPlayer) -> Unit,
) {

    private val specialItems = SpecialItems.create(gameHandle, map, level, random, debugController) { registrar ->
        registrar.register(SpeedBoostPowerUp(riders, onUsed), 1f)
        registrar.register(JumpPowerUp(riders, onUsed), 1f)
        registrar.register(InvisiblePowerUp(riders, onUsed), 1f)
    }

    private val points = HashMap<BlockPos, SpecialItemObject>()

    init {
        specialItems.itemSlot = ITEM_SLOT
        specialItems.itemSize = 0.4 // a little bigger than the default 0.25, so pickups read at driving speed
        specialItems.markGlowing= true
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

    fun startRefreshing() {
        gameHandle.scheduler.interval(REFRESH_INTERVAL.inWholeTicks) { ->
            spawn()
        }
    }

    /** Spawns a random power-up with [SPAWN_CHANCE] at every spawn point that no longer has one. */
    fun spawn() {
        for (pos in positions) {
            val existing = points[pos]

            if (existing != null && specialItems.contains(existing)) continue
            if (random.nextFloat() >= SPAWN_CHANCE) continue

            specialItems.spawnRandomItemAt(pos)?.let { points[pos] = it }
        }
    }
}

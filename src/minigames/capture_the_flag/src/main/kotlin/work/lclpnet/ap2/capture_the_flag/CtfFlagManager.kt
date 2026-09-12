package work.lclpnet.ap2.capture_the_flag

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.capture_the_flag.flag.CtfFlagItem
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.item.SpecialItemObject
import work.lclpnet.ap2.game.item.SpecialItemScene
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import java.util.Random

private const val CAPTURE_RADIUS = 2.5
private const val DROP_TIMEOUT_TICKS = 15 * 20
private const val DROP_PICKUP_DELAY_TICKS = 40

/** Scale of a dropped flag, relative to the default special item size. */
private const val DROP_SCALE = 2.0

class CtfFlagManager(
    private val gameHandle: MiniGameHandle,
    level: ServerLevel,
    private val teamManager: TeamManager,
    private val teamInfo: List<CtfTeamInfo>,
    random: Random,
    private val stats: CtfStats,
    private val onCapture: (Team, CtfTeamInfo) -> Unit,
) {

    private val scene = SpecialItemScene(random, level)
    private val states = teamInfo.associateWith { FlagState(it) }

    fun setup() {
        scene.init(gameHandle.rootScheduler, gameHandle.hooks)
        scene.onPickup().register(::onPickup)

        for (info in teamInfo) {
            info.flag.init(gameHandle.hooks) { player -> steal(info, player) }
        }
    }

    fun tick() {
        for (player in gameHandle.participants) {
            scene.tickPickUp(player)
        }

        for (state in states.values) {
            tickState(state)
        }
    }

    /** Drops the flag the player is carrying, if any. */
    fun dropCarriedFlag(player: ServerPlayer) {
        val state = states.values.find { it.carrier === player } ?: return

        clearCarrier(state)

        val pos = player.position().add(0.0, 0.5, 0.0)
        state.dropped = spawnDrop(state, pos)
        state.droppedTicks = 0

        announce(state.info, "flag_dropped", player.name)
    }

    /** Returns every flag to its home position, used when the game ends. */
    fun reset() {
        for (state in states.values) {
            clearCarrier(state)
            removeDrop(state)

            if (!state.atHome) {
                state.info.flag.placeAtHome()
                state.atHome = true
            }
        }
    }

    private fun tickState(state: FlagState) {
        val carrier = state.carrier

        if (carrier != null) {
            tickCarrier(state, carrier)
            return
        }

        val dropped = state.dropped ?: return

        if (!scene.contains(dropped)) {
            state.dropped = null
            returnHome(state, null)
            return
        }

        if (++state.droppedTicks >= DROP_TIMEOUT_TICKS) {
            returnHome(state, null)
        }
    }

    private fun tickCarrier(state: FlagState, carrier: ServerPlayer) {
        if (!gameHandle.participants.isParticipating(carrier) || carrier.isRemoved) {
            dropCarriedFlag(carrier)
            return
        }

        val carrierTeam = teamManager.getTeam(carrier) ?: return
        val home = teamInfo.find { teamManager.getTeam(it) === carrierTeam } ?: return

        if (carrier.position().distanceToSqr(home.flag.homePosition) > CAPTURE_RADIUS * CAPTURE_RADIUS) return

        capture(state, carrier, carrierTeam, home)
    }

    private fun steal(info: CtfTeamInfo, player: ServerPlayer) {
        if (!gameHandle.participants.isParticipating(player)) return

        val state = states[info] ?: return

        if (teamManager.getTeam(info) === teamManager.getTeam(player)) return

        if (!state.atHome) return

        state.info.flag.takeFromHome()
        state.atHome = false

        setCarrier(state, player)
        stats.flagStolen(player)

        announce(info, "flag_stolen", player.name)
        playSound(SoundEvents.RAID_HORN.value(), 0.7f)
    }

    private fun capture(state: FlagState, carrier: ServerPlayer, carrierTeam: Team, home: CtfTeamInfo) {
        clearCarrier(state)

        state.info.flag.placeAtHome()
        state.atHome = true

        stats.flagCaptured(carrier)

        announce(state.info, "flag_captured", carrier.name)
        playSound(SoundEvents.PLAYER_LEVELUP, 0.6f)

        onCapture(carrierTeam, home)
    }

    private fun onPickup(player: ServerPlayer, obj: SpecialItemObject): Boolean {
        val state = states.values.find { it.dropped === obj } ?: return false

        if (!gameHandle.participants.isParticipating(player)) return false

        val flagTeam = teamManager.getTeam(state.info)

        state.dropped = null

        if (teamManager.getTeam(player) === flagTeam) {
            returnHome(state, player)
        } else {
            setCarrier(state, player)
            announce(state.info, "flag_stolen", player.name)
        }

        return true
    }

    private fun returnHome(state: FlagState, player: ServerPlayer?) {
        removeDrop(state)

        state.info.flag.placeAtHome()
        state.atHome = true

        if (player != null) {
            stats.flagRescued(player)

            announce(state.info, "flag_returned", player.name)
        } else {
            announce(state.info, "flag_returned_timeout")
        }

        playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f)
    }

    private fun setCarrier(state: FlagState, player: ServerPlayer) {
        state.carrier = player
        state.droppedTicks = 0

        player.setItemSlot(EquipmentSlot.OFFHAND, state.info.flag.carryStack.copy())
        player.addEffect(MobEffectInstance(MobEffects.GLOWING, Int.MAX_VALUE, 0, false, false, true))
    }

    private fun clearCarrier(state: FlagState) {
        val carrier = state.carrier ?: return

        state.carrier = null

        carrier.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        carrier.removeEffect(MobEffects.GLOWING)
    }

    private fun spawnDrop(state: FlagState, pos: Vec3): SpecialItemObject {
        val name = state.info.key.getDisplayName(gameHandle.translations)
        val size = DROP_SCALE * SpecialItemObject.DEFAULT_SIZE

        val obj = scene.spawnItem(
            pos, CtfFlagItem(state.info.flag), state.info.flag.carryStack.copy(),
            gameHandle.translations, name, size
        )

        obj.setPickupDelay(DROP_PICKUP_DELAY_TICKS)
        obj.setGlowing(true)

        scene.velocity(obj).set(0.0, 4.0, 0.0)

        return obj
    }

    private fun removeDrop(state: FlagState) {
        val dropped = state.dropped ?: return

        state.dropped = null

        if (scene.contains(dropped)) {
            scene.remove(dropped)
        }
    }

    private fun announce(info: CtfTeamInfo, key: String, vararg args: Any) {
        val flagName = info.key.getDisplayName(gameHandle.translations)

        gameHandle.translations.translateText(key, *args, flagName)
            .sendTo(PlayerLookup.all(gameHandle.server))
    }

    private fun playSound(sound: SoundEvent, volume: Float) {
        for (player in PlayerLookup.all(gameHandle.server)) {
            player.playNotifySound(sound, SoundSource.PLAYERS, volume, 1f)
        }
    }

    private class FlagState(val info: CtfTeamInfo) {
        var atHome = true
        var carrier: ServerPlayer? = null
        var dropped: SpecialItemObject? = null
        var droppedTicks = 0
    }
}

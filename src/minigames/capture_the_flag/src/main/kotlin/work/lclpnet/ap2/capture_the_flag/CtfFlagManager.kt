package work.lclpnet.ap2.capture_the_flag

import io.ktor.http.content.TextContent
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ServerPacketListener
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
import work.lclpnet.ap2.game.util.DynamicWaypoint
import work.lclpnet.kibu.translate.Translations
import java.util.*

private const val CAPTURE_RADIUS = 2.5
private const val DROP_TIMEOUT_TICKS = 15 * 20
private const val DROP_PICKUP_DELAY_TICKS = 40

/** Scale of a dropped flag, relative to the default special item size. */
private const val DROP_SCALE = 2.0

class CtfFlagManager(
    private val gameHandle: MiniGameHandle,
    private val level: ServerLevel,
    private val teamManager: TeamManager,
    private val teamInfo: List<CtfTeamInfo>,
    random: Random,
    private val stats: CtfStats,
    private val translations: Translations,
    private val onCapture: (Team, CtfTeamInfo) -> Unit,
) {

    private val scene = SpecialItemScene(random, level)
    private val states = teamInfo.associateWith { FlagState(it) }
    private val waypoints = states.values.associateWith(::createWaypoint)

    fun setup() {
        scene.init(gameHandle.rootScheduler, gameHandle.hooks)
        scene.onPickup().register(::onPickup)

        for (info in teamInfo) {
            info.flag.init(gameHandle) { player -> steal(info, player) }
        }

        waypoints.values.forEach(DynamicWaypoint::track)
    }

    fun tick() {
        for (player in gameHandle.participants) {
            scene.tickPickUp(player)
        }

        for ((state, waypoint) in waypoints) {
            tickState(state)
            waypoint.update()
        }
    }

    /** Drops the flag the player is carrying, if any. */
    fun dropCarriedFlag(player: ServerPlayer) {
        val state = states.values.find { it.carrier === player } ?: return

        clearCarrier(state)

        val pos = player.position().add(0.0, 0.5, 0.0)
        state.dropped = spawnDrop(state, pos)
        state.droppedTicks = 0

        announce(state.info, "flag_dropped", player.displayName)
    }

    /** Returns every flag to its home position, used when the game ends. */
    fun reset() {
        waypoints.values.forEach(DynamicWaypoint::untrack)

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
        val home = homeOf(carrier) ?: return

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

        announce(info, "flag_stolen", player.displayName)
        playSound(SoundEvents.GOAT_HORN_SOUND_VARIANTS[2].value(), 0.7f)
        player.playNotifySound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.5f, 1f)

        translations.translateText("waypoint.own_flag")
            .withColor(TextColor.YELLOW)
            .sendTo(teamManager.getTeam(info)?.players.orEmpty())

        translations.translateText("waypoint.bring_target")
            .withColor(TextColor.YELLOW)
            .sendTo(player)
    }

    private fun capture(state: FlagState, carrier: ServerPlayer, carrierTeam: Team, home: CtfTeamInfo) {
        clearCarrier(state)

        state.info.flag.placeAtHome()
        state.atHome = true

        stats.flagCaptured(carrier)

        announce(state.info, "flag_captured", carrier.displayName)
        playSound(SoundEvents.PLAYER_LEVELUP, 0.6f, carrierTeam.players)
        playSound(SoundEvents.BLAZE_HURT, 0.6f, teamManager.getTeam(home)?.players.orEmpty())

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
            announce(state.info, "flag_stolen", player.displayName)
        }

        return true
    }

    private fun returnHome(state: FlagState, player: ServerPlayer?) {
        removeDrop(state)

        state.info.flag.placeAtHome()
        state.atHome = true

        if (player != null) {
            stats.flagRescued(player)

            announce(state.info, "flag_returned", player.displayName)
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
        val name = translations.translateText(
            "flag_of",
            state.info.key.getDisplayName(gameHandle.translations)
        ).withColor(state.info.key.color)

        val size = DROP_SCALE * SpecialItemObject.DEFAULT_SIZE

        val obj = scene.spawnItem(
            pos,
            CtfFlagItem(state.info.flag),
            state.info.flag.carryStack.copy(),
            gameHandle.translations,
            name,
            size
        )

        obj.setPickupDelay(DROP_PICKUP_DELAY_TICKS)
        obj.setGlowColorOverride(state.info.key.color)
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
            .withColor(TextColor.GRAY)
            .sendTo(PlayerLookup.all(gameHandle.server))
    }

    private fun playSound(
        sound: SoundEvent,
        volume: Float,
        players: Collection<ServerPlayer> = PlayerLookup.all(gameHandle.server),
    ) {
        for (player in players) {
            player.playNotifySound(sound, SoundSource.PLAYERS, volume, 1f)
        }
    }

    /**
     * The flag is always revealed to the opposing team.
     * The owning team only sees it while it is away from its home position.
     */
    private fun createWaypoint(state: FlagState) = DynamicWaypoint(
        level,
        color = state.info.key.color,
        visibleTo = { player -> !state.atHome || teamManager.getTeam(player) !== teamManager.getTeam(state.info) }
    ) { receiver -> flagPosition(state, receiver) }

    /** The carrier is pointed at the position the flag has to be brought to, everyone else at the flag itself. */
    private fun flagPosition(state: FlagState, receiver: ServerPlayer): Vec3 {
        state.carrier?.let { carrier ->
            if (carrier === receiver) {
                return homeOf(carrier)?.flag?.homePosition ?: carrier.position()
            }

            return carrier.position()
        }

        state.dropped?.let { return Vec3(it.position.x, it.position.y, it.position.z) }

        return state.info.flag.homePosition
    }

    private fun homeOf(player: ServerPlayer): CtfTeamInfo? {
        val team = teamManager.getTeam(player) ?: return null

        return teamInfo.find { teamManager.getTeam(it) === team }
    }

    private class FlagState(val info: CtfTeamInfo) {
        var atHome = true
        var carrier: ServerPlayer? = null
        var dropped: SpecialItemObject? = null
        var droppedTicks = 0
    }
}

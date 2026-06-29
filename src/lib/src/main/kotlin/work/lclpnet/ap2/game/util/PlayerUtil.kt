package work.lclpnet.ap2.game.util

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import lombok.Getter
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.game.player.PlayerManager
import work.lclpnet.ap2.impl.util.effect.ApEffect
import work.lclpnet.combatctl.api.CombatControl
import work.lclpnet.combatctl.api.CombatStyle
import work.lclpnet.combatctl.impl.CombatStyles
import work.lclpnet.game.util.PlayerReset
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class PlayerUtil(
    private val server: MinecraftServer,
    private val playerManager: PlayerManager,
) {

    private val combatControl: CombatControl = CombatControl.get(server)
    private val effects = ObjectOpenHashSet<ApEffect>(1)
    private val stateOverrides = HashMap<UUID, State>()

    var defaultGameMode: GameType = INITIAL_GAMEMODE
        private set

    var defaultCombatStyle: CombatStyle = CombatStyles.MODERN
        private set

    @Getter
    private var allowFlight = false

    fun setDefaultGameMode(defaultGameMode: GameType) {
        this.defaultGameMode = defaultGameMode
    }

    fun setDefaultCombatStyle(defaultCombatStyle: CombatStyle) {
        this.defaultCombatStyle = defaultCombatStyle
        combatControl.setStyle(this.defaultCombatStyle)
    }

    fun setAllowFlight(allowFlight: Boolean) {
        this.allowFlight = allowFlight

        for (player in playerManager) {
            player.abilities.mayfly = allowFlight
            player.onUpdateAbilities()
        }
    }

    fun enableEffect(effect: ApEffect) {
        effects.add(effect)

        val players = if (effect.isGlobal) PlayerLookup.all(server) else playerManager

        for (player in players) {
            effect.apply(player)
        }
    }

    fun disableEffect(effect: ApEffect) {
        effects.remove(effect)

        val players = if (effect.isGlobal) PlayerLookup.all(server) else playerManager

        for (player in players) {
            effect.remove(player)
        }
    }

    fun setStateOverride(player: ServerPlayer, state: State?) {
        if (state == null) {
            stateOverrides.remove(player.getUUID())
        } else {
            stateOverrides[player.getUUID()] = state
        }
    }

    fun getState(player: ServerPlayer): State {
        val override = stateOverrides[player.getUUID()]

        if (override != null) {
            return override
        }

        return if (playerManager.isParticipating(player)) State.DEFAULT else State.SPECTATOR
    }

    @JvmOverloads
    fun resetPlayer(player: ServerPlayer, state: State = getState(player)) {
        player.setGameMode((if (state == State.DEFAULT) defaultGameMode else GameType.SPECTATOR))
        player.removeAllEffects()
        player.inventory.clearContent()
        PlayerUtils.setCursorStack(player, ItemStack.EMPTY)

        player.getFoodData().foodLevel = 20
        player.absorptionAmount = 0f
        player.setExperienceLevels(0)
        player.setExperiencePoints(0)
        player.remainingFireTicks = 0
        player.setSharedFlagOnFire(false)
        player.arrowCount = 0
        VelocityModifier.setVelocity(player, Vec3.ZERO)

        PlayerReset.resetAttributes(player)

        player.health = player.maxHealth
        player.removeVehicle()

        PlayerReset.resetSpawnPoint(player)

        val abilities = player.abilities
        abilities.flyingSpeed = 0.05f
        PlayerReset.modifyWalkSpeed(player, 0.1f, false)

        when (state) {
            State.DEFAULT -> {
                abilities.flying = false
                abilities.mayfly = allowFlight
                abilities.invulnerable = false
            }

            State.SPECTATOR -> {
                abilities.flying = true
                abilities.mayfly = true
                abilities.invulnerable = true
            }
        }

        player.onUpdateAbilities()

        for (effect in effects) {
            effect.apply(player)
        }

        combatControl.setStyle(player, defaultCombatStyle)
    }

    fun resetToDefaults() {
        setDefaultGameMode(INITIAL_GAMEMODE)
        setDefaultCombatStyle(
            CombatStyles.MODERN.andThen(
                { player -> player.isDisableOldBobbing = false },
                { _ -> }
            )
        )

        setAllowFlight(false)
        effects.clear()
        stateOverrides.clear()
    }

    fun updatePlayerListNames() {
        updatePlayerListNames(PlayerLookup.all(server))
    }

    fun updatePlayerListNames(players: Collection<ServerPlayer>) {
        val packet = ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
            players
        )

        server.playerList.broadcastAll(packet)
    }

    enum class State {
        DEFAULT,
        SPECTATOR
    }

    companion object {
        val INITIAL_GAMEMODE: GameType = GameType.ADVENTURE

        @JvmStatic
        fun getLoadingDelayTicks(players: Int): Int {
            return Ticks.seconds(5) + players * 10
        }

        fun getRelativeHorizontalInputVector(input: Input): Vec3 {
            var x = 0.0
            val y = 0.0
            var z = 0.0

            if (input.forward()) z += 1.0
            if (input.backward()) z -= 1.0

            if (input.left()) x += 1.0
            if (input.right()) x -= 1.0

            val lenSq = x * x + y * y + z * z

            if (abs(lenSq) < 1e-6) {
                return Vec3.ZERO
            }

            val len = sqrt(lenSq)

            return Vec3(x / len, y / len, z / len)
        }

        fun getHorizontalInputVector(player: ServerPlayer): Vec3 {
            val relInput: Vec3 = getRelativeHorizontalInputVector(player.lastClientInput)

            return relInput.yRot(Math.toRadians(-player.yRot.toDouble()).toFloat())
        }
    }
}
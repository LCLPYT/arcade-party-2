package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.level.GameType
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.impl.game.data.type.FFAGameResult
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.game.data.type.TeamGameResult
import work.lclpnet.ap2.impl.game.data.type.TeamRef
import work.lclpnet.combatctl.impl.CombatStyles
import work.lclpnet.game.impl.prot.MutableProtectionConfig
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback
import java.util.*


fun MiniGameInstance.configureDefaults(
    locatorBar: Boolean = false,
) {
    gameHandle.protect { config ->
        config.disallowAll()
        ProtectorUtils.allowCreativeOperatorBypass(config)
    }

    registerDefaultHooks()

    resetPlayers()

    if (!locatorBar) {
        disableLocatorBar()
    }

    gameHandle.deathMessages.replaceVanillaDeathMessages(level, hooks)
}

private fun MiniGameInstance.registerDefaultHooks() {
    val playerUtil = gameHandle.playerUtil

    ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, _ ->
        if (!source.isOf(DamageTypes.FELL_OUT_OF_WORLD) || entity !is ServerPlayer) return@registerWith true

        if (entity.isSpectator) {
            gameHandle.worldFacade.teleport(entity)
            false
        } else {
            true
        }
    }

    PlayerSpawnLocationCallback.HOOK.registerWith(hooks) { data ->
        playerUtil.resetPlayer(data.player)
    }
}

private fun MiniGameInstance.resetPlayers() {
    for (player in allPlayers()) {
        gameHandle.playerUtil.resetPlayer(player)
    }
}

private fun MiniGameInstance.disableLocatorBar() {
    // hide players from locator by default
    PlayerWaypointCallback.HOOK.registerWith(gameHandle.hooks) { _, waypoint ->
        waypoint is ServerPlayer
    }

    level.waypointManager.breakAllConnections()
}

fun MiniGameInstance.useStartup(go: () -> Unit) {
    GameStartSequence(gameHandle, ::allPlayers).startWithGo { go() }
}

fun MiniGameInstance.useFFAWinManager(
    map: GameMap?,
    data: () -> DataContainer<ServerPlayer, PlayerRef>,
): WinManager<ServerPlayer, PlayerRef> {
    val winData: WinManager.Data<ServerPlayer, PlayerRef> = WinManager.Data(
        data,
        { value -> Optional.of(value) },
        { player -> PlayerRef.create(player) },
        { player -> PlayerRef.create(player) },
        { container -> FFAGameResult(container) }
    )

    return WinManager(gameHandle, map, winData)
}

fun MiniGameInstance.useTeamWinManager(
    teamManager: TeamManager,
    map: GameMap?,
    data: () -> DataContainer<Team, TeamRef>
): WinManager<Team, TeamRef> {
    val winData: WinManager.Data<Team, TeamRef> = WinManager.Data(
        data,
        { player: ServerPlayer -> teamManager.getTeam(player) },
        { team: Team -> TeamRef(team.key(), gameHandle.translations) },
        { player: ServerPlayer ->
            teamManager.getTeam(player).map { team ->
                TeamRef(team.key(), gameHandle.translations)
            }.orElse(null)
        },
        { dataContainer ->
            TeamGameResult(dataContainer) { ref ->
                teamManager.getTeam(ref).orElse(null)
            }
        }
    )

    return WinManager(gameHandle, map, winData)
}

/**
 * Sets the default player game mode to survival mode.
 * Player game modes are updated by [work.lclpnet.ap2.impl.game.PlayerUtil.resetPlayer].
 * Should be called before [configureDefaults] is called.
 * For games extending [work.lclpnet.ap2.game.base.MapGameInstance], call this in the class initializer.
 */
fun MiniGameInstance.useSurvivalMode() {
    gameHandle.playerUtil.setDefaultGameMode(GameType.SURVIVAL)
}

/**
 * Sets the combat style to classic combat.
 * Player combat styles are updated by [work.lclpnet.ap2.impl.game.PlayerUtil.resetPlayer].
 * Should be called before [configureDefaults] is called.
 * For games extending [work.lclpnet.ap2.game.base.MapGameInstance], call this in the class initializer.
 */
fun MiniGameInstance.useOldCombat() {
    gameHandle.playerUtil.setDefaultCombatStyle(CombatStyles.CLASSIC)
}

/**
 * Resets the protector and applies new configuration using the given closure.
 * @param configure The protector configuration function.
 */
fun MiniGameInstance.useProtector(configure: MutableProtectionConfig.() -> Unit) = gameHandle.protect {
    configure(it)
}

fun useLastRemainingParticipantListener(
    winManager: WinManager<ServerPlayer, PlayerRef>
): ParticipantListener = {
    winManager.checkForLastRemaining()
}

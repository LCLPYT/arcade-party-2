package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback


fun MiniGameInstance.configureDefaults(
    locatorBar: Boolean = false,
) {
    gameHandle.protect { config ->
        config.disallowAll()
        ProtectorUtils.allowCreativeOperatorBypass(config)
    }

    registerDefaultHooks()

    resetPlayers()

    if (locatorBar) {
        configureLocatorBar()
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

private fun MiniGameInstance.configureLocatorBar() {
    // hide players from locator by default
    PlayerWaypointCallback.HOOK.registerWith(gameHandle.hooks) { _, waypoint ->
        waypoint is ServerPlayer
    }

    level.waypointManager.breakAllConnections()
}

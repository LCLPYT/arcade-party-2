package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.resource.ApResources
import work.lclpnet.ap2.impl.util.debug.DebugController

fun useDebugController(level: ServerLevel): DebugController {
    val debugController = DebugController()

    if (ApConstants.DEBUG) {
        val modelManager = ApResources.getInstance()
        debugController.init(modelManager, level)
    }

    return debugController
}
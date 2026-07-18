package work.lclpnet.ap2

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ApConstants {
    val DEVELOPMENT: Boolean =
        FabricLoader.getInstance().isDevelopmentEnvironment || "true" == System.getenv("AP2_DEV")
    @JvmField
    val DEBUG: Boolean = FabricLoader.getInstance().isDevelopmentEnvironment

    const val ID = "ap2"
    const val LIB_ID = "ap2-lib"
    @JvmField
    val RUNTIME_CONFIG_ID = if (DEVELOPMENT) "$ID-dev" else ID

    @JvmField
    val logger: Logger = LoggerFactory.getLogger(ID)

    // people
    const val PERSON_LCLP = "@person.lclp"
    const val PERSON_BOPS = "@person.bops"
    const val PERSON_QUADRUBO = "@person.quadrubo"

    // other
    const val SEPARATOR = "============================================="
    const val SCOREBOARD_SEPARATOR = "=============="
    const val SCOREBOARD_SEPARATOR_SM = "----------------"
    const val TABLIST_SEPARATOR = "========================"
    const val TABLIST_SEPARATOR_SM = "------------------------"

    @JvmStatic
    fun identifier(path: String): Identifier =
        Identifier.fromNamespaceAndPath(ID, path)
}

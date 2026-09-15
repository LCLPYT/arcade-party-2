package work.lclpnet.ap2.api.config

import org.slf4j.Logger
import work.lclpnet.config.json.ConfigHandler
import work.lclpnet.config.json.FileConfigSerializer
import work.lclpnet.config.json.JsonConfigFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ConfigManager(
    configPath: Path,
    factory: JsonConfigFactory<Ap2Config>,
    logger: Logger,
) : ConfigAccess {
    private val handler: ConfigHandler<Ap2Config>

    init {
        val serializer = FileConfigSerializer(factory, logger)

        handler = ConfigHandler(configPath, serializer, logger)
    }

    override val config: Ap2Config
        get() = handler.getConfig()

    fun init(executor: Executor): CompletableFuture<Void> =
        CompletableFuture.runAsync({ handler.loadConfig() }, executor)
}

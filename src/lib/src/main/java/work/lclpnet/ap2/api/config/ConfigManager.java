package work.lclpnet.ap2.api.config;

import org.slf4j.Logger;
import work.lclpnet.config.json.ConfigHandler;
import work.lclpnet.config.json.FileConfigSerializer;
import work.lclpnet.config.json.JsonConfigFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ConfigManager implements ConfigAccess {

    private final ConfigHandler<Ap2Config> handler;

    public ConfigManager(Path configPath, JsonConfigFactory<Ap2Config> factory, Logger logger) {
        var serializer = new FileConfigSerializer<>(factory, logger);

        handler = new ConfigHandler<>(configPath, serializer, logger);
    }

    @Override
    public Ap2Config getConfig() {
        return handler.getConfig();
    }

    public CompletableFuture<Void> init(Executor executor) {
        return CompletableFuture.runAsync(handler::loadConfig, executor);
    }
}

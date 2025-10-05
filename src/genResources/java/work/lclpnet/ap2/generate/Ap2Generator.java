package work.lclpnet.ap2.generate;

import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Ap2Generator implements ModInitializer {

    @Override
    public void onInitialize() {
        var logger = LoggerFactory.getLogger(Ap2Generator.class);

        logger.info("Generating resources...");

        var outputDir = Path.of("generated", "map_schemas");
        ClassLoader classLoader = getClass().getClassLoader();
        var generator = new MapSchemaGenerator("work.lclpnet.ap2.", outputDir, classLoader, logger);

        generator.generate();

        logger.info("Resources generated.");

        System.exit(0);
    }
}

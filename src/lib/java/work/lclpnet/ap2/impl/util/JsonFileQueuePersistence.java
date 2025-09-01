package work.lclpnet.ap2.impl.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.util.QueuePersistence;
import work.lclpnet.ap2.api.util.QueueTransfer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class JsonFileQueuePersistence<T> implements QueuePersistence<T> {

    private final Path path;
    private final Codec<QueueTransfer<T>> transferCodec;
    private final Logger logger;

    public JsonFileQueuePersistence(Path path, Codec<T> elementCodec, Logger logger) {
        this.path = path;
        this.logger = logger;

        transferCodec = RecordCodecBuilder.create(instance -> instance.group(
                elementCodec.listOf().fieldOf("elements").forGetter(QueueTransfer::history)
        ).apply(instance, QueueTransfer::new));
    }

    @Override
    public QueueTransfer<T> restore() {
        return readJson()
                .flatMap(json -> transferCodec.decode(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> logger.error("Failed to decode queue: {}", err)))
                .map(Pair::getFirst)
                .orElseGet(QueueTransfer::empty);
    }

    @Override
    public void store(QueueTransfer<T> transfer) {
        transferCodec.encodeStart(JsonOps.INSTANCE, transfer)
                .resultOrPartial(err -> logger.error("Failed to encode queue: {}", err))
                .ifPresent(this::writeJson);
    }

    private synchronized void writeJson(JsonElement jsonElement) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            logger.error("Failed to create directory {}", path.getParent(), e);
            return;
        }

        try (var out = Files.newOutputStream(path)) {
            out.write(jsonElement.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to write json to {}", path);
        }
    }

    private synchronized Optional<JsonElement> readJson() {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        String content;

        try (var in = Files.newInputStream(path)) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var json = new Gson().fromJson(content, JsonElement.class);

        return Optional.of(json);
    }

    public static <T> JsonFileQueuePersistence<T> create(Identifier id, Codec<T> elementCodec, Logger logger) {
        Path path = Path.of("config")
                .resolve(ApConstants.ID)
                .resolve("queues")
                .resolve(id.getNamespace())
                .resolve(id.getPath() + ".json");

        return new JsonFileQueuePersistence<>(path, elementCodec, logger);
    }
}

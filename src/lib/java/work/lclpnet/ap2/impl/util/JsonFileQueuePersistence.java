package work.lclpnet.ap2.impl.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.stream.JsonWriter;
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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JsonFileQueuePersistence<T> implements QueuePersistence<T> {

    private final Path path;
    private final Codec<QueueTransfer<T>> transferCodec;
    private final Logger logger;

    public JsonFileQueuePersistence(Path path, Codec<T> elementCodec, Logger logger) {
        this.path = path;
        this.logger = logger;

        Codec<List<T>> listCodec = elementCodec.listOf();
        Codec<Set<T>> setCodec = listCodec.xmap(LinkedHashSet::new, List::copyOf);

        transferCodec = RecordCodecBuilder.create(instance -> instance.group(
                listCodec.fieldOf("elements").forGetter(QueueTransfer::history),
                setCodec.fieldOf("occurred").forGetter(QueueTransfer::occurred)
        ).apply(instance, QueueTransfer::new));
    }

    @Override
    public QueueTransfer<T> restore() {
        return readJson()
                .flatMap(json -> transferCodec.decode(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> logger.error("Failed to decode queue element: {}", err)))
                .map(Pair::getFirst)
                .orElseGet(QueueTransfer::empty);
    }

    @Override
    public void store(QueueTransfer<T> transfer) {
        transferCodec.encodeStart(JsonOps.INSTANCE, transfer)
                .resultOrPartial(err -> logger.error("Failed to encode queue element: {}", err))
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
            out.write(toStringWithIndent(jsonElement).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to write json to {}", path);
        }
    }

    private String toStringWithIndent(JsonElement jsonElement) {
        try {
            var stringWriter = new StringWriter();
            var jsonWriter = new JsonWriter(stringWriter);

            jsonWriter.setStrictness(Strictness.LENIENT);
            jsonWriter.setIndent("  ");

            TypeAdapters.JSON_ELEMENT.write(jsonWriter, jsonElement);

            return stringWriter.toString();
        } catch (IOException e) {
            throw new AssertionError("Failed to stringfy", e);
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

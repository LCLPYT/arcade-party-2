package work.lclpnet.ap2.impl.game.kit;

import com.mojang.serialization.Dynamic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.ds.Partial;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PrefabKitLoader {

    private final RegistryWrapper.WrapperLookup registries;
    private final Logger logger;
    private final List<Partial<PrefabKit, KitHandle>> kits = new ArrayList<>();

    public PrefabKitLoader(RegistryWrapper.WrapperLookup registries, Logger logger) {
        this.registries = registries;
        this.logger = logger;
    }

    public CompletableFuture<Void> loadHotbar(Object owner) {
        return CompletableFuture.runAsync(() -> {
            List<String> kitIds = findKits(owner.getClass()).stream().sorted().toList();

            for (String id : kitIds) {
                readHotbar(owner.getClass(), id);
            }
        });
    }

    private List<String> findKits(Class<?> owner) {
        URL url = owner.getResource("/kits");

        if (url == null) {
            return List.of();
        }

        if (url.getProtocol().equals("jar")) {
            return findKitsFromJar(url);
        }

        if (url.getProtocol().equals("file")) {
            return findKitsFromFs(url);
        }

        throw new IllegalStateException("Unsupported protocol: " + url.getProtocol());
    }

    private List<String> findKitsFromFs(URL url) {
        try {
            Path path = Paths.get(url.toURI());

            return readFlatKitIds(path);
        } catch (URISyntaxException | IOException e) {
            logger.error("Failed to find kits from file system: {}", url, e);
            throw new RuntimeException(e);
        }
    }

    private List<String> findKitsFromJar(URL url) {
        String jarPath = url.toString().substring(0, url.toString().indexOf("!"));

        try (FileSystem fs = FileSystems.newFileSystem(URI.create(jarPath), Collections.emptyMap())) {
            Path pathInJar = fs.getPath("/kits");

            return readFlatKitIds(pathInJar);
        } catch (IOException e) {
            logger.error("Failed to find kits from jar: {}", url, e);
            return List.of();
        }
    }

    private @NotNull List<String> readFlatKitIds(Path path) throws IOException {
        try (var stream = Files.list(path)) {
            return stream.filter(p -> p.toString().endsWith(".nbt"))
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(0, s.length() - 4))
                    .toList();
        }
    }

    private void readHotbar(Class<?> owner, String id) {
        var items = readHotbarItems(owner, id);

        if (items.isEmpty()) return;

        kits.add(handle -> new PrefabKit(handle, id, items.get()));
    }

    private Optional<List<ItemStack>> readHotbarItems(Class<?> owner, String id) {
        String resource = "/kits/%s.nbt".formatted(id);

        var in = owner.getResourceAsStream(resource);

        if (in == null) {
            logger.error("No kit definition");
            return Optional.empty();
        }

        NbtCompound nbt;

        try (var dataIn = new DataInputStream(in)) {
            nbt = NbtIo.readCompound(dataIn, NbtSizeTracker.of(1024 * 1024 * 4));
        } catch (IOException e) {
            logger.error("Failed to read items from {}", resource, e);
            return Optional.empty();
        }

        NbtList list = nbt.getListOrEmpty("0");

        List<ItemStack> items = new ArrayList<>(9);

        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound entry)) continue;

            var dynamic = new Dynamic<>(NbtOps.INSTANCE, entry);

            ItemStack stack = ItemStack.OPTIONAL_CODEC
                    .parse(RegistryOps.withRegistry(dynamic, registries))
                    .resultOrPartial(error -> logger.warn("Could not parse hotbar item: {}", error))
                    .orElse(ItemStack.EMPTY);

            items.add(stack);
        }

        return Optional.of(items);
    }

    public List<PrefabKit> createKits(KitHandle handle) {
        return kits.stream()
                .map(partial -> partial.with(handle))
                .toList();
    }
}

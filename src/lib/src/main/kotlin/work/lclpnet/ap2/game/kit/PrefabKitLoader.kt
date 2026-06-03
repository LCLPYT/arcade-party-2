package work.lclpnet.ap2.game.kit

import com.mojang.serialization.Dynamic
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger
import work.lclpnet.gaco.core.api.Partial
import java.io.DataInputStream
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.*
import java.util.concurrent.CompletableFuture

class PrefabKitLoader(private val registries: HolderLookup.Provider, private val logger: Logger) {
    private val kits: MutableList<Partial<PrefabKit, KitHandle>> = mutableListOf()

    fun loadHotbar(owner: Any): CompletableFuture<Void?> {
        return CompletableFuture.runAsync {
            val kitIds = findKits(owner.javaClass).stream().sorted().toList()
            for (id in kitIds) {
                readHotbar(owner.javaClass, id)
            }
        }
    }

    private fun findKits(owner: Class<*>): MutableList<String> {
        val url = owner.getResource("/kits") ?: return mutableListOf()

        if (url.protocol == "jar") {
            return findKitsFromJar(url)
        }

        if (url.protocol == "file") {
            return findKitsFromFs(url)
        }

        throw IllegalStateException("Unsupported protocol: ${url.protocol}")
    }

    private fun findKitsFromFs(url: URL): MutableList<String> {
        try {
            val path = Paths.get(url.toURI())

            return readFlatKitIds(path)
        } catch (e: URISyntaxException) {
            logger.error("Failed to find kits from file system: {}", url, e)
            throw RuntimeException(e)
        } catch (e: IOException) {
            logger.error("Failed to find kits from file system: {}", url, e)
            throw RuntimeException(e)
        }
    }

    private fun findKitsFromJar(url: URL): MutableList<String> {
        try {
            val uri = url.toURI()

            // reuse the existing FileSystem if the classloader already opened this JAR
            try {
                val pathInJar = Path.of(uri)
                return readFlatKitIds(pathInJar)
            } catch (_: FileSystemNotFoundException) {}

            // FileSystem not yet open - create it ourselves
            val jarPath = url.toString().substring(0, url.toString().indexOf("!"))

            FileSystems.newFileSystem(URI.create(jarPath), mutableMapOf<String, Any>()).use { fs ->
                val pathInJar = fs.getPath("/kits")
                return readFlatKitIds(pathInJar)
            }
        } catch (e: URISyntaxException) {
            logger.error("Failed to find kits from jar: {}", url, e)
            return mutableListOf()
        } catch (e: IOException) {
            logger.error("Failed to find kits from jar: {}", url, e)
            return mutableListOf()
        }
    }

    @Throws(IOException::class)
    private fun readFlatKitIds(path: Path): MutableList<String> = Files.list(path).use { stream ->
        stream.filter { p -> p.toString().endsWith(".nbt") }
            .filter { path -> Files.isRegularFile(path) }
            .map { p -> p.fileName.toString() }
            .map { s -> s.substring(0, s.length - 4) }
            .toList()
    }

    private fun readHotbar(owner: Class<*>, id: String) {
        val items = readHotbarItems(owner, id) ?: return

        kits.add(Partial { handle -> PrefabKit(handle, id, items) })
    }

    private fun readHotbarItems(owner: Class<*>, id: String): List<ItemStack>? {
        val resource = "/kits/$id.nbt"

        val input = owner.getResourceAsStream(resource)

        if (input == null) {
            logger.error("No kit definition")
            return null
        }

        val nbt: CompoundTag

        try {
            DataInputStream(input).use { dataIn ->
                nbt = NbtIo.read(dataIn, NbtAccounter.create((1024 * 1024 * 4).toLong()))
            }
        } catch (e: IOException) {
            logger.error("Failed to read items from {}", resource, e)
            return null
        }

        val list = nbt.getListOrEmpty("0")

        val items = mutableListOf<ItemStack>()

        for (element in list) {
            if (element !is CompoundTag) continue

            val dynamic = Dynamic(NbtOps.INSTANCE, element)

            val stack = ItemStack.OPTIONAL_CODEC
                .parse(RegistryOps.injectRegistryContext(dynamic, registries))
                .resultOrPartial { error -> logger.warn("Could not parse hotbar item: {}", error) }
                .orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY

            items.add(stack)
        }

        return items
    }

    fun createKits(handle: KitHandle): List<PrefabKit> {
        return kits.stream()
            .map { partial -> partial.with(handle) }
            .toList()
    }
}

package work.lclpnet.ap2.game.killeporter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.kit.PrefabKitLoader
import work.lclpnet.ap2.game.openRandomMap
import work.lclpnet.ap2.util.loot.JsonLootLoader
import work.lclpnet.ap2.util.loot.LootEntry
import work.lclpnet.gaco.ds.WeightedList

class KilleporterFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val kitLoader = PrefabKitLoader(level.registryAccess(), handle.logger)
        val loot = WeightedList<LootEntry>()

        kitLoader.loadHotbar(this).await()

        withContext(Dispatchers.IO) {
            JsonLootLoader(handle.logger)
                .fromResource(KilleporterFactory::class.java)
                ?.loadInto(loot)
        }

        return KilleporterInstance(handle, level, map, kitLoader, loot)
    }
}

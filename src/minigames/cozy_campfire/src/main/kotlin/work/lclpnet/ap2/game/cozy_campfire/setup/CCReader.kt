package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture

class CCReader(
    private val map: GameMap,
    private val world: ServerLevel,
    private val logger: Logger
) {

    fun readBases(teams: Set<Team>): CompletableFuture<Map<Team, CCBase>> =
        CompletableFuture.supplyAsync { readBasesSync(teams) }

    private fun readBasesSync(teams: Set<Team>): Map<Team, CCBase> {
        val server = checkNotNull(world.server)
        val session = (server as MinecraftServerAccessor).storageSource
        val storage = session.getDimensionPath(world.dimension())
        val schematicsDir = storage.resolve("schematics")

        val basesJson: JSONObject = map.requireProperty("bases")
        val bases = mutableMapOf<Team, CCBase>()

        for (team in teams) {
            val id = team.key().id()
            val mapId: Identifier = map.descriptor.identifier

            if (!basesJson.has(id)) {
                logger.error("No base configured for team {} in map {}", id, mapId)
                continue
            }

            val base = readBase(basesJson.getJSONObject(id), id, mapId, schematicsDir) ?: continue
            bases[team] = base
        }

        return bases
    }

    private fun readBase(json: JSONObject, teamId: String, mapId: Identifier, schematicsDir: java.nio.file.Path): CCBase? {
        if (!json.has("bounds")) {
            logger.error("Base of team {} in map {} does not contain property 'bounds'", teamId, mapId)
            return null
        }

        val boundsArray: JSONArray = json.getJSONArray("bounds")
        val bounds = mutableListOf<BlockBox>()

        for (entry in boundsArray) {
            if (entry !is JSONArray) continue
            bounds.add(MapUtil.readBox(entry))
        }

        if (!json.has("campfire")) {
            logger.error("Base of team {} in map {} does not contain property 'campfire'", teamId, mapId)
            return null
        }

        val campfirePos: BlockPos = MapUtil.readBlockPos(json.getJSONArray("campfire"))

        if (!json.has("entity")) {
            logger.error("Base of team {} in map {} does not contain property 'entity'", teamId, mapId)
            return null
        }

        val entityUuid: UUID = UUID.fromString(json.getString("entity"))

        val doorPosTuple: JSONArray? = json.optJSONArray("door-pos")
        val doorPos: BlockPos? = doorPosTuple?.let { MapUtil.readBlockPos(it) }

        val doorSchem = readDoor(json, schematicsDir)

        return CCBase(bounds, campfirePos, entityUuid, doorSchem, doorPos)
    }

    private fun readDoor(json: JSONObject, schematicsDir: java.nio.file.Path): BlockStructure? {
        val name = json.optString("door-schematic").takeIf { it.isNotEmpty() } ?: return null
        val path = schematicsDir.resolve("$name.schem")

        return try {
            Files.newInputStream(path).use { input ->
                SchematicFormats.SPONGE_V2.reader().read(input, FabricBlockStateAdapter.getInstance())
            }
        } catch (e: IOException) {
            logger.error("Failed to read schematic {}", path, e)
            null
        }
    }
}

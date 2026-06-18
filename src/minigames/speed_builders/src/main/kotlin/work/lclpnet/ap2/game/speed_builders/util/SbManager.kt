package work.lclpnet.ap2.game.speed_builders.util

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import net.minecraft.world.scores.PlayerTeam
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.speed_builders.data.SbIsland
import work.lclpnet.ap2.game.speed_builders.data.SbModule
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import java.util.*
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

private val BASE_BUILD_DURATION = 25.seconds
private val MIN_BUILD_DURATION = 5.seconds
private val SUCCESSIVE_COMPLETION_REDUCTION = 7.seconds

class SbManager(
    private val islands: Map<UUID, SbIsland>,
    modules: List<SbModule>,
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val fastMode: Boolean,
    private val allPlayersCompleted: Runnable,
    private val lastPlayerRemaining: Runnable
) {
    private val modules: List<SbModule> = Collections.unmodifiableList(modules)
    private val logger = gameHandle.logger
    private val queue = mutableListOf<SbModule>()
    private val lastEdited = Object2LongOpenHashMap<UUID>()
    private val edited = mutableSetOf<UUID>()
    private val completed = mutableSetOf<UUID>()
    var buildingPhase: Boolean = false
    private var currentModule: SbModule? = null
    var team: PlayerTeam? = null
    private var successiveCompletion = 0
    private var round = 0
    private var roundResolved = false

    fun eachIsland(action: (SbIsland, ServerPlayer) -> Unit) {
        val playerManager = gameHandle.server.playerList
        islands.forEach { (uuid, island) ->
            val player = playerManager.getPlayer(uuid) ?: return@forEach
            action(island, player)
        }
    }

    fun canModify(player: ServerPlayer): Boolean = buildingPhase && !completed.contains(player.uuid)

    fun clearIslands() {
        for ((_, island) in activeIslands()) {
            island.clearBuildingArea(world)
        }
    }

    fun isWithinBuildingArea(player: ServerPlayer, pos: BlockPos): Boolean =
        islands[player.uuid]?.isWithinBuildingArea(pos) ?: false

    @Synchronized
    fun setModule(module: SbModule) {
        val scoreboardManager = gameHandle.scoreboardManager
        val playerManager = gameHandle.server.playerList

        for ((uuid, island) in activeIslands()) {
            playerManager.getPlayer(uuid) ?: continue

            if (!island.supports(module)) {
                logger.error("Module {} is incompatible with island {}", module, island)
                continue
            }

            island.clearBuildingArea(world)
            island.placeModulePreview(module, world, team!!, scoreboardManager)
        }

        currentModule = module
    }

    fun nextModule(): SbModule {
        if (queue.isEmpty()) {
            queue.addAll(modules)
            queue.shuffle(random)
        }

        if (queue.isEmpty()) throw IllegalStateException("There are no modules defined")

        return queue.removeFirst()
    }

    fun getWorstPlayer(): ServerPlayer? {
        val evaluation = evaluate()
        if (evaluation.isEmpty()) return null

        val minScore = evaluation.values.minOrNull() ?: return null

        return evaluation.entries
            .filter { (_, score) -> score == minScore }
            .maxByOrNull { (player, _) -> lastEdited.getOrDefault(player.uuid, Long.MAX_VALUE) }
            ?.key
    }

    private fun activeIslands(): Set<Map.Entry<UUID, SbIsland>> {
        val participants = gameHandle.participants
        return islands.entries.filter { (uuid, _) -> participants.isParticipating(uuid) }.toSet()
    }

    private fun evaluate(): Map<ServerPlayer, Int> {
        val module = currentModule ?: return emptyMap()
        val playerManager = gameHandle.server.playerList

        return activeIslands().mapNotNull { (uuid, island) ->
            val player = playerManager.getPlayer(uuid) ?: return@mapNotNull null
            player to island.evaluate(world, module)
        }.toMap()
    }

    fun getPreviewEntities(): List<Entity> =
        activeIslands().firstOrNull()?.value?.getPreviewEntities(world) ?: emptyList()

    fun onEdit(player: ServerPlayer) {
        if (currentModule == null || completed.contains(player.uuid)) return
        lastEdited.put(player.uuid, System.currentTimeMillis())
        edited.add(player.uuid)
    }

    fun tick() {
        checkPlayerPositions()
        processEdits()
    }

    private fun processEdits() {
        if (edited.isEmpty()) return

        val participants = gameHandle.participants
        val playerManager = gameHandle.server.playerList

        for (uuid in edited) {
            if (!participants.isParticipating(uuid)) continue
            val player = playerManager.getPlayer(uuid) ?: continue
            onEdited(player)
        }

        edited.clear()
    }

    private fun checkPlayerPositions() {
        val playerManager = gameHandle.server.playerList

        for ((uuid, island) in activeIslands()) {
            val player = playerManager.getPlayer(uuid) ?: continue
            if (!player.abilities.mayfly) continue
            if (island.movementBounds.contains(player.position())) continue
            island.teleport(player)
        }
    }

    private fun onEdited(player: ServerPlayer) {
        val inventory: Inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty || stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET)) continue
            return
        }

        logger.debug("Player {} has no items left", player.scoreboardName)

        val island = islands[player.uuid] ?: return
        if (!island.isCompleted(world, currentModule!!)) return
        if (!completed.add(player.uuid)) return

        logger.debug("Player {} has completed the building", player.scoreboardName)

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75f, 1.1f)

        val msg = gameHandle.translations.translateText(player, "completed")
            .formatted(ChatFormatting.GREEN)
        player.sendSystemMessage(msg)

        checkOverallCompletion()
    }

    private fun checkOverallCompletion() {
        if (roundResolved) return

        val participantCount = gameHandle.participants.count()

        if (completed.size >= participantCount) {
            logger.debug("All players completed their buildings")
            roundResolved = true
            allPlayersCompleted.run()
            return
        }

        if (fastMode && completed.size >= participantCount - 1) {
            logger.debug("Only one builder remaining, eliminating instantly (fast mode)")
            roundResolved = true
            lastPlayerRemaining.run()
        }
    }

    fun getIsland(player: ServerPlayer): SbIsland? = islands[player.uuid]

    fun allIslandsComplete(): Boolean {
        val playerManager = gameHandle.server.playerList
        return islands.all { (uuid, island) ->
            playerManager.getPlayer(uuid) ?: return@all true

            island.isCompleted(world, currentModule!!)
        }
    }

    fun incrementSuccessiveCompletion() { successiveCompletion++ }
    fun resetSuccessiveCompletion() { successiveCompletion = 0 }

    fun reset() {
        completed.clear()
        lastEdited.clear()
        edited.clear()
        roundResolved = false
    }

    fun getBuildingDuration(): Duration {
        val module = currentModule ?: return BASE_BUILD_DURATION
        val complexity = module.getComplexity()
        val bonusTime = floor((maxOf(0, complexity - 64) * 0.4)).seconds
        val reduction = maxOf(0.seconds, successiveCompletion * SUCCESSIVE_COMPLETION_REDUCTION)
        return maxOf(MIN_BUILD_DURATION, BASE_BUILD_DURATION + bonusTime - reduction)
    }

    fun incrementRound() { round++ }

    fun getRoundsCompleted(player: ServerPlayer, winner: Boolean): Int =
        if (completed.contains(player.uuid) || winner) round + 1 else round
}

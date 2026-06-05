package work.lclpnet.ap2.game.pig_race.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.movement.TickMovementObserver
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.Checkpoint
import work.lclpnet.gaco.ds.Collider
import work.lclpnet.gaco.math.SplinePath
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*
import java.util.stream.Collectors
import kotlin.math.ceil
import kotlin.math.max

const val DEBUG_PROGRESS = false

fun createSegmentedPath(path: SplinePath, checkpoints: List<Checkpoint>, logger: Logger): SegmentedPath {
    data class UnorderedMarker(val box: BlockBox, val progress: Double)

    val markers = checkpoints.stream()
        .map { checkpoint ->
            val progress = path.getProgress(checkpoint.pos())
            UnorderedMarker(checkpoint.bounds(), progress)
        }
        .sorted(Comparator.comparing { it.progress })
        .collect(Collectors.toCollection(::ArrayList))

    val first = markers.first()
    markers[0] = UnorderedMarker(first.box, 0.0)

    val segments = ArrayList<SegmentedPath.Segment>()

    for (i in markers.indices) {
        val marker = markers[i]
        val segmentMarker = SegmentedPath.Marker(marker.box, marker.progress)

        val from = marker.progress
        val to = if (i == markers.size - 1) 1.0 else markers[i + 1].progress

        val subpath = subpathOf(path, from, to, logger)
            .orElseThrow { IllegalStateException("Failed to create subpath for segmented path") }

        segments.add(SegmentedPath.Segment(subpath, segmentMarker, i, to - from))
    }

    return SegmentedPath(segments)
}

fun subpathOf(path: SplinePath, from: Double, to: Double, logger: Logger): Optional<SplinePath> {
    val totalSamples = 100
    val relativeLength = to - from
    val samples = max(2, ceil(totalSamples * relativeLength).toInt())

    val keypoints = ArrayList<Vec3>(samples)

    for (i in 0 until samples) {
        keypoints.add(path.samplePosition(from + i * relativeLength / (samples - 1)))
    }

    return SplinePath.create(keypoints, logger)
}

class SegmentedPath(private val segments: List<Segment>) {

    private val playerSegments = Object2IntOpenHashMap<UUID>()
    val combinedLength: Double = segments.sumOf { it.path.length }

    init {
        require(segments.isNotEmpty()) { "At least one marker is required" }
    }

    fun init(
        participants: Participants,
        scheduler: TaskScheduler,
        hooks: HookRegistrar,
        server: MinecraftServer,
        debugController: DebugController
    ) {
        val movementObserver = TickMovementObserver(ChunkedCollisionDetector(), participants::isParticipating)
        movementObserver.init(scheduler, hooks, server)

        for (segment in segments) {
            movementObserver.whenEntering(segment.marker) { player -> onReachSegmentMarker(player, segment) }
        }

        if (DEBUG_PROGRESS) {
            debugSegments(participants, scheduler, debugController)
        }
    }

    private fun debugSegments(participants: Participants, scheduler: TaskScheduler, debugController: DebugController) {
        val colors = arrayOf(
            Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.RED_CONCRETE,
            Blocks.ORANGE_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.CYAN_CONCRETE
        )

        for (segment in segments) {
            val debugger = SplinePathDebugger(debugController, segment.path)

            val pathColor = colors[segment.index % colors.size].defaultBlockState()
            debugger.renderPath(ceil(1000 * segment.relativeLength).toInt(), pathColor)

            debugger.renderLiveProgress({ participants }, scheduler) { entity ->
                if (entity !is ServerPlayer) return@renderLiveProgress -1
                if (getSegmentIndex(entity) == segment.index) DyeColor.LIME.textureDiffuseColor
                else DyeColor.RED.textureDiffuseColor
            }
        }
    }

    private fun onReachSegmentMarker(player: ServerPlayer, segment: Segment) {
        val nextSegmentIndex = getNextSegmentIndex(player)
        if (segment.index != nextSegmentIndex) return
        playerSegments.put(player.uuid, nextSegmentIndex)
    }

    private fun getNextSegmentIndex(player: ServerPlayer): Int =
        (getSegment(player).index + 1) % segments.size

    fun getSegment(player: ServerPlayer): Segment = segments[getSegmentIndex(player)]

    private fun getSegmentIndex(player: ServerPlayer): Int =
        playerSegments.getOrDefault(player.uuid, 0)

    fun getProgress(player: ServerPlayer): Double {
        val segment = getSegment(player)
        val relativeProgress = segment.path.getProgress(player.position())
        return segment.marker.progress + relativeProgress * segment.relativeLength
    }

    fun isInLastSegment(player: ServerPlayer): Boolean = getSegmentIndex(player) == segments.size - 1

    data class Segment(val path: SplinePath, val marker: Marker, val index: Int, val relativeLength: Double)

    data class Marker(val box: BlockBox, val progress: Double) : Collider {
        override fun collidesWith(x: Double, y: Double, z: Double) = box.collidesWith(x, y, z)
        override fun collidesWith(box: AABB) = this.box.collidesWith(box)
        override fun min(): BlockPos = box.min()
        override fun max(): BlockPos = box.max()
    }
}

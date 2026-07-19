package work.lclpnet.ap2.game.guess_it.challenge

import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.*
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.entity.vehicle.minecart.Minecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.MobSpawner
import work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.util.world.AdjacentBlocks
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.access.entity.FireworkEntityAccess
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import kotlin.math.roundToInt

private const val TURN_CHANCE = 0.2f
private val DURATION_TICKS = Ticks.seconds(16)
private val MAX_RUNTIME_TICKS = Ticks.seconds(35)

class MinecartChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier
) : Challenge, LongerChallenge, SchedulerAction {

    private var powerPos: BlockPos? = null
    private var minecartUuid: UUID? = null
    private var onDone: Runnable? = null
    private var goal: BlockPos? = null
    private var finalTime = 0
    private var running = 0

    override fun id() = "minecart"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = DURATION_TICKS

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("minecart"))

        input.expectInput().validateFloat(translations, 3)

        generateTracks()
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = LocalizedFormat.format("%.3f", finalTime / 1000f)
        result.grantClosest3(gameHandle.participants.asSet, finalTime) { player ->
            choices.getFloat(player)?.let { (it * 1000).roundToInt() }
        }
    }

    override fun evaluateDeferred(callback: Runnable) {
        val startTime = System.currentTimeMillis()
        val powerPos = this.powerPos!!

        modifier.setBlockState(
            powerPos,
            Blocks.REDSTONE_BLOCK.defaultBlockState(),
            Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS
        )

        val up = powerPos.above()
        modifier.setBlockState(
            up,
            world.getBlockState(up).setValue(PoweredRailBlock.POWERED, true),
            Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS
        )

        onDone = Runnable {
            finalTime = (System.currentTimeMillis() - startTime).toInt()
            callback.run()
        }

        running = 0
        gameHandle.scheduler.interval(this, 1)
    }

    override fun run(info: RunningTask) {
        val entity = world.getEntity(minecartUuid!!)

        if (entity != null && !isOnGoal(entity) && ++running < MAX_RUNTIME_TICKS) return

        info.cancel()
        onDone!!.run()

        if (entity == null) return

        entity.indirectPassengers.forEach { it.discard() }
        entity.discard()

        val explosion = FireworkExplosion(
            FireworkExplosion.Shape.SMALL_BALL,
            IntList.of(0xff0000),
            IntList.of(),
            false,
            false
        )

        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        rocket.set(DataComponents.FIREWORKS, Fireworks(1, listOf(explosion)))

        val firework = FireworkRocketEntity(world, entity.x, entity.y, entity.z, rocket)
        world.addFreshEntity(firework)

        FireworkEntityAccess.explode(firework)
    }

    private fun isOnGoal(entity: Entity): Boolean {
        val goal = this.goal!!
        return goal.x == entity.blockX && goal.z == entity.blockZ
    }

    private fun generateTracks() {
        val positions = HashSet<BlockPos>()

        for (pos in findGroundPositions(blockShape, world)) {
            positions.add(pos.immutable())
        }

        if (positions.isEmpty()) {
            throw IllegalStateException("No ground positions in stage")
        }

        val trackCount = 30 + random.nextInt(71)

        val start = positions.stream().skip(random.nextInt(positions.size).toLong()).findFirst().orElseThrow()
        val adjacent: AdjacentBlocks = SimpleAdjacentBlocks({ positions.contains(it) }, 0)
        val tracks = Generator().generate(start, trackCount, adjacent)

        var nextPower = 0

        for (i in tracks.indices) {
            val track = tracks[i]
            val dir = track.dir

            val last = i == tracks.size - 1
            val nextDir = if (last) null else tracks[i + 1].dir
            var shape = getRailShape(dir, nextDir)

            val state: BlockState

            if (last) {
                shape = getRailShape(dir, null)
                state = Blocks.DETECTOR_RAIL.defaultBlockState().setValue(DetectorRailBlock.SHAPE, shape)
            } else if (nextPower-- <= 0 && dir == nextDir) {
                nextPower = 5 + random.nextInt(9)
                state = Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, shape)
                    .setValue(PoweredRailBlock.POWERED, true)
            } else {
                state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape)
            }

            if (i > 0 && state.isOf(Blocks.POWERED_RAIL)) {
                modifier.setBlockState(track.pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS)
            }

            modifier.setBlockState(track.pos, state, Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE)
        }

        val firstTrack = tracks[0]
        val buffer = firstTrack.pos.relative(firstTrack.dir.opposite)

        modifier.setBlockState(buffer, Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE)

        val x = firstTrack.pos.x + 0.5
        val y = firstTrack.pos.y.toDouble()
        val z = firstTrack.pos.z + 0.5

        val minecart = Minecart(EntityTypes.MINECART, world)
        minecart.setPosRaw(x, y, z)

        val villager = Villager(EntityTypes.VILLAGER, world)
        MobSpawner(world, random, IndexedSet<UUID>()).randomizeEntity(villager)
        villager.setPosRaw(x, y, z)

        modifier.spawnEntity(minecart)
        modifier.spawnEntity(villager)

        villager.startRiding(minecart, true, false)

        powerPos = firstTrack.pos.below()
        minecartUuid = minecart.getUUID()
        goal = tracks[tracks.size - 1].pos
    }

    private data class PosDir(val pos: BlockPos, val dir: Direction)

    private inner class Generator {
        val open = HashSet<BlockPos>()
        val closed = HashSet<BlockPos>()
        val path = Stack<PosDir>()

        fun generate(start: BlockPos, length: Int, adjacent: AdjacentBlocks): Array<PosDir> {
            val directions = ArrayList<Direction>()

            for (pos in adjacent.iterate(start)) {
                val dx = pos.x - start.x
                val dz = pos.z - start.z

                val dir = Direction.getNearest(dx, 0, dz, null)

                if (dir != null) {
                    directions.add(dir)
                }
            }

            if (directions.isEmpty()) {
                throw IllegalStateException("Cannot find starting direction")
            }

            val direction = directions[random.nextInt(directions.size)]

            path.push(PosDir(start, direction))
            closed.add(start)

            // force the second path position
            var buffer = start.relative(direction)
            open.add(buffer)

            // block possible tracks besides the first track
            closed.add(start.relative(direction.clockWise))
            closed.add(start.relative(direction.counterClockWise))

            // ensure that there is buffer space in the other direction
            buffer = start.relative(direction.opposite)
            closed.add(buffer)

            while (path.isNotEmpty() && path.size < length) {
                val current = path.peek()
                val next = next(current)

                if (next == null) {
                    // backtracking
                    path.pop()
                    continue
                }

                open.remove(next.pos)
                closed.add(next.pos)
                path.push(next)

                for (pos in adjacent.iterate(next.pos)) {
                    if (closed.contains(pos)) continue

                    open.add(pos.immutable())
                }
            }

            if (path.size < 2) {
                throw IllegalStateException("Could not generate minecart tracks")
            }

            return path.toTypedArray()
        }

        private fun next(current: PosDir): PosDir? {
            var turnTried = false

            if (random.nextFloat() < TURN_CHANCE) {
                // try to turn
                val next = turn(current)

                if (next != null) {
                    return next
                }

                turnTried = true
            }

            // go straight
            val newPos = current.pos.relative(current.dir)

            if (open.contains(newPos)) {
                return PosDir(newPos, current.dir)
            }

            if (turnTried) {
                return null
            }

            // try to turn
            return turn(current)
        }

        private fun turn(current: PosDir): PosDir? {
            var turn = Turn.entries[random.nextInt(Turn.entries.size)]
            var newDir = turnDirection(current.dir, turn)
            var newPos = current.pos.relative(newDir)

            if (open.contains(newPos)) {
                return PosDir(newPos, newDir)
            }

            // try to turn the other way
            turn = turn.opposite()
            newDir = turnDirection(current.dir, turn)
            newPos = current.pos.relative(newDir)

            if (open.contains(newPos)) {
                return PosDir(newPos, newDir)
            }

            return null
        }
    }
}

private fun getRailShape(pre: Direction, post: Direction?): RailShape {
    if (post == null) {
        return when (pre) {
            NORTH, SOUTH -> RailShape.NORTH_SOUTH
            else -> RailShape.EAST_WEST
        }
    }

    // NS
    if (pre == NORTH && post == NORTH || pre == SOUTH && post == SOUTH) {
        return RailShape.NORTH_SOUTH
    }

    // EW
    if (pre == EAST && post == EAST || pre == WEST && post == WEST) {
        return RailShape.EAST_WEST
    }

    // NE
    if (pre == NORTH && post == EAST || pre == EAST && post == NORTH) {
        return RailShape.NORTH_EAST
    }

    // NW
    if (pre == NORTH && post == WEST || pre == WEST && post == NORTH) {
        return RailShape.NORTH_WEST
    }

    // SE
    if (pre == SOUTH && post == EAST || pre == EAST && post == SOUTH) {
        return RailShape.SOUTH_EAST
    }

    // SW
    return RailShape.SOUTH_WEST
}

private enum class Turn {
    LEFT,
    RIGHT;

    fun opposite(): Turn = entries[1 - ordinal]
}

private fun turnDirection(direction: Direction, turn: Turn): Direction = when (turn) {
    Turn.LEFT -> direction.counterClockWise
    Turn.RIGHT -> direction.clockWise
}

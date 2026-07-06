package work.lclpnet.ap2.task_rush.task

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.*
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.TeamColor
import work.lclpnet.ap2.core.hook.AnimalBreedCallback
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Be the first to breed two animals.
 * Chickens, cows and pigs are spawned around spawn and marked with a glowing outline, coloured by type,
 * so players can quickly find a pair to breed.
 */
object BreedAnimalsTask : OrderTask("breed_animals") {

    private const val RADIUS = 50.0
    private const val COUNT_PER_TYPE = 12

    private val types: List<Triple<EntityType<out Mob>, String, TeamColor>> = listOf(
        Triple(EntityTypes.CHICKEN, "tr_breed_chicken", TeamColor.YELLOW),
        Triple(EntityTypes.COW, "tr_breed_cow", TeamColor.DARK_RED),
        Triple(EntityTypes.PIG, "tr_breed_pig", TeamColor.LIGHT_PURPLE),
    )

    private val spawned = ArrayList<Entity>()
    private val teams = ArrayList<PlayerTeam>()

    override fun begin(env: TaskEnv) {
        val progress = start(env)

        spawnAnimals(env)

        AnimalBreedCallback.HOOK.registerWith(env.hooks) { breeder, _, _, _ ->
            if (env.players.isParticipating(breeder)) {
                env.translations.translateText("task.feedback.breed_animals")
                    .withStyle(ChatFormatting.GREEN)
                    .sendTo(breeder, true)

                progress.finish(breeder)
            }
        }
    }

    private fun spawnAnimals(env: TaskEnv) {
        val level = env.level

        for ((type, teamName, color) in types) {
            val team = env.scoreboardManager.createTeam(teamName)
            team.color = Optional.of(color)
            teams.add(team)

            repeat(COUNT_PER_TYPE) {
                val pos = randomSurfacePos(level, env.spawnPos)
                val animal = type.create(level, EntitySpawnReason.COMMAND) ?: return@repeat

                animal.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
                animal.setPersistenceRequired()
                animal.setGlowingTag(true)

                level.addFreshEntity(animal)
                env.scoreboardManager.joinTeam(animal, team)
                spawned.add(animal)
            }
        }
    }

    private fun randomSurfacePos(level: ServerLevel, spawn: BlockPos): BlockPos {
        val angle = level.random.nextDouble() * 2.0 * PI
        val distance = level.random.nextDouble() * RADIUS
        val x = spawn.x + (cos(angle) * distance).toInt()
        val z = spawn.z + (sin(angle) * distance).toInt()
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        return BlockPos(x, y, z)
    }

    override fun end(env: TaskEnv) {
        for (entity in spawned) {
            entity.discard()
        }
        spawned.clear()

        for (team in teams) {
            env.scoreboardManager.removeTeam(team.name)
        }
        teams.clear()
    }
}

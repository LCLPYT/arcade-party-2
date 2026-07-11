package work.lclpnet.ap2.task_rush.task

import net.minecraft.ChatFormatting
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.TeamColor
import work.lclpnet.ap2.core.hook.AnimalBreedCallback
import work.lclpnet.ap2.task_rush.util.TRSpawns
import work.lclpnet.ap2.task_rush.util.randomSurfacePos
import java.util.*

/**
 * Be the first to breed two animals.
 * Chickens, cows and pigs are spawned around spawn and marked with a glowing outline, coloured by type,
 * so players can quickly find a pair to breed.
 */
object BreedAnimalsTask : OrderTask("breed_animals") {

    data class AnimalSpawn(
        val type: EntityType<out Mob>,
        val teamColor: TeamColor,
        val count: Int,
    )

    private val types = listOf(
        AnimalSpawn(EntityTypes.CHICKEN, TeamColor.YELLOW, 16),
        AnimalSpawn(EntityTypes.COW, TeamColor.DARK_RED, 8),
        AnimalSpawn(EntityTypes.PIG, TeamColor.LIGHT_PURPLE, 8),
    )

    private val teams = ArrayList<PlayerTeam>()
    private val spawns = TRSpawns()

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

        for ((type, color, count) in types) {
            val typeId = EntityType.getKey(type)
            val teamName = "tr_${typeId.namespace}_${typeId.path.replace('/', '_')}"
            val team = env.scoreboardManager.createTeam(teamName)
            team.color = Optional.of(color)
            teams.add(team)

            repeat(count) {
                val pos = randomSurfacePos(level, env.spawnPos, 10.0, 75.0)
                val animal = type.create(level, EntitySpawnReason.COMMAND) ?: return@repeat

                animal.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
                animal.setPersistenceRequired()
                animal.setGlowingTag(true)

                level.addFreshEntity(animal)
                env.scoreboardManager.joinTeam(animal, team)
                spawns.add(animal)
            }
        }
    }


    override fun end(env: TaskEnv) {
        spawns.removeAll()

        for (team in teams) {
            env.scoreboardManager.removeTeam(team.name)
        }

        teams.clear()
    }
}

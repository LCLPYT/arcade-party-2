package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.ServerScoreboard
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.entity.Entity
import net.minecraft.world.scores.*
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.core.type.ApServerPlayerEntity
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.hook.LanguageChangedCallback
import java.util.function.Function

class CustomScoreboardManager(
    private val scoreboard: ServerScoreboard,
    val translations: Translations,
    private val playerManager: PlayerList
) {
    private val teams = HashSet<PlayerTeam>()
    private val objectives = HashSet<Objective>()
    private val virtualObjectives = ArrayList<VirtualScoreboardObjective>()

    fun init(hooks: HookRegistrar) {
        LanguageChangedCallback.HOOK.registerWith(hooks) { player, _, _ ->
            for (objective in virtualObjectives) {
                objective.update(player)
            }
        }

        PlayerConnectionHooks.JOIN.registerWith(hooks) { player ->
            for (objective in virtualObjectives) {
                objective.add(player)
            }
        }

        PlayerConnectionHooks.QUIT.registerWith(hooks) { player ->
            val team = scoreboard.getPlayersTeam(player.scoreboardName)

            if (team != null) {
                leaveTeam(player, team)
            }
        }
    }

    fun joinTeam(entity: Entity, team: PlayerTeam) {
        scoreboard.addPlayerToTeam(entity.scoreboardName, team)

        // manually set team color as teams themselves don't support arbitrary text color
        if (entity is ServerPlayer) {
            (entity as ApServerPlayerEntity).`ap2$setPlayerListName`(entity.getDisplayName())
        }
    }

    fun leaveTeam(entity: Entity, team: PlayerTeam) {
        val entityName = entity.scoreboardName

        if (scoreboard.getPlayersTeam(entityName) !== team) return

        scoreboard.removePlayerFromTeam(entityName, team)
    }

    fun joinTeam(players: Iterable<Entity>, team: PlayerTeam) {
        for (entity in players) {
            joinTeam(entity, team)
        }
    }

    fun createTeam(name: String): PlayerTeam {
        removeTeam(name)

        val team = scoreboard.addPlayerTeam(name)

        synchronized(this) {
            teams.add(team)
        }

        return team
    }

    fun removeTeam(name: String) {
        val team = scoreboard.getPlayerTeam(name) ?: return

        scoreboard.removePlayerTeam(team)

        synchronized(this) {
            teams.remove(team)
        }
    }

    @JvmOverloads
    fun createObjective(
        name: String,
        criterion: ObjectiveCriteria,
        displayName: Component,
        renderType: ObjectiveCriteria.RenderType,
        numberFormat: NumberFormat? = StyledFormat.SIDEBAR_DEFAULT
    ): Objective {
        removeObjective(name)

        val objective = scoreboard.addObjective(
            name,
            criterion,
            displayName,
            renderType,
            true,
            numberFormat
        )

        synchronized(this) {
            objectives.add(objective)
        }

        return objective
    }

    private fun removeObjective(name: String) {
        val objective = scoreboard.getObjective(name) ?: return

        scoreboard.removeObjective(objective)

        synchronized(this) {
            objectives.remove(objective)
        }
    }

    fun setScore(player: ScoreHolder, objective: Objective, score: Int) {
        val playerScore = getOrCreateScore(player, objective) ?: return

        playerScore.set(score)
    }

    fun removeScore(holder: ScoreHolder, objective: Objective) {
        scoreboard.resetSinglePlayerScore(holder, objective)
    }

    fun setNumberFormat(holder: ScoreHolder, objective: Objective, format: NumberFormat?) {
        val playerScore = getOrCreateScore(holder, objective) ?: return

        playerScore.numberFormatOverride(format)
    }

    fun setDisplayText(holder: ScoreHolder, objective: Objective, text: Component?) {
        val playerScore = getOrCreateScore(holder, objective) ?: return

        playerScore.display(text)
    }

    fun getOrCreateScore(holder: ScoreHolder, objective: Objective): ScoreAccess? {
        if (!objectives.contains(objective)) return null // objective is not associated with this instance

        return scoreboard.getOrCreatePlayerScore(holder, objective)
    }

    fun setDisplay(slot: DisplaySlot, objective: Objective?) {
        scoreboard.setDisplayObjective(slot, objective)
    }

    fun translateObjective(
        name: String,
        translationKey: String,
        vararg args: Any
    ): TranslatedScoreboardObjective {
        return translateObjective(name, ObjectiveCriteria.RenderType.INTEGER, translationKey, *args)
    }

    fun translateObjective(
        name: String,
        renderType: ObjectiveCriteria.RenderType,
        translationKey: String,
        vararg args: Any
    ): TranslatedScoreboardObjective {
        val objective = TranslatedScoreboardObjective(
            translations,
            playerManager,
            name,
            renderType,
            translationKey,
            arrayOf(*args)
        )

        virtualObjectives.add(objective)

        return objective
    }

    fun addVirtualObjective(objective: VirtualScoreboardObjective) {
        virtualObjectives.add(objective)
    }

    fun createDynamicObjective(name: String, title: Function<ServerPlayer, Component>): DynamicScoreboardObjective {
        return createDynamicObjective(name, ObjectiveCriteria.RenderType.INTEGER, title)
    }

    fun createDynamicObjective(
        name: String,
        renderType: ObjectiveCriteria.RenderType,
        title: Function<ServerPlayer, Component>
    ): DynamicScoreboardObjective {
        val objective = DynamicScoreboardObjective(name, renderType, title, playerManager)

        virtualObjectives.add(objective)

        return objective
    }

    @Synchronized
    fun unload() {
        for (team in teams) {
            scoreboard.removePlayerTeam(team)
        }

        teams.clear()

        for (objective in virtualObjectives) {
            objective.unload()
        }

        for (objective in objectives) {
            scoreboard.removeObjective(objective)
        }

        objectives.clear()
    }
}
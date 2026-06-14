package work.lclpnet.ap2.game.base

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.isParticipating
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.EliminationDataContainer
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.translate.text.TranslatedText

abstract class TeamEliminationGameInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap,
    teamManager: TeamManager
) : TeamGameInstance(gameHandle, world, map, teamManager) {

    override val data = EliminationDataContainer { team: Team ->
        createReference(team)
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected fun useSmoothDeath() {
        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            if (entity !is ServerPlayer || health > 0) return@registerWith false
            eliminate(entity)
            true
        }
    }

    protected fun eliminate(player: ServerPlayer) {
        if (isParticipating(player)) {
            gameHandle.deathMessages.eliminated(player).sendTo(allPlayers())

            gameHandle.participants.remove(player)
            onEliminated(player)
        }

        resetPlayer(player)
    }

    private fun resetPlayer(player: ServerPlayer) {
        gameHandle.playerUtil.resetPlayer(player)
        gameHandle.worldFacade.teleport(player)
    }

    protected fun eliminate(team: Team) {
        // eliminate all remaining team players first
        for (player in team.getPlayers()) {
            gameHandle.participants.remove(player)
            onEliminated(player)
            resetPlayer(player)
        }

        // now actually eliminate the team
        if (!teamManager.isParticipating(team)) return

        gameHandle.deathMessages.eliminated(team).sendTo(allPlayers())

        teamManager.setTeamEliminated(team)
    }

    protected fun eliminateAll(teams: Iterable<Team>, detail: TranslatedText? = null) {
        val toEliminate = mutableSetOf<Team>()

        for (team in teams) {
            if (!teamManager.isParticipating(team)) continue

            val key = team.key()
            val displayName = key.getDisplayName(gameHandle.translations)

            translate("ap2.game.team_eliminated", displayName)
                .formatted(ChatFormatting.GRAY)
                .sendTo(allPlayers())

            toEliminate.add(team)
        }

        // mark all teams as eliminated at the same moment
        data.addAll(toEliminate, detail)

        // deliberately use teams instead of toEliminate, to make sure there are no participating members anymore
        for (team in teams) {
            for (player in team.getPlayers()) {
                gameHandle.participants.remove(player)
                onEliminated(player)
                resetPlayer(player)
            }
        }

        // finally actually set the teams to eliminated, after the all team participants are eliminated
        for (team in toEliminate) {
            teamManager.setTeamEliminated(team)
        }
    }

    override fun teamEliminated(team: Team) {
        // make sure the team is tracked as eliminated
        data.add(team)

        super.teamEliminated(team)
    }

    protected fun onEliminated(player: ServerPlayer) {
        PlayerEliminatedCallback.HOOK.invoker().onEliminated(player)
    }
}
package work.lclpnet.ap2.turf_wars.util

import net.minecraft.ChatFormatting
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.turf_wars.CAMP_ELIMINATION_SECONDS
import work.lclpnet.ap2.turf_wars.CAMP_WARNING_SECONDS
import work.lclpnet.ap2.turf_wars.Phase

/**
 * Detects teams that camp inside their own base for too long during the fight phase. A team that
 * keeps every participant within its base bounds is warned once and eliminated if it never leaves.
 */
class CampingMonitor(
    private val gameHandle: MiniGameHandle,
    private val teamManager: TeamManager,
    private val teamInfos: Map<DyeTeamKey, TurfWarsTeamInfo>
) {

    private val absenceSeconds = mutableMapOf<DyeTeamKey, Int>()

    /**
     * Advances the camp timers by one second and returns the teams that should be eliminated for
     * camping. Warnings are emitted internally. Outside of the fight phase the timers are reset.
     */
    fun tick(phase: Phase): List<Team> {
        if (phase != Phase.Fight) {
            absenceSeconds.clear()
            return emptyList()
        }

        val participants: Participants = gameHandle.participants
        val toEliminate = mutableListOf<Team>()

        for (team in teamManager.teams) {
            val teamKey = team.key()

            if (teamKey !is DyeTeamKey) continue

            val base = teamInfos[teamKey]?.baseBounds ?: continue

            val anyOutOfBase = team.players.any { player ->
                participants.isParticipating(player) && !base.contains(player.position())
            }

            if (anyOutOfBase) {
                absenceSeconds[teamKey] = 0
                continue
            }

            val seconds = (absenceSeconds[teamKey] ?: 0) + 1
            absenceSeconds[teamKey] = seconds

            if (seconds >= CAMP_ELIMINATION_SECONDS) {
                absenceSeconds.remove(teamKey)
                toEliminate.add(team)
                continue
            }

            val remaining = CAMP_ELIMINATION_SECONDS - seconds

            if (remaining == CAMP_WARNING_SECONDS) {
                warn(team, remaining)
            }
        }

        return toEliminate
    }

    private fun warn(team: Team, remaining: Int) {
        gameHandle.translations.translateText("game.ap2.turf_wars.camp_warning", remaining)
            .formatted(ChatFormatting.RED)
            .sendTo(team.players)

        for (player in team.players) {
            player.playNotifySound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.7f, 1f)
        }
    }
}

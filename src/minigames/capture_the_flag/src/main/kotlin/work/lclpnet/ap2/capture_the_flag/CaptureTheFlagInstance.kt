package work.lclpnet.ap2.capture_the_flag

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.scores.Team.CollisionRule
import work.lclpnet.ap2.ext.mc.setBlocks
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.TeamGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.game.map.GameMap

class CaptureTheFlagInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    teamManager: TeamManager,
    val schema: CtfSchema,
) : TeamGameInstance(gameHandle, level, map, teamManager) {

    override val data = IntScoreDataContainer(this::createReference)
    lateinit var teamInfo: List<CtfTeamInfo>

    init {
        useOldCombat()

        teamManager.setUseColorCodes(true)
    }

    override fun prepare() {
        teamInfo = setupTeams()

        for (info in teamInfo) {
            val team = teamManager.getTeam(info) ?: continue

            for (player in team.players) {
                player.teleport(info.spawn)
            }
        }
    }

    fun setupTeams(): List<CtfTeamInfo> {
        val team1Key = map.properties.optString("team1Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.RED
        val team2Key = map.properties.optString("team2Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.BLUE

        require(team1Key != team2Key) { "Team colors cannot be the same" }

        val team1Info = CtfTeamInfo(
            spawn = schema.team1Spawn!!,
            flagPosition = schema.team1FlagPos!!,
            gate = schema.team1SpawnGate!!,
            key = team1Key
        )

        val team2Info = CtfTeamInfo(
            spawn = schema.team2Spawn!!,
            flagPosition = schema.team2FlagPos!!,
            gate = schema.team2SpawnGate!!,
            key = team2Key
        )

        teamManager.partitionIntoTeams(players(), setOf(team1Key, team2Key))

        teamManager.minecraftTeams.forEach { team ->
            team.isAllowFriendlyFire = false
            team.setSeeFriendlyInvisibles(true)
            team.collisionRule = CollisionRule.PUSH_OTHER_TEAMS
        }

        return listOf(team1Info, team2Info)
    }

    override fun go() {
        for ((_, _, gate) in teamInfo) {
            level.setBlocks(gate, Blocks.AIR)
        }
    }
}

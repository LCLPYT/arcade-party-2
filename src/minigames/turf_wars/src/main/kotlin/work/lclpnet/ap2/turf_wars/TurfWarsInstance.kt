package work.lclpnet.ap2.turf_wars

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.TeamKey
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.turf_wars.util.TurfManager
import work.lclpnet.ap2.turf_wars.util.TurfWarsTeamInfo

val TEAM_1: TeamKey = DyeTeamKey.RED
val TEAM_2: TeamKey = DyeTeamKey.BLUE

class TurfWarsInstance(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<TurfWarsSchema> = useSchema(TurfWarsSchema::class.java)
    lateinit var turfManager: TurfManager

    fun setupTeams(): List<TurfWarsTeamInfo> {
        val team1Key = map.properties.optString("team1Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.RED
        val team2Key = map.properties.optString("team2Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.BLUE

        require(team1Key != team2Key) { "Team colors cannot be the same" }

        val schema = schemaHolder.get()

        val team1Info = TurfWarsTeamInfo(
            spawn = schema.team1Spawn!!,
            baseBounds = schema.team1Base!!,
            initialTurf = schema.team1Turf!!,
            teamKey = team1Key
        )

        val team2Info = TurfWarsTeamInfo(
            spawn = schema.team2Spawn!!,
            baseBounds = schema.team2Base!!,
            initialTurf = schema.team2Turf!!,
            teamKey = team1Key
        )

        teamManager.partitionIntoTeams(players(), setOf(team1Key, team2Key))

        return listOf(team1Info, team2Info)
    }

    override fun prepare() {
        val teams = setupTeams()

        turfManager = TurfManager(
            teams.map { it.initialTurf },
            teams.map { it.key() }
        )
    }

    override fun go() {

    }
}
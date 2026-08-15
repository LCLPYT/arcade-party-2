package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.ap2.game.team.TeamKeyable
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.PositionRotation

data class CtfTeamInfo(
    val spawn: PositionRotation,
    val flagPosition: BlockPos,
    val gate: BlockBox,
    override val key: TeamKey,
) : TeamKeyable
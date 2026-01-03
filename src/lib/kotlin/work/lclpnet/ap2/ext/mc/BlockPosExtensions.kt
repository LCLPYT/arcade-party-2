package work.lclpnet.ap2.ext.mc

import net.minecraft.core.BlockPos

operator fun BlockPos.unaryMinus(): BlockPos = this.multiply(-1)

operator fun BlockPos.plus(other: BlockPos): BlockPos = this.offset(other)

operator fun BlockPos.minus(other: BlockPos): BlockPos = this.subtract(other)

operator fun BlockPos.times(scalar: Int) = BlockPos(
    x * scalar,
    y * scalar,
    z * scalar,
)

operator fun Int.times(pos: BlockPos) = BlockPos(
    pos.x * this,
    pos.y * this,
    pos.z * this,
)

operator fun BlockPos.div(scalar: Int) = BlockPos(
    x / scalar,
    y / scalar,
    z / scalar,
)

operator fun BlockPos.rangeTo(other: BlockPos): Iterable<BlockPos> =
    BlockPos.betweenClosed(this, other)
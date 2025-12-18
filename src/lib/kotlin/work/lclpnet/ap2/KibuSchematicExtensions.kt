package work.lclpnet.ap2

import net.minecraft.core.BlockPos
import work.lclpnet.kibu.mc.KibuBlockPos

fun KibuBlockPos.toMinecraft(): BlockPos = BlockPos(x, y, z)
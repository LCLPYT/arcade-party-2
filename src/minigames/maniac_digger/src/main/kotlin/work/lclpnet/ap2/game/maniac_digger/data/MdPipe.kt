package work.lclpnet.ap2.game.maniac_digger.data

import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.ds.BlockBox

data class MdPipe(val spawn: Vec3, val bounds: BlockBox, val path: MdPipePath)

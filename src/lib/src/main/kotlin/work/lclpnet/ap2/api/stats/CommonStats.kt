package work.lclpnet.ap2.api.stats

object CommonStats {
    @JvmField
    val Score = Stat("score", 0)

    @JvmField
    val Kills = Stat("kills", 0)

    @JvmField
    val Deaths = Stat("deaths", 0, higherIsBetter = false)

    @JvmField
    val KillDeathRatio = Stat("kd", 0f)
}
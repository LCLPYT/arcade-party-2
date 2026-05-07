package work.lclpnet.ap2.ext.mc

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.clock.WorldClocks

fun ServerLevel.setDayTime(ticks: Int) {
    level.registryAccess().get(WorldClocks.OVERWORLD).ifPresent {
        level.clockManager().setTotalTicks(it, ticks.toLong())
    }
}

fun ServerLevel.setWeatherParameters(clearTicks: Int, rainTicks: Int, raining: Boolean, thundering: Boolean) {
    weatherData.setClearWeatherTime(clearTicks)
    weatherData.setRainTime(rainTicks)
    weatherData.setThunderTime(rainTicks)
    weatherData.setRaining(raining)
    weatherData.setThundering(thundering)
}
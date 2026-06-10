package work.lclpnet.ap2.game

fun interface MiniGameFactory {
    suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance
}
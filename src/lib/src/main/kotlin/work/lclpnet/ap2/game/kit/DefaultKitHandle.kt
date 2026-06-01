package work.lclpnet.ap2.game.kit

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations

data class DefaultKitHandle(
    override val gameId: Identifier,
    override val hooks: HookRegistrar,
    override val scheduler: TaskScheduler,
    override val translations: Translations,
    override val registries: RegistryAccess,
    override val readView: KitReadView
) : KitHandle {

    companion object {

        @JvmStatic
        fun of(gameHandle: MiniGameHandle, registries: RegistryAccess, readView: KitReadView): DefaultKitHandle =
            DefaultKitHandle(
                gameId = gameHandle.getGameInfo().getId(),
                hooks = gameHandle.getHooks(),
                scheduler = gameHandle.getScheduler(),
                translations = gameHandle.getTranslations(),
                registries = registries,
                readView = readView
            )
    }
}

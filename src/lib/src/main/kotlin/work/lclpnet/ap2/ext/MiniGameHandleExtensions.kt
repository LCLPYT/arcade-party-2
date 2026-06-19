package work.lclpnet.ap2.ext

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.kibu.title.Title

fun MiniGameHandle.sendGo(player: ServerPlayer) {
    val text = translations.translateText("ap2.go")
        .withStyle(ChatFormatting.RED)
        .translateFor(player)

    Title.get(player).title(text, Component.empty(), 5, 20, 5)

    player.playNotifySound(SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0f)
}

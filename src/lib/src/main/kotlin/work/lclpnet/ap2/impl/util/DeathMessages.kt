package work.lclpnet.ap2.impl.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.core.hook.PlayerDeathMessageCallback
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TextTranslatable
import work.lclpnet.kibu.translate.text.TranslatedText
import work.lclpnet.kibu.translate.util.LocaleUtil

class DeathMessages(private val translations: Translations) {

    fun root(key: String, vararg args: Any?): TranslatedText {
        return root(translations.translateText(key, *args))
    }

    fun root(text: TranslatedText): TranslatedText {
        return text.withStyle(ChatFormatting.GRAY)
    }

    fun wrap(player: Player): Any {
        return wrap(player.displayName)
    }

    fun wrap(obj: Any?): Any {
        if (obj is Component) {
            val style = obj.style
            val hoverEvent = style.hoverEvent

            // text with entity hover action indicates an entity display name
            // if the display name already has a color, keep it as is
            if (hoverEvent != null && hoverEvent.action() == HoverEvent.Action.SHOW_ENTITY && style.color != null) {
                return obj
            }
        }

        return FormatWrapper.styled(obj, ChatFormatting.YELLOW)
    }

    fun eliminated(player: ServerPlayer): TranslatedText {
        return root(ELIMINATED, wrap(player))
    }

    fun killedBy(victim: ServerPlayer, killer: ServerPlayer): TranslatedText {
        return root(KILLED_BY, wrap(victim), wrap(killer))
    }

    fun shotBy(victim: ServerPlayer, killer: ServerPlayer): TranslatedText {
        return root(SHOT_BY, wrap(victim), wrap(killer))
    }

    fun eliminated(team: Team): TranslatedText {
        return eliminated(team.key)
    }

    fun eliminated(key: TeamKey): TranslatedText {
        val displayName = key.getDisplayName(translations)

        return root(TEAM_ELIMINATED, displayName)
    }

    fun getDeathMessage(player: ServerPlayer, source: DamageSource?): TranslatedText {
        val content = player.combatTracker.deathMessage.contents

        if (content is TranslatableContents) {
            val key = content.key

            if (translations.translator.hasTranslation("en_us", key)) {
                return root(key, *content.args.map { wrap(it) }.toTypedArray())
            }
        }

        if (source == null) {
            return eliminated(player)
        }

        val attacker = source.entity

        if (attacker !is ServerPlayer) {
            return eliminated(player)
        }

        val directSource = source.directEntity

        if (directSource is Projectile && directSource !is Snowball) {
            return shotBy(player, attacker)
        }

        return killedBy(player, attacker)
    }

    /**
     * Builds a death message from the victim's combat tracker, annotating the killer's name with their
     * remaining health (e.g. `Killer (8.5 ♥)`).
     *
     * The victim may be any [LivingEntity] (e.g. an NPC), not just a player.
     *
     * @return the annotated message, or `null` if the death is not a translatable message caused by a living entity.
     */
    fun getDeathMessageWithKillerHealth(victim: LivingEntity, source: DamageSource): TranslatedText? {
        val content = victim.combatTracker.deathMessage.contents

        if (content !is TranslatableContents) return null

        val killer = source.entity

        if (killer !is LivingEntity) return null

        val args = content.args.map { arg ->
            if (arg !is Component) return@map arg

            when (arg.string) {
                victim.displayName.string -> Component.literal(arg.string).withStyle(ChatFormatting.YELLOW)
                killer.displayName.string -> killerWithHealth(killer)
                else -> arg
            }
        }

        return root(content.key, *args.toTypedArray())
    }

    fun killerWithHealth(killer: LivingEntity): TextTranslatable = TextTranslatable { language ->
        val locale = LocaleUtil.getLocale(language)
        val health = "%.1f ♥".format(locale, killer.health / 2f)

        Component.empty()
            .append(killer.displayName.copy().withStyle(ChatFormatting.YELLOW))
            .append(" (")
            .append(Component.literal(health).withStyle(ChatFormatting.RED))
            .append(")")
    }

    fun replaceVanillaDeathMessages(world: ServerLevel, hooks: HookRegistrar) {
        world.gameRules.set(GameRules.SHOW_DEATH_MESSAGES, false, world.server)

        PlayerDeathMessageCallback.HOOK.registerWith(hooks) { player, source, _ ->
            val server = player.level().server

            val msg = getDeathMessage(player, source)
            msg.sendTo(PlayerLookup.all(server))

            msg.translateFor(player)  // shown in death screen, but not sent to chat because of the game rule
        }
    }

    companion object {
        const val ELIMINATED = "ap2.game.eliminated"
        const val TEAM_ELIMINATED = "ap2.game.team_eliminated"
        const val KILLED_BY = "ap2.pvp.killed_by"
        const val SHOT_BY = "ap2.pvp.shot_by"
    }
}

package work.lclpnet.ap2.api.game.data

import net.minecraft.server.level.ServerPlayer

/**
 * Creates a reference for the subject of a player.
 * The subject can be the player itself, or the team the player is in.
 * If the player has no subject, null will be returned.
 * @param Ref The SubjectRef type.
 */
fun interface PlayerSubjectRefFactory<Ref : SubjectRef?> : SubjectRefFactory<ServerPlayer, Ref> {
    override fun create(subject: ServerPlayer): Ref
}

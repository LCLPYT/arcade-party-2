package work.lclpnet.ap2.rapid_runner

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.util.disallowDropItem
import work.lclpnet.ap2.util.scoreboard.setupTranslatedSidebarObjective
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val DURATION = 1.minutes + 20.seconds

class RapidRunnerInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer { DoubleScoreDataContainer(it, detailKey = "ap2.score.blocks_away") }
    override val winManager = useFFAWinManager(null) { data }
    override val participantListener = useLastRemainingParticipantListener(winManager)

    init {
        useSurvivalMode()
        useFFAStats(winManager, data, CommonStats.DoubleScore, listOf())
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)

            if (isParticipating(player)) {
                giveCompass(player)
            }
        }

        setupObjective()

        useStartup(::go)
    }

    private fun giveCompass(player: ServerPlayer) {
        player.inventory.setItem(8, ItemStack(Items.COMPASS).apply {
            set(
                DataComponents.ITEM_NAME, translate("compass_name")
                    .withStyle(ChatFormatting.GOLD)
                    .translateFor(player)
            )
        })
    }

    private fun setupObjective() {
        val objective = setupTranslatedSidebarObjective(gameHandle.scoreboardManager, "game.ap2.rapid_runner.distance")

        useScoreboardStatsSync(data, objective)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }
    }

    private fun go() {
        walls.undo()

        runEveryTick {
            updateScore()
        }

        useTaskTimer(DURATION).whenDone {
            winManager.complete()
        }
        
        useProtector { 
            allowAll()
            
            ProtectionTypes.ALLOW_DAMAGE.disallow(this) { victim, source ->
                (victim is ServerPlayer && source.entity is ServerPlayer) || source.isOf(DamageTypes.FALL)
            }
        }

        disallowDropItem(hooks) {
            it.isOf(Items.COMPASS)
        }

        PlayerSpawnLocationCallback.HOOK.registerWith(hooks) { data ->
            if (isParticipating(data.player)) {
                giveCompass(data.player)
            }
        }
    }

    private fun updateScore() {
        val spawn = Vec3.atCenterOf(level.respawnData.pos())

        for (player in players()) {
            val dist = player.position().subtract(spawn).horizontalDistance()

            data.setScore(player, dist)
        }
    }
}
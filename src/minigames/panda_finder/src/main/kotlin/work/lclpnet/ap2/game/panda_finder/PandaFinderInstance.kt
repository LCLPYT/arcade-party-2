package work.lclpnet.ap2.game.panda_finder

import it.unimi.dsi.fastutil.ints.IntList
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.createFFAStats
import work.lclpnet.ap2.game.util.usePlayerDynamicTaskDisplay
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.NotOccupiedBlockPredicate
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.FireworkEntityAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*
import kotlin.time.Duration.Companion.seconds

const val WIN_SCORE = 3
const val CLOSE_CALL_DISTANCE = 10.0

val PandasClicked = Stat("pandas_clicked", 0)
val AvgSpawnDistance = Stat("avg_spawn_distance", 0f)
val CloseCalls = Stat("close_calls", 0)
val PandasStolen = Stat("pandas_stolen", 0)
val PandasLost = Stat("pandas_lost", 0)
val Cooldowns = Stat("cooldowns", 0)

class PandaFinderInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    override val data = IntScoreDataContainer(PlayerRef::create)
    private val random = Random()
    private val spamManager = SpamManager()
    private lateinit var pandaManager: PandaManager
    private lateinit var bossBar: DynamicTranslatedPlayerBossBar
    private val stats = createFFAStats(winManager, data, CommonStats.IntScore, listOf(
        PandasClicked, AvgSpawnDistance, CloseCalls, PandasStolen, PandasLost, Cooldowns
    ))
    private var round = 0

    override fun prepare() {
        scanWorld()
        readImages()

        bossBar = usePlayerDynamicTaskDisplay(
            FormatWrapper.styled(0, ChatFormatting.YELLOW),
            FormatWrapper.styled(WIN_SCORE, ChatFormatting.YELLOW)
        )
    }

    override fun go() {
        val hooks = gameHandle.hooks

        PlayerInteractionHooks.USE_ENTITY.registerWith(hooks) { player, _, hand, entity, _ ->
            onUseEntity(player, hand, entity)
            InteractionResult.PASS
        }

        PlayerInteractionHooks.ATTACK_ENTITY.registerWith(hooks) { player, _, hand, entity, _ ->
            onUseEntity(player, hand, entity)
            InteractionResult.PASS
        }

        setupScoreboard()
        nextRound()
    }

    private fun setupScoreboard() {
        val scoreboardManager = gameHandle.scoreboardManager

        val objective = scoreboardManager.translateObjective("score",
                ObjectiveCriteria.RenderType.INTEGER, "ap2.score")
            .formatted(ChatFormatting.YELLOW, ChatFormatting.BOLD)

        objective.setSlot(DisplaySlot.SIDEBAR)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }

        useScoreboardStatsSync(data, objective)
    }

    private fun nextRound() {
        val round = ++this.round

        pandaManager.next()

        val pandaPositions = pandaManager.getSearchedPandaPositions()

        if (pandaPositions.isNotEmpty()) {
            for (participant in gameHandle.participants) {
                val roundAvg = pandaPositions.map { participant.position().distanceTo(it) }.average().toFloat()

                stats.modify(participant, AvgSpawnDistance) { oldAvg ->
                    oldAvg + (roundAvg - oldAvg) / round
                }
            }
        }

        val players = PlayerLookup.all(gameHandle.server)
        val translations = gameHandle.translations

        pandaManager.getLocalizedPandaGene()?.let { key ->
            translations.translateText("game.ap2.panda_finder.find",
                    FormatWrapper.styled(translations.translateText(key), ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GREEN).sendTo(players)
        }
    }

    private fun onRoundOver() {
        val maxScore = data.bestScore.orElse(0) ?: 0

        if (maxScore >= WIN_SCORE) {
            winManager.complete()
            return
        }

        runAfter(3.seconds) {
            nextRound()
        }
    }

    private fun scanWorld() {
        val map = map
        val start = MapUtil.readBlockPos(map.requireProperty("search-start"))
        val bounds = MapUtil.readBox(map.requireProperty("bounds"))
        val exclude = MapUtil.readBox(map.requireProperty("search-exclude"))

        val world = level

        val predicate = NotOccupiedBlockPredicate(world).and { pos ->
            bounds.contains(pos) && !exclude.contains(pos)
        }

        val adjacent = SimpleAdjacentBlocks(predicate, 1)
        val scanner = BfsWorldScanner(adjacent)

        val spaceFinder = SizedSpaceFinder.create(world, EntityType.PANDA)
        val spaces = spaceFinder.findSpaces(scanner.scan(start))

        pandaManager = PandaManager(gameHandle.logger, spaces, random, world, gameHandle.participants)
    }

    private fun readImages() {
        pandaManager.readImages(map.requireProperty("images"))
    }

    private fun onUseEntity(player: Player, hand: InteractionHand, entity: Entity) {
        if (entity !is Panda || player !is ServerPlayer
            || player.hasEffect(MobEffects.BLINDNESS) || hand != InteractionHand.MAIN_HAND) return

        if (spamManager.interact(player)) {
            onCooldownReached(player)
            return
        }

        stats.increment(player, PandasClicked)

        if (!pandaManager.isSearchedPanda(entity)) return

        pandaFound(player, entity)
    }

    private fun onCooldownReached(player: ServerPlayer) {
        stats.increment(player, Cooldowns)

        player.sendSystemMessage(gameHandle.translations.translateText(player, "game.ap2.panda_finder.cooldown")
            .formatted(ChatFormatting.RED))

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 0.5f, 1.5f)
        player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, Ticks.seconds(3), 1, false, false))
    }

    private fun pandaFound(player: ServerPlayer, panda: Panda) {
        val searchedPositions = pandaManager.getSearchedPandaPositions()

        pandaManager.setFound()

        val foundPos = panda.position()
        var stolen = false

        for (participant in gameHandle.participants) {
            if (participant == player) continue

            if (searchedPositions.any { pos -> participant.position().distanceTo(pos) <= CLOSE_CALL_DISTANCE }) {
                stats.increment(participant, CloseCalls)
            }

            if (participant.position().distanceTo(foundPos) <= CLOSE_CALL_DISTANCE) {
                stats.increment(participant, PandasLost)
                stolen = true
            }
        }

        if (stolen) stats.increment(player, PandasStolen)

        data.addScore(player, 1)
        bossBar.setArgument(player, 0, FormatWrapper.styled(data.getScore(player), ChatFormatting.YELLOW))

        val translations = gameHandle.translations
        val players = PlayerLookup.all(gameHandle.server)

        translations.translateText("game.ap2.panda_finder.panda_found",
                FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW))
            .formatted(ChatFormatting.GRAY).sendTo(players)

        val participants = gameHandle.participants

        for (serverPlayer in players) {
            if (participants.isParticipating(serverPlayer) && serverPlayer != player) {
                ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.5f, 1f)
            } else {
                ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1f)
            }
        }

        val rocket = ItemStack(Items.FIREWORK_ROCKET)
        val explosion = FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(0xff0000), IntList.of(), false, false)
        rocket.set(DataComponents.FIREWORKS, Fireworks(1, listOf(explosion)))

        val world = level
        val firework = FireworkRocketEntity(world, panda.x, panda.y, panda.z, rocket)
        world.addFreshEntity(firework)
        FireworkEntityAccess.explode(firework)

        onRoundOver()
    }
}

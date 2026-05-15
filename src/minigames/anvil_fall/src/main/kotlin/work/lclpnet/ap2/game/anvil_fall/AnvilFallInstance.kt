package work.lclpnet.ap2.game.anvil_fall

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedBossBar
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.access.entity.FallingBlockAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*

const val DIRECT_ANVIL_CHANCE = 0.02
const val SPREAD_RADIUS = 8
const val INITIAL_DELAY = 5
val INITIAL_DELAY_DECREASE_INTERVAL = Ticks.seconds(2)
val INCREASE_INTERVAL = Ticks.seconds(8)

class AnvilFallInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    private val directions = arrayOf(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.WEST)
    private val random = Random()
    private lateinit var amountDisplay: DynamicTranslatedBossBar
    private lateinit var setup: AnvilFallSetup
    private var playArea: BlockBox? = null
    private lateinit var center: Vec3

    override fun prepare() {
        commons().gameRuleBuilder()
            .set(GameRules.ENTITY_DROPS, false)
            .set(GameRules.FALL_DAMAGE, true)

        scanWorld()

        val scoreboardManager = gameHandle.scoreboardManager
        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER

        scoreboardManager.joinTeam(gameHandle.participants, team)

        val manager = VisibilityManager(team, Visibility.PARTIALLY_VISIBLE)
        val handler = VisibilityHandler(manager, gameHandle.translations, gameHandle.participants)
        handler.init(gameHandle.hooks)
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, damageSource ->
                if (damageSource.isOf(DamageTypes.FALLING_ANVIL) && entity is ServerPlayer) {
                    onHitByAnvil(entity)
                }
                false
            }
        }

        setupBossBar()
        startAnvilSpawning()

        PlayerMoveCallback.HOOK.registerWith(gameHandle.hooks) { player, _, to ->
            repelPlayer(player, to)
            false
        }

        for (player in gameHandle.participants) {
            repelPlayer(player, player.position())
        }
    }

    private fun setupBossBar() {
        val id = gameHandle.gameInfo.identifier("status")
        val key = "game.ap2.anvil_fall.status"
        val args = arrayOf<Any>(FormatWrapper.styled(0, ChatFormatting.YELLOW))

        val bossBar: TranslatedBossBar = gameHandle.translations.translateBossBar(id, key, *args)
            .with(gameHandle.bossBarProvider)
            .formatted(ChatFormatting.GREEN)

        amountDisplay = DynamicTranslatedBossBar(bossBar, key, args)

        bossBar.color = BossEvent.BossBarColor.GREEN
        bossBar.addPlayers(PlayerLookup.all(gameHandle.server))
        gameHandle.bossBarHandler.showOnJoin(bossBar)
    }

    private fun onHitByAnvil(player: ServerPlayer) {
        if (!gameHandle.participants.isParticipating(player)) return

        val pos = player.position()
        val x = pos.x(); val y = pos.y(); val z = pos.z()

        player.level().let { lvl ->
            lvl.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, 0.7f)
            lvl.sendParticles(ParticleTypes.LAVA, x, y, z, 25, 0.1, 0.1, 0.1, 0.0)
        }

        eliminate(player)
    }

    private fun scanWorld() {
        val box = MapUtil.readBox(map.requireProperty("anvil-box"))
        setup = AnvilFallSetup.scanWorld(world, box, random)
        playArea = MapUtil.readBox(map.requireProperty("play-area"))
        val spawn = MapUtil.readBlockPos(map.requireProperty("spawn"))
        center = Vec3(spawn.x + 0.5, 0.0, spawn.z + 0.5)
    }

    private fun startAnvilSpawning() {
        amountDisplay.setArgument(0, FormatWrapper.styled(20 / INITIAL_DELAY, ChatFormatting.YELLOW))

        var delay = INITIAL_DELAY
        var cooldown = 0
        var anvilAmount = 1
        var timer = 0
        var prevAmount = 0

        runEveryTick {
            val time = ++timer

            if (delay > 0) {
                if (time % INITIAL_DELAY_DECREASE_INTERVAL == 0) {
                    if (--delay == 0) timer = 0
                }

                if (cooldown > 0) {
                    cooldown--
                } else {
                    cooldown = delay

                    val obj: Any = if (delay > 0) {
                        if (20 % delay == 0) 20 / delay else "%.2f".format(20f / delay)
                    } else {
                        20
                    }
                    amountDisplay.setArgument(0, FormatWrapper.styled(obj, ChatFormatting.YELLOW))
                    spawnRandomAnvil()
                }
            } else {
                if (time % INCREASE_INTERVAL == 0) anvilAmount++

                if (prevAmount != anvilAmount) {
                    prevAmount = anvilAmount
                    amountDisplay.setArgument(0, FormatWrapper.styled(anvilAmount * 20, ChatFormatting.YELLOW))
                }

                repeat(minOf(anvilAmount, 256)) { spawnRandomAnvil() }
            }
        }
    }

    private fun spawnRandomAnvil() {
        if (winManager.isGameOver) return

        val pos = gameHandle.participants.getRandomParticipant(random)
            .map { player ->
                if (random.nextFloat() < DIRECT_ANVIL_CHANCE) {
                    setup.getRandomPositionAt(player.blockX, player.blockZ)
                } else {
                    setup.getRandomPosition(player.blockX, player.blockZ, SPREAD_RADIUS.toDouble())
                }
            }
            .orElseGet { setup.getRandomPosition() }

        val randomDirection = directions[random.nextInt(directions.size)]
        val state = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, randomDirection)

        val anvil = FallingBlockEntity(EntityType.FALLING_BLOCK, world)
        anvil.setPosRaw(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        anvil.isSilent = true
        anvil.time = 1
        anvil.setHurtsEntities(2.0f, 40)
        FallingBlockAccess.setDropItem(anvil, false)
        FallingBlockAccess.setDestroyedOnLanding(anvil, true)
        FallingBlockAccess.setBlockState(anvil, state)

        world.addFreshEntity(anvil)
    }

    private fun repelPlayer(player: ServerPlayer, to: Position) {
        val area = playArea ?: return
        if (!gameHandle.participants.isParticipating(player)) return

        val boundingBox: AABB = player.boundingBox
        if (area.contains(boundingBox.contract(1e-9, 0.0, 1e-9))) return

        val vec = Vec3(center.x() - to.x(), 0.5, center.z() - to.z())
        VelocityModifier.setVelocity(player, vec.normalize().scale(0.5))
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.5f, 2f)
    }
}

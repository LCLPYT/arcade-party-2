package work.lclpnet.ap2.game.bow_spleef

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import org.json.JSONArray
import work.lclpnet.ap2.api.stats.CommonStats.BlocksBroken
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.core.hook.EntitySpawnCallback
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.playSound
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.bow_spleef.item.*
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.whenBelowCriticalHeight
import work.lclpnet.ap2.impl.game.item.SpecialItems
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.FallKillTracker
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler
import work.lclpnet.ap2.impl.util.handler.VisualCooldown
import work.lclpnet.combatctl.impl.CombatStyles
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.level.BlockBreakParticleCallback
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

private val WORLD_BORDER_DELAY = Ticks.seconds(70).toLong()
private val WORLD_BORDER_TIME = Ticks.seconds(20).toLong()
private val DOUBLE_JUMP_COOLDOWN_TICKS = Ticks.seconds(2)

fun interface Impact {
    fun onImpact(projectile: Projectile, pos: BlockPos)
}

class BowSpleefInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : EliminationGameInstance(gameHandle, level, map) {

    private val stats = useFFAStats(winManager, listOf(
        TimeSurvived, Kills, BlocksBroken, DistanceMoved,
    ))
    private val killTracker = FallKillTracker(gameHandle.participants)
    private val doubleJumpHandler: DoubleJumpHandler
    private val heavyWeightItem = HeavyWeightItem()
    private val tripleJumpItem = TripleJumpItem()
    private lateinit var specialItems: SpecialItems

    init {
        val cooldown = VisualCooldown(gameHandle.rootScheduler)

        doubleJumpHandler = DoubleJumpHandler { player ->
            !cooldown.isOnCooldown(player) && !heavyWeightItem.isHeavyWeighted(player)
        }

        heavyWeightItem.doubleJumpHandler = doubleJumpHandler

        doubleJumpHandler.onDoubleJump().then { player ->
            if (specialItems.hasSpecialItem(player, tripleJumpItem) && tripleJumpItem.handleExtraJump(player, specialItems)) return@then

            doubleJumpHandler.disable(player)
            cooldown.setCooldown(player, DOUBLE_JUMP_COOLDOWN_TICKS)
        }

        cooldown.setOnCooldownOver { player ->
            if (heavyWeightItem.isHeavyWeighted(player)) return@setOnCooldownOver
            doubleJumpHandler.enable(player)
        }

        gameHandle.playerUtil.setDefaultCombatStyle(
            CombatStyles.CLASSIC.andThen({ it.isFishingRodPull = true }, {})
        )
    }

    override fun prepare() {
        useSmoothDeath()
        useNoHealing()
        useRemainingPlayersDisplay()

        trackSurvivalTime(stats)
        trackDistanceMoved(stats)

        val hooks = gameHandle.hooks

        BlockBreakParticleCallback.HOOK.registerWith(hooks) { _, _, _ -> true }

        val impactHook = HookFactory.createArrayBacked(Impact::class.java) { callbacks ->
            Impact { projectile, pos ->
                for (callback in callbacks) {
                    callback.onImpact(projectile, pos)
                }
            }
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, hit ->
            if (projectile is Arrow) {
                impactHook.invoker().onImpact(projectile, hit.blockPos)
            }
        }

        ProjectileHitEntityCallback.HOOK.registerWith(hooks) { projectile, hit ->
            if (projectile is Arrow) {
                impactHook.invoker().onImpact(projectile, hit.entity.blockPosition().below())
            }
        }

        EntitySpawnCallback.HOOK.registerWith(hooks) { entity, _ ->
            entity is Chicken
        }

        whenBelowCriticalHeight { player ->
            player.hurtServer(level, player.damageSources().fellOutOfWorld(), player.health)
        }

        specialItems = SpecialItems.create(
            gameHandle,
            map,
            level,
            Random(),
            commons().debugController()
        ) { config -> config.apply {
            register(TripleShotItem(), 0.55f)
            register(BurstShotItem(), 0.4f)
            register(ExplodeAmmoItem(impactHook), 0.3f)
            register(heavyWeightItem, 0.3f)
            register(FishingRodItem(), 0.1f)
            register(SwitcherItem(), 0.3f)
            register(LevitationItem(), 0.2f)
            register(LightWeightItem(), 0.25f)
            register(tripleJumpItem, 0.15f)
            register(CreeperExplosionItem(), 0.15f)
        }}

        specialItems.setup()
        specialItems.syncWithWorldBorder()

        // register this callback after special item setup, to execute it last (updates spawn pos mesh)
        impactHook.register { projectile, pos ->
            val broken = removeBlocks(pos, level)

            val shooter = projectile.owner as? ServerPlayer
            if (broken > 0 && shooter != null && gameHandle.participants.isParticipating(shooter)) {
                killTracker.onAreaBroken(BlockBox.ofRadius(pos, 1), shooter)
                stats.increment(shooter, BlocksBroken, broken)
            }

            projectile.discard()
        }
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                damageSource.isOf(DamageTypes.OUTSIDE_BORDER) ||
                (damageSource.isOf(DamageTypes.THROWN) && damageSource.directEntity is FishingHook)
            }

            ProtectionTypes.EXPLOSION.allow(config)
        }

        val hooks = gameHandle.hooks

        doubleJumpHandler.init(hooks)
        doubleJumpHandler.enable(gameHandle.participants)

        killTracker.init(gameHandle.scheduler)

        giveBowsToPlayers()

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, Ticks.seconds(5).toLong())
            .then(this::removeBlocksUnder)

        specialItems.spawnPeriodically()
    }

    private fun giveBowsToPlayers() {
        val infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, level.registryAccess())

        val bowName = translate("bow")
            .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) }

        for (player in gameHandle.participants) {
            val stack = unbreakable(ItemStack(Items.BOW))

            stack.set(DataComponents.CUSTOM_NAME, bowName.translateFor(player))
            stack.enchant(infinity, 1)
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)

            player.inventory.setItem(4, stack)
            player.inventory.setItem(9, ItemStack(Items.ARROW))

            PlayerInventoryAccess.setSelectedSlot(player, 4)
        }
    }

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        super.onDeath(player, attacker)

        val killerId = killTracker.getKiller(player)

        if (killerId != null) {
            val killer = gameHandle.server.playerList.getPlayer(killerId)

            if (killer != null && killer != player) {
                gainKill(killer, stats)
                player.setLastHurtByPlayer(killer, 100)
            }
        }

        killTracker.forget(player)
    }

    private fun removeBlocks(pos: BlockPos, world: ServerLevel): Int {
        val x = pos.x
        val y = pos.y
        val z = pos.z

        val air = Blocks.AIR.defaultBlockState()
        var broken = 0

        for (p in BlockPos.betweenClosed(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)) {
            if (world.getBlockState(p).isAir) continue

            world.setBlockAndUpdate(p, air)
            broken++
        }

        val cx = x + 0.5
        val cz = z + 0.5

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y.toDouble(), cz, 60, 1.0, 0.6, 1.0, 0.01)
        world.sendParticles(ParticleTypes.FLAME, cx, y.toDouble(), cz, 30, 1.0, 0.6, 1.0, 0.04)
        world.playSound(null, x.toDouble(), y.toDouble(), z.toDouble(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.AMBIENT, 0.12f, 0f)

        specialItems.positions().update()

        return broken
    }

    private fun removeBlocksUnder() {
        val world = level

        val spawnJson: JSONArray = requireNotNull(map.getProperty("spawn")) { "Spawn not configured" }
        val spawn = MapUtil.readBlockPos(spawnJson)

        val x = spawn.x
        val y = spawn.y
        val z = spawn.z

        val air = Blocks.AIR.defaultBlockState()

        playSound(SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.8f, 1f)

        for (pos in BlockPos.betweenClosed(x - 3, y - 30, z - 3, x + 3, y + 10, z + 3)) {
            world.setBlockAndUpdate(pos, air)
        }
    }
}
